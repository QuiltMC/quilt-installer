// Require use of WinMain entrypoint so cmd doesn't flash open
// See RFC 1665 for additional details
#![windows_subsystem = "windows"]

#[cfg(windows)]
#[path = "windows.rs"]
mod platform;

#[cfg(target_os = "macos")]
#[path = "macos.rs"]
mod platform;

#[cfg(not(any(windows, target_os = "macos")))]
#[path = "unsupported.rs"]
mod platform;

use crate::platform::{get_jre_locations, INSTALLER_JRE_HELP_URL};
use native_dialog::{MessageDialog, MessageType};
use rand::random;
use std::env::temp_dir;
use std::fs::File;
use std::io;
use std::io::{ErrorKind, Write};
use std::path::{Path, PathBuf};
use std::process::{exit, Command};

const INSTALLER_LAST_RESORT_URL: &str = "https://quiltmc.org/"; // TODO: Fill in URL
const OS_ISSUES_URL: &str = "https://quiltmc.org/"; // TODO: Fill in URL

/// The bundled installer jar, see main entrypoint for why we include the bytes of the installer jar.
const INSTALLER_JAR: &[u8] = include_bytes!("../../build/native-quilt-installer.jar");

// TODO: Some things to do in the future
//  Error dialog localization, possibly this for getting the OS's locale? https://github.com/i509VCB/os-locale
fn main() {
	// Let's begin
	//
	// So we need to setup the jar file for this native installer launcher to actually launch.
	// We could just append the jar file to the end of the executable, but that abuses windows
	// specific logic and macOS would need something different on macOS since applications are really
	// fancy directories.
	//
	// Even if we could fulfill the above, `std::env::args(_os)` is not safe since the executable name
	// at idx=0 could be set to arbitrary stuff at runtime.
	//
	// Exhibit A of above case: https://vulners.com/securityvulns/SECURITYVULNS:DOC:22183
	//
	// We will be prudent and bundle the bytes of the installer jar right into the binary and copy it to a temporary file at runtime.

	// Generate a randomish file name so we can write the bundled jar to it for launching.
	let mut installer_jar_name = random::<u32>().to_string();
	installer_jar_name.push_str("quilt-installer.jar");

	let installer_jar = temp_dir().join(installer_jar_name);

	// Scope here will drop the file writer
	{
		let mut installer_jar = match File::create(installer_jar.clone()) {
			Ok(f) => f,
			Err(e) => {
				eprintln!("Failed to extract installer");

				if MessageDialog::new()
					.set_type(MessageType::Error)
					.set_title("Failed to launch installer")
					.set_text(
						format!("Failed to extract installer.\nError: {:?}", e.kind()).as_str(),
					)
					.show_alert()
					.is_err()
				{
					last_resort();
				}

				exit(1);
			}
		};

		match installer_jar.write_all(INSTALLER_JAR) {
			Ok(_) => {}
			Err(e) => {
				// Well... this is a problem
				// Show the dialog and give up I guess
				eprintln!("Failed to extract installer");

				if MessageDialog::new()
					.set_type(MessageType::Error)
					.set_title("Failed to launch installer")
					.set_text(
						format!("Failed to extract installer.\nError: {:?}", e.kind()).as_str(),
					)
					.show_alert()
					.is_err()
				{
					last_resort();
				}

				exit(1);
			}
		}
	}

	if let Ok(possible_jres) = get_jre_locations() {
		// Let's try some of the JREs we got
		for jre in possible_jres {
			try_launch(&installer_jar, jre);
		}
	}

	// Well time for the last resort by testing the system's `javaw` executable.
	match try_launch(&installer_jar, "javaw") {
		JreLaunchError::Os(_) => {
			// Blame the OS
			if let Ok(result) = MessageDialog::new()
				.set_type(MessageType::Error)
				.set_title("Failed to launch installer")
				.set_text("The installer failed to launch due to issues with the current OS. Do you want to open a link for more information?")
				.show_confirm()
			{
				if result {
					let _ = open::that(OS_ISSUES_URL);
					exit(1); // We did not successfully start
				}
			} else {
				// Yikes the dialog did not open, last resort it is
				last_resort();
			}
		}
		JreLaunchError::Jre => {
			// Blame the lack of a JRE
			if let Ok(result) = MessageDialog::new()
				.set_type(MessageType::Error)
				.set_title("Failed to launch installer")
				.set_text("The installer failed to launch because it could not find a suitable Java runtime. Do you want to open a link for more information?")
				.show_confirm()
			{
				if result {
					let _ = open::that(INSTALLER_JRE_HELP_URL);
					exit(1); // We did not successfully start
				}
			} else {
				// Yikes the dialog did not open, last resort it is
				last_resort();
			}
		}
		JreLaunchError::NoPermission => {
			eprintln!("Did not have permission to launch java");

			if MessageDialog::new()
				.set_type(MessageType::Error)
				.set_title("Failed to launch installer")
				.set_text("Did not have permission to launch Java for installer.")
				.show_alert()
				.is_err()
			{
				last_resort();
			}
		}
	}
}

