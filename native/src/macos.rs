//! macOS dependent logic

use std::io;
use std::io::{Error, ErrorKind};
use std::path::PathBuf;
use dirs::home_dir;

// Warning: Untested

pub const PLATFORM_JAVA_EXECUTABLE_NAME: &str = "java";
pub const INSTALLER_JRE_HELP_URL: &str = "https://quiltmc.org"; // TODO: Fill in URL

pub(crate) fn get_host_jre_locations() -> io::Result<Vec<PathBuf>> {
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

fn launcher_install_dir() -> io::Result<PathBuf> {
	println!("Attempting to find Minecraft folder in Application Support");

	let home = home_dir();

	if let Some(home) = home {
		let extended: PathBuf = ["Library", "Application Support", "minecraft"].iter().collect();

		let path = home.join(extended);

		if path.exists() {
			Ok(path)
		} else {
			Err(Error::new(ErrorKind::Other, "Can't find Minecraft location! That is VERY BAD! How did I do that?"))
		}
	} else {
		Err(Error::new(ErrorKind::Other, "Can't find Minecraft location! This must be running without a home dir!"))
	}
}

pub(crate) fn get_jre_locations() -> io::Result<Vec<PathBuf>> {
	let paths = vec![
		//oh lord I hope these are correct
		"runtime/jre-x64/jre.bundle/Contents/Home/bin/java",
		"runtime/jre-legacy/jre.bundle/Contents/Home/bin/java",
		"runtime/java-runtime-alpha/macos/java-runtime-alpha/jre.bundle/Contents/Home/bin/java",
		"runtime/java-runtime-beta/macos/java-runtime-beta/jre.bundle/Contents/Home/bin/java",
	];

	let mut candidates = Vec::new();

	let installer_dir = launcher_install_dir();

	if let Ok(installer_dir) = installer_dir {
		for x in &paths {
			candidates.push(installer_dir.join(x));
		}
	}

	let host_jvms = get_host_jre_locations();

	if let Ok(host_jvms) = host_jvms {
		for host_jvm in host_jvms {
			candidates.push(host_jvm);
		}
	}

	Ok(candidates)
}
