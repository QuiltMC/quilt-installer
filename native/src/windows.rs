//! Windows dependent logic
// Some code in this file referenced from fabric-installer-native-bootstrap
use std::ffi::OsString;
use std::path::{Path, PathBuf};
use std::{env, io};
use winreg::RegKey;

const UWP_PATH: &str = "Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local";

#[cfg(target_pointer_width = "32")]
const PROGRAM_FILES_VAR_NAME: &str = "ProgramFiles";
#[cfg(target_pointer_width = "64")]
const PROGRAM_FILES_VAR_NAME: &str = "ProgramFiles(x86)";

fn get_uwp_installer() -> io::Result<PathBuf> {
	println!("Attempting to find a Java version from the UWP Minecraft installer");

	let Some(mut path) = env::var_os("LOCALAPPDATA").map(PathBuf::from) else {
		return Err(io::Error::from(io::ErrorKind::Unsupported));
	};
	path.push(UWP_PATH);

	if path.exists() {
		Ok(path)
	} else {
		Err(io::Error::from(io::ErrorKind::NotFound))
	}
}

/// Get the installation directory of the vanilla launcher.
/// Mojang conveniently adds a registry entry for us to find the install location.
fn launcher_install_dir() -> io::Result<PathBuf> {
	println!("Attempting to find Minecraft Launcher installation from registry");

	let launcher_install_location = RegKey::predef(winreg::enums::HKEY_CURRENT_USER)
		.open_subkey(r"SOFTWARE\Mojang\InstalledProducts\Minecraft Launcher")?
		.get_value::<OsString, &str>("InstallLocation")?;

	Ok(PathBuf::from(launcher_install_location))
}

/// Gets all possible installation locations of a the JRE
///
/// If the Minecraft Launcher is installed, then we may be able to use the JRE the launcher has downloaded
/// if the system's bundled JRE is not suitable.
pub(crate) fn get_jre_locations(has_args: bool) -> io::Result<Vec<PathBuf>> {
	let paths = vec![
		"runtime/java-runtime-gamma/windows-x64/java-runtime-gamma/bin",
		"runtime/java-runtime-beta/windows-x64/java-runtime-beta/bin",
		"runtime/java-runtime-delta/windows-x64/java-runtime-delta/bin",
		// x86 versions
		"runtime/java-runtime-gamma/windows-x86/java-runtime-gamma/bin",
		"runtime/java-runtime-beta/windows-x86/java-runtime-beta/bin", // Used 1.18.2 and above
		"runtime/java-runtime-delta/windows-x86/java-runtime-delta/bin", // Used by 1.20.5 and above
	];

	let mut candidates = Vec::new();

	if let Ok(uwp_dir) = get_uwp_installer() {
		candidates.extend(collect_paths(&paths, has_args, &uwp_dir));
	}

	if let Ok(install_dir) = launcher_install_dir() {
		candidates.extend(collect_paths(&paths, has_args, &install_dir));
	}

	if let Some(program_files_path) = env::var_os(PROGRAM_FILES_VAR_NAME).map(PathBuf::from) {
		let launcher_dir = program_files_path.join("Minecraft Launcher");
		if launcher_dir.try_exists().unwrap_or(false) {
			candidates.extend(collect_paths(&paths, has_args, &launcher_dir));
		}
	}

	Ok(candidates)
}

fn collect_paths(paths: &[&str], has_args: bool, location: &Path) -> Vec<PathBuf> {
	paths
		.iter()
		.map(|x| {
			let mut path = location.join(x);
			match has_args {
				true => path.push("java.exe"),
				false => path.push("javaw.exe"),
			}

			path
		})
		.collect()
}
