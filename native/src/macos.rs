//! macOS dependent logic

use std::io;
use std::path::PathBuf;

// Warning: Untested

pub const PLATFORM_JAVA_EXECUTABLE_NAME: &str = "java";
pub const INSTALLER_JRE_HELP_URL: &str = "https://quiltmc.org"; // TODO: Fill in URL

pub(crate) fn get_jre_locations() -> io::Result<Vec<PathBuf>> {
	// macOS will store all the jvms inside of `/Library/Java/JavaVirtualMachines/`
	let jvms = PathBuf::from("/Library/Java/JavaVirtualMachines/");
	let child_dirs = jvms.read_dir()?;
	let mut jvms = Vec::new();

	for dir in child_dirs {
		if let Ok(dir) = dir {
			let executable = dir.path().join("bin").join(PLATFORM_JAVA_EXECUTABLE_NAME);

			if executable.exists() {
				jvms.push(executable);
			}
		}
	}

	// TODO: Does the launcher bundle it's own jvms or store those somewhere?

	Ok(jvms)
}
