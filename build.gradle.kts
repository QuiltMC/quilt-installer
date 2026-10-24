import java.net.URI

plugins {
	java
	`maven-publish`
	idea

	alias(libs.plugins.blossom)
	alias(libs.plugins.spotless)
	alias(libs.plugins.shadow)
}

val projectJavaVersion = 17

group = "org.quiltmc"

version = if (providers.environmentVariable("SNAPSHOTS_URL").isPresent)
	"0-SNAPSHOTS"
else
	providers.environmentVariable("VERSION").getOrElse("DEVELOPMENT")

base {
	archivesName.set(project.name)
}

repositories {
	mavenCentral()

	maven("https://maven.quiltmc.org/repository/release/") {
		name = "QuiltMC Releases"
	}
}

sourceSets {
	create("java8") {
		blossom {
			javaSources {
				property("minimum_java_version", projectJavaVersion.toString())
			}
		}
	}
	main {
		compileClasspath += sourceSets["java8"].output
	}
}

dependencies {
	compileOnly(libs.jetbrains.annotations)
	compileOnly(libs.jspecify)

	implementation(libs.gson)
	implementation(libs.bouncycastle)
}

spotless {
	java {
		target("src/*/java*/**/*.java*")
		// Use comma separator for openjdk like license headers
		licenseHeaderFile(project.file("codeformat/HEADER")).yearSeparator(", ")
	}
}

tasks.compileJava {
	options.release.set(projectJavaVersion)
}

tasks.getByName("compileJava8Java", JavaCompile::class) {
	options.release.set(8)
}
java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(projectJavaVersion))
	}
}

tasks.jar.get().dependsOn(tasks["compileJava8Java"])
tasks.jar {
	manifest {
		attributes["Implementation-Title"] = "Quilt-Installer"
		attributes["Implementation-Version"] = project.version
		attributes["Multi-Release"] = true
		attributes["Built-On-Java"] = "${System.getProperty("java.vm.version")} (${System.getProperty("java.vm.vendor")})"

		attributes["Main-Class"] = "org.quiltmc.installer.Main"
		attributes["Quilt-Installer-Required-Java-Version"] = projectJavaVersion;
	}
}

tasks.shadowJar {
//	minimize()

	enableAutoRelocation = true
	relocationPrefix = "org.quiltmc.installer._lib"

	dependencies {
		exclude(dependency("org.jetbrains:annotations:.*"))
		exclude(dependency("com.google.errorprone:.*"))
	}
	exclude("META-INF/**")
	excludes.remove(
		"module-info.class"
	)
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE

	// Compiler does not know which set method we are targeting with null value
	val classifier: String? = null;
	archiveClassifier.set(classifier)
	from(sourceSets["java8"].output)
}

tasks.assemble {
	dependsOn(tasks.shadowJar)
}

val copyForNative = tasks.register<Copy>("copyForNative") {
	dependsOn(tasks.shadowJar)
	dependsOn(tasks.jar)
	from(tasks.shadowJar)
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
		providers.environmentVariable("NATIVE_TARGET").orNull?.let { nativeTarget ->
			create<MavenPublication>("mavenNatives") {
				groupId = "org.quiltmc.quilt-installer-native-bootstrap"
				artifactId = providers.environmentVariable("NATIVE_TARGET_ARTIFACT_ID").get()

				artifact {
					file("$projectDir/native/target/${nativeTarget}/release/quilt-installer${providers.environmentVariable("NATIVE_TARGET_EXT").get()}")
				}
			}
		} ?: run {
			create<MavenPublication>("mavenJava") {
				from(components["java"])
			}
		}
	}

	repositories {
		listOf("MAVEN", "SNAPSHOTS").forEach { prefix ->
			providers.environmentVariable("${prefix}_URL").orNull?.let { mavenUrl ->
				maven(mavenUrl) {
					credentials {
						username = providers.environmentVariable("${prefix}_USERNAME").get()
						password = providers.environmentVariable("${prefix}_PASSWORD").get()
					}
				}
			}
		}
	}
}

// IDEA no longer automatically downloads sources/javadoc jars for dependencies, so we need to explicitly enable the behavior.
idea {
	module {
		isDownloadSources = true
	}
}
