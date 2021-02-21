plugins {
	java
	`java-library`
	// application

	id("net.kyori.blossom") version "1.1.0"
	id("com.diffplug.spotless") version "5.8.2"
	id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()

	maven("https://maven.quiltmc.org/repository/release/") {
		name = "QuiltMC Releases"
	}
}

dependencies {
	implementation("org.quiltmc:gson-stream:1.0")
	compileOnly("org.jetbrains:annotations:20.1.0")
}

spotless {
	java {
		// Use comma separator for openjdk like license headers
		licenseHeaderFile(project.file("codeformat/HEADER")).yearSeparator(", ")
	}
}

// Apply constant string constant replacements for the project version in CLI class
blossom {
	replaceToken("__INSTALLER_VERSION", project.version)
}

tasks.compileJava {
	options.release.set(8)
}

// Cannot use application for the time being because shadow does not like mainClass being set for some reason.
// There is a PR which has fixed this, so update shadow probably when 6.10.1 or 6.11 is out
//application {
//	mainClass.set("org.quiltmc.installer.Main")
//}

tasks.jar {
	manifest {
		attributes["Implementation-Title"] = "Quilt-Installer"
		attributes["Implementation-Version"] = project.version
		attributes["Main-Class"] = "org.quiltmc.installer.Main"
	}

	enabled = false
}

tasks.shadowJar {
	relocate("org.quiltmc.lib.gson", "org.quiltmc.installer.lib.gson")
	minimize()

	// Compiler does not know which set method we are targetting with null value
	val classifier: String? = null;
	archiveClassifier.set(classifier)
}

tasks.build {
	dependsOn(tasks.shadowJar)
}
