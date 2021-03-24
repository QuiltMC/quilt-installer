extern crate embed_resource;

fn main() {
	// Include our program resources for visual styling and DPI awareness on windows
	if cfg!(windows) {
		embed_resource::compile("resources/windows/program.rc");

		let result = winres::WindowsResource::new()
			.set_icon("resources/icon.ico")
			.set("ProductName", "Quilt Installer")
			.set("CompanyName", "The Quilt Project")
			.set("LegalCopyright", "Apache License Version 2.0")
			.compile();

		if let Err(_) = result {
			panic!("Failed to set windows resources");
		}
	}
}
