extern crate embed_resource;

use execute::shell;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::{env, fs};

fn main() {
	println!("cargo:rerun-if-env-changed=QUILT_INSTALLER_JAR_PATH");
	let installer_path = match env::var_os("QUILT_INSTALLER_JAR_PATH") {
		Some(path) => PathBuf::from(path),
		None => {
			println!("QUILT_INSTALLER_JAR_PATH not set, building installer jar");

			let current_dir = Path::new(env!("CARGO_MANIFEST_DIR"));
			let parent_dir = current_dir
				.parent()
				.expect("Failed to get parent directory");
			let installer_path = parent_dir.join("build/native-quilt-installer.jar");

			if !fs::exists(&installer_path).expect("Failed to check if installer jar exists") {
				let mut cmd = "gradlew";
				if cfg!(windows) {
					cmd = "gradlew.bat";
				}

				let result = shell(cmd)
					.current_dir(parent_dir)
					.arg("copyForNative")
					.stdout(Stdio::inherit())
					.status()
					.expect("Failed to execute gradle build");

				if !result.success() {
					panic!(
						"Failed to build installer jar: Process finished with exit code {}",
						result
							.code()
							.map(|x| x.to_string())
							.unwrap_or("INTERRUPTED BY SIGNAL".to_string())
					);
				}
			}

			installer_path
		}
	};

	let full_jar_path = installer_path
		.canonicalize()
		.expect("Failed to get absolute path for installer jar");

	let full_jar_path_str = full_jar_path
		.to_str()
		.expect("Failed to convert path to string");

	println!("cargo::rerun-if-changed={full_jar_path_str}");
	println!("cargo::rustc-env=QUILT_INSTALLER_JAR_INCLUDE_PATH={full_jar_path_str}");

	// Include our program resources for visual styling and DPI awareness on windows
	if cfg!(windows) {
		embed_resource::compile("resources/windows/quilt-installer.exe.rc", embed_resource::NONE)
			.manifest_required()
			.expect("Failed to compile windows resources");

		winres::WindowsResource::new()
			.set_icon("resources/windows/icon.ico")
			.set("ProductName", "Quilt Installer")
			.set("CompanyName", "The Quilt Project")
			.set("LegalCopyright", "Apache License Version 2.0")
			.compile()
			.expect("Failed to set windows resources");
	}
}
