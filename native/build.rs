extern crate embed_resource;

use std::{env, fs};
use std::path::Path;
use std::process::Stdio;
use execute::shell;

const INSTALLER_JAR_PATH: &str = "build/native-quilt-installer.jar";

fn main() {
	println!("cargo::rerun-if-changed=build.rs");

	let current_dir = Path::new(env!("CARGO_MANIFEST_DIR"));
	let parent_dir = current_dir.parent().expect("Failed to get parent directory");
	let installer_path = parent_dir.join(INSTALLER_JAR_PATH);

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
			panic!("Failed to build installer jar: Process finished with exit code {}", result.code().map(|x| x.to_string()).unwrap_or("INTERRUPTED BY SIGNAL".to_string()));
		}
	}

	println!("cargo::rustc-env=QUILT_INSTALLER_JAR_PATH={}", fs::canonicalize(installer_path).expect("Failed to get absolute path for installer jar").to_str().expect("Failed to convert path to string"));
	println!("cargo:rerun-if-changed={INSTALLER_JAR_PATH}");

	// Include our program resources for visual styling and DPI awareness on windows
	if cfg!(windows) {
		embed_resource::compile("resources/windows/program.rc", embed_resource::NONE).manifest_required().expect("Failed to compile windows resources");

		winres::WindowsResource::new()
			.set_icon("resources/windows/icon.ico")
			.set("ProductName", "Quilt Installer")
			.set("CompanyName", "The Quilt Project")
			.set("LegalCopyright", "Apache License Version 2.0")
			.compile()
			.expect("Failed to set windows resources");
	}
}
