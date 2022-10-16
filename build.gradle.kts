import java.net.URI

plugins {
	java
	`java-library`
	`maven-publish`
	// application

	id("net.kyori.blossom") version "1.3.1"
	id("com.diffplug.spotless") version "5.8.2"
	id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "org.quiltmc"
val env = System.getenv()
version = if (env["SNAPSHOTS_URL"] != null) {
	"0-SNAPSHOT"
} else {
	"0.4.4"
}
base.archivesBaseName = project.name

repositories {
	mavenCentral()

	maven("https://maven.quiltmc.org/repository/release/") {
		name = "QuiltMC Releases"
	}
}

dependencies {
	implementation("org.quiltmc:quilt-json5:${project.quilt_json5")
	compileOnly("org.jetbrains:annotations:${project.annotations}")
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
	if (JavaVersion.current().isJava9Compatible) {
		options.release.set(8)
	} else {
		java.sourceCompatibility = JavaVersion.VERSION_1_8
		java.targetCompatibility = JavaVersion.VERSION_1_8
	}
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
}

tasks.shadowJar {
	relocate("org.quiltmc.json5", "org.quiltmc.installer.lib.json5")
	minimize()

	// Compiler does not know which set method we are targeting with null value
	val classifier: String? = null;
	archiveClassifier.set(classifier)
}

tasks.build {
	dependsOn(tasks.shadowJar)
}

val copyForNative = tasks.register<Copy>("copyForNative") {
	dependsOn(tasks.jar)
	from(tasks.jar)
	into(file("build"))

	rename {
		return@rename if (it.contains("quilt-installer")) {
			"native-quilt-installer.jar"
		} else {
			it
		}
	}
}


publishing {
	publications {
		if (env["TARGET"] == null) {
			create<MavenPublication>("mavenJava") {
				from(components["java"])
			}
		} else {
			// TODO: When we build macOS make this work
			val architecture = env["TARGET"]

			create<MavenPublication>("mavenNatives") {
				groupId = "org.quiltmc.quilt-installer-native-bootstrap"
				artifactId = "windows-$architecture"

				artifact {
					file("$projectDir/native/target/$architecture-pc-windows-msvc/release/quilt-installer.exe")
				}
			}
		}
	}

	repositories {
		if (env["MAVEN_URL"] != null) {
			repositories.maven {
				url = URI(env["MAVEN_URL"]!!)

				credentials {
					username = env["MAVEN_USERNAME"]
					password = env["MAVEN_PASSWORD"]
				}
			}
		} else if (env["SNAPSHOTS_URL"] != null) {
			repositories.maven {
				url = URI(env["SNAPSHOTS_URL"]!!)

				credentials {
					username = env["SNAPSHOTS_USERNAME"]
					password = env["SNAPSHOTS_PASSWORD"]
				}
			}
		}
	}
}
