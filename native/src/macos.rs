//! macOS dependent logic

use std::io;
use std::path::PathBuf;

// FIXME: Temporary until we figure out how to build macOS
compile_error!("macOS will be supported but is not implemented yet");

pub const INSTALLER_JRE_HELP_URL: &'static str = "https://quiltmc.org"; // TODO: Fill in URL

pub(crate) fn get_jre_locations() -> io::Result<Vec<PathBuf>> {
	// TODO: Are there any physical installation locations we should know of?
	Ok(vec![])
}
