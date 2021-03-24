//! Windows dependent logic

use std::ffi::OsString;
use std::io;
use std::path::PathBuf;
use winreg::RegKey;

pub const INSTALLER_JRE_HELP_URL: &'static str = "https://quiltmc.org"; // TODO: Fill in URL

/// Get the installation directory of the vanilla launcher.
/// Mojang conveniently adds a registry entry for us to find the install location.
fn launcher_install_dir() -> io::Result<PathBuf> {
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
	let installer_dir = launcher_install_dir()?;

	// FIXME: When Mojang upgrades the JRE they bundle on windows, search for a suitable JRE in the
	//  proper folder.
	Ok(vec![
		installer_dir.join("runtime/jre-legacy/windows-x64/jre-legacy/bin/javaw.exe"),
		installer_dir.join("runtime/jre-legacy/windows-x86/jre-legacy/bin/javaw.exe"),
		installer_dir.join("runtime/jre-x64/bin/javaw.exe"),
		installer_dir.join("runtime/jre-x86/bin/javaw.exe"),
	])
}
