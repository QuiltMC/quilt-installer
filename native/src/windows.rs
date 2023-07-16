//! Windows dependent logic
// Some code in this file referenced from fabric-installer-native-bootstrap
use std::ffi::OsString;
use std::io;
use std::io::Error;
use std::path::PathBuf;
use winreg::RegKey;

pub const PLATFORM_JAVA_EXECUTABLE_NAME: &str = "javaw";
pub const INSTALLER_JRE_HELP_URL: &str = "https://quiltmc.org"; // TODO: Fill in URL
pub const UWP_PATH: &str = "Packages/Microsoft.4297127D64EC6_8wekyb3d8bbwe/LocalCache/Local/";

fn get_uwp_installer() -> io::Result<PathBuf> {
	println!("Attempting to find a Java version from the UWP Minecraft installer");
	let mut path = std::env::var_os("LOCALAPPDATA");
	if path.is_none() {
		return Err(io::Error::from(io::ErrorKind::Unsupported))
	}
	let mut path = PathBuf::from(path.unwrap());
	path.push(UWP_PATH);

	return if path.exists() {
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
pub(crate) fn get_jre_locations() -> io::Result<Vec<PathBuf>> {
	let paths = vec![
		"runtime/java-runtime-gamma/windows-x64/java-runtime-gamma/bin/javaw.exe",
		"runtime/java-runtime-beta/windows-x64/java-runtime-beta/bin/javaw.exe",

		// x86 versions
		"runtime/java-runtime-gamma/windows-x86/java-runtime-gamma/bin/javaw.exe",
		"runtime/java-runtime-beta/windows-x86/java-runtime-beta/bin/javaw.exe", // used by at least 1.18.2



		// All signs point to a versioning scheme based on Greek letters. Let's future-proof it against the next predicted one
		"runtime/java-runtime-delta/windows-x64/javaw.exe",
		"runtime/java-runtime-delta/windows-x86/javaw.exe"
	];

	let mut candidates = Vec::new();

	let uwp_dir = get_uwp_installer();

	if let Ok(uwp_dir) = uwp_dir {
		for x in &paths {
			candidates.push(uwp_dir.join(x));
		}
	}

	let installer_dir = launcher_install_dir();

	if let Ok(installer_dir) = installer_dir {
		for x in &paths {
			candidates.push(installer_dir.join(x));
		}
	}

	Ok(candidates)
}
