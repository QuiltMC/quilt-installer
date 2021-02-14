plugins {
	java
	`java-library`
	application

	id("net.kyori.blossom") version "1.1.0"
	id("com.diffplug.spotless") version "5.8.2"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
	mavenCentral()

	maven("https://maven.quiltmc.org/repository/release/") {
		name = "QuiltMC"
	}
}

dependencies {
	val junitVersion = "5.3.1"

	testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

	implementation("org.quiltmc:gson-stream:1.0")

	compileOnly("org.jetbrains:annotations:20.1.0")
}

spotless {
	java {
		// Use comma separator for openjdk like license headers
		licenseHeaderFile(project.file("codeformat/HEADER")).yearSeparator(", ")

		// Special licensing cases - exclude from regular process
		targetExclude(
				"/src/main/java/org/quiltmc/installer/cli/InputParser.java",
				"/src/main/java/org/quiltmc/installer/cli/Node.java",
				"/src/main/java/org/quiltmc/installer/cli/UsageParser.java"
		)
	}

	format("java-parser-classes", com.diffplug.gradle.spotless.JavaExtension::class.java) {
		// Use comma separator for openjdk like license headers
		licenseHeaderFile(project.file("codeformat/HEADER-PARSER-CLASSES")).yearSeparator(", ")

		target(
				"/src/main/java/org/quiltmc/installer/cli/InputParser.java",
				"/src/main/java/org/quiltmc/installer/cli/Node.java",
				"/src/main/java/org/quiltmc/installer/cli/UsageParser.java"
		)
	}
}

// Apply constant string constant replacements for the project version in CLI class
blossom {
	replaceToken("__INSTALLER_VERSION", project.version)
}

tasks.compileJava {
	options.release.set(8)
}

tasks.test {
	useJUnitPlatform()
}
