//! This module exists to provide the required platform specific methods for type checking when not
//! developing on Windows or macOS.
//!
//! This module being used to compile will obviously just fail as you can see below.

compile_error!("The native launch only supports Windows and macOS!");

pub const PLATFORM_JAVA_EXECUTABLE_NAME: &str = "java";
pub const INSTALLER_JRE_HELP_URL: &str = "DUMMY_STUB";

use std::io;
use std::path::PathBuf;

pub(crate) fn get_jre_locations() -> io::Result<Vec<PathBuf>> {
	unreachable!()
}
