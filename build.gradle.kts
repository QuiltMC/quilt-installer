import java.lang.UnsupportedOperationException
import java.net.URI

plugins {
	java
	`java-library`
	`maven-publish`
	id("com.diffplug.spotless") version "6.19.0"
//	id("com.github.johnrengelman.shadow") version "8.1.1"
	id("org.beryx.jlink") version "2.26.0"
}

group = "org.quiltmc"
val env = System.getenv()
// also set this in <X>
val baseVersion = "0.6.0"
version = if (env["SNAPSHOTS_URL"] != null) {
	"0-SNAPSHOT"
} else {
	baseVersion
}
base.archivesBaseName = project.name

repositories {
	mavenCentral()

	maven("https://maven.quiltmc.org/repository/release/") {
		name = "QuiltMC Releases"
	}
}

dependencies {
	implementation("org.quiltmc.parsers:json:0.2.1")
	compileOnly("org.jetbrains:annotations:20.1.0")
}

spotless {
	java {
		// Use comma separator for openjdk like license headers
		licenseHeaderFile(project.file("codeformat/HEADER")).yearSeparator(", ")
	}
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(17))
	}
}

tasks.compileJava {
	if (JavaVersion.current().isJava9Compatible) {
		options.release.set(17)
	} else {
		java.sourceCompatibility = JavaVersion.VERSION_17
		java.targetCompatibility = JavaVersion.VERSION_17
	}
}

// Cannot use application for the time being because shadow does not like mainClass being set for some reason.
// There is a PR which has fixed this, so update shadow probably when 6.10.1 or 6.11 is out
application {
	mainClass.set("org.quiltmc.installer.Main")
	mainModule.set("org.quiltmc.installer")
}

tasks.jar {
	manifest {
		attributes["Implementation-Title"] = "Quilt-Installer"
		attributes["Implementation-Version"] = project.version
		attributes["Main-Class"] = "org.quiltmc.installer.Main"
	}
}

val platform = env["PLATFORM"]
//val arch = env["ARCH"]
publishing {
	publications {
		if (platform == null) {
			create<MavenPublication>("mavenJava") {
				from(components["java"])
			}
		} else {
			// TODO: When we build macOS make this work
			create<MavenPublication>("mavenNatives") {
				groupId = "org.quiltmc.quilt-installer.native"
				artifactId = "$platform-x64"

				tasks.publish {
					dependsOn("jpackage")
				}
				artifact {
					val executableName = if (platform == "windows") {
						"quilt-installer.exe"
					} else if (platform == "macos") {
						"TODO" // todo
					}
					else {
						throw UnsupportedOperationException("Unknown platform")
					}
					file("$buildDir/jpackage/quilt-installer/$executableName")
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

jlink {
//	options.set(listOf("--strip-debug"))
	jpackage {
		skipInstaller = true
		if (platform == "windows") {
			imageOptions = listOf("--win-console")
		}
		appVersion = baseVersion
		// sorry everyone, but i use windows
		icon = if (platform == null || platform == "windows") {
			"icon.ico"
		} else {
			"src/main/resources/icon.png"
		}
	}
}