/// Try to launch the installer
///
/// This will terminate the process if the java installer was successfully launched.
fn try_launch<P: AsRef<Path>>(installer_jar: &PathBuf, jre_path: P) -> JreLaunchError {
	// Let's see if the jre is valid
	// -version will always return an exit code of 0 if successful.

	println!("Trying JVM located at: {:?}", jre_path.as_ref());

	match Command::new(jre_path.as_ref()).arg("-version").status() {
		Ok(status) => {
			if !status.success() {
				// TODO: Introspect into the status code
				//  None only occurs on unix-like OSes, so macOS could have a signal
				match status.code() {
					None => {
						eprintln!("JVM terminated via signal");
					}
					Some(code) => {
						eprintln!("JVM terminated with exit code {}", code);
					}
				}

				return JreLaunchError::Jre;
			}
		}
		Err(e) => {
			return match e.kind() {
				ErrorKind::NotFound => {
					eprintln!("Could not find JVM at location");
					JreLaunchError::Jre
				}
				ErrorKind::PermissionDenied => {
					eprintln!("Permission denied when trying to launch jvm");
					JreLaunchError::NoPermission
				}
				_ => {
					eprintln!("Failed to launch JVM with error {:?}", e);
					JreLaunchError::Os(e)
				}
			}
		}
	}

	println!("Running JVM located at {:?}", jre_path.as_ref());

	// We have successfully run -version, so now we launch the installer.
	let result = Command::new(jre_path.as_ref())
		.arg("-jar")
		.arg(installer_jar.as_os_str())
		.status();

	match result {
		Ok(status) => {
			if status.success() {
				println!("Success, terminating bootstrapper");
				// Our job here is done
				exit(0);
			}

			// What happened?
			//
			// If the Java Virtual Machine was not successfully created, the exit code is 1.
			// The installer having a non-zero return code is not a good sign though.
			match status.code() {
				None => {
					// TODO: Signal
					eprintln!("JVM killed via signal");
					JreLaunchError::Jre
				}
				Some(code) => {
					// JVM failed to start
					eprintln!("JVM terminated with exit code {}", code);
					JreLaunchError::Jre
				}
			}
		}
		Err(e) => match e.kind() {
			ErrorKind::NotFound => {
				eprintln!("Could not find JVM at location");
				JreLaunchError::Jre
			}
			ErrorKind::PermissionDenied => {
				eprintln!("Permission denied when trying to launch jvm");
				JreLaunchError::NoPermission
			}
			_ => {
				eprintln!("Failed to launch JVM with error {:?}", e);
				JreLaunchError::Os(e)
			}
		},
	}
}

/// Given everything else so far has failed, try opening a URL as the last resort.
fn last_resort() -> ! {
	let _ = open::that(INSTALLER_LAST_RESORT_URL);
	exit(1)
}

enum JreLaunchError {
	/// OS Error
	Os(io::Error),
	/// Issue when running the JRE, this includes failing to run with -version
	Jre,
	/// OS did not give the application permission to run javaw.
	NoPermission,
}
