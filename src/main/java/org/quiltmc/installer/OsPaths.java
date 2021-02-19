/*
 * Copyright 2021 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.installer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public final class OsPaths {
	/**
	 * Platform specific environment variable key for the Windows APPDATA folder.
	 */
	private static final String WIN_APPDATA = "APPDATA";
	private static final String MAC_LIBRARY = "Library";
	private static final String MAC_APPLICATION_SUPPORT = "Application Support";
	private static final String DOT_MINECRAFT = ".minecraft";

	/**
	 * Get's the data directory that the Minecraft launcher is typically installed at.
	 * This may return a platform specific value.
	 *
	 * @return the path to the default launcher install directory
	 */
	public static Path getDefaultInstallationDir() {
		String userHome = System.getProperty("user.home", ".");
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

		Path homeDir = Paths.get(userHome);

		// Rely on appdata environment variable for the path
		if (os.contains("win") && System.getenv(WIN_APPDATA) != null) {
			return Paths.get(System.getenv(WIN_APPDATA), DOT_MINECRAFT);
		} else if (os.contains("mac")) {
			// MacOS is the odd one out here
			// On MacOS, Minecraft is at `~/Library/Application Support/minecraft`
			return homeDir.resolve(MAC_LIBRARY)
					.resolve(MAC_APPLICATION_SUPPORT)
					.resolve("minecraft");
		}

		// Assume Linux-like directory as a fallback
		return homeDir.resolve(DOT_MINECRAFT);
	}

	public static Path getUserDataDir() {
		String userHome = System.getProperty("user.home", ".");
		String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

		Path homeDir = Paths.get(userHome);

		// Rely on appdata environment variable for the path
		if (os.contains("win") && System.getenv(WIN_APPDATA) != null) {
			return Paths.get(System.getenv(WIN_APPDATA));
		} else if (os.contains("mac")) {
			// MacOS is the odd one out here at `~/Library/Application Support/`
			return homeDir.resolve(MAC_LIBRARY)
					.resolve(MAC_APPLICATION_SUPPORT);
		}

		// We are already here on a Linux-like OS
		return homeDir.resolve(DOT_MINECRAFT);
	}

	private OsPaths() {
	}
}
