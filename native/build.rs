extern crate embed_resource;

fn main() {
	// Include our program resources for visual styling and DPI awareness on windows
	if cfg!(windows) {
		embed_resource::compile("resources/windows/program.rc");

		winres::WindowsResource::new()
			.set_icon("resources/windows/icon.ico")
			.set("ProductName", "Quilt Installer")
			.set("CompanyName", "The Quilt Project")
			.set("LegalCopyright", "Apache License Version 2.0")
			.compile()
			.expect("Failed to set windows resources");
	}
}
