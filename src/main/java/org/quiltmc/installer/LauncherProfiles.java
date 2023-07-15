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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonWriter;

public final class LauncherProfiles {
	private static final DateFormat ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	private static final String LOADER_NAME = "quilt-loader";

	/**
	 * Reads the launcher_profiles, creates or modifies the existing launcher profile and then writes the new launcher profiles.
	 *
	 * @param gameDir the game directory
	 * @param name the name of the profile to create or rewrite
	 * @param gameVersion the game version
	 * @throws IOException if there were any issues reading or writing
	 */
	public static void updateProfiles(Path gameDir, String name, String gameVersion) throws IOException {
		final Path launcherProfilesPath = gameDir.resolve("launcher_profiles.json");

		if (Files.notExists(launcherProfilesPath)) {
			throw new IllegalStateException("No launcher_profiles.json to read from");
		}

		Object launcherProfiles;

		try (JsonReader reader = JsonReader.json(new InputStreamReader(Files.newInputStream(launcherProfilesPath)))) {
			launcherProfiles = Gsons.read(reader);
		}

		if (!(launcherProfiles instanceof Map)) {
			throw new IllegalArgumentException("launcher_profiles.json must have a root object!");
		}

		Object rawProfiles = ((Map<?, ?>) launcherProfiles).get("profiles");

		if (!(rawProfiles instanceof Map)) {
			throw new IllegalArgumentException("\"profiles\" field must be an object!");
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> profiles = (Map<String, Object>) rawProfiles;
		String newProfileName = LOADER_NAME + "-" + gameVersion;

		// Modify the profile
		if (profiles.containsKey(newProfileName)) {
			Object rawProfile = profiles.get(newProfileName);

			if (!(rawProfile instanceof Map)) {
				throw new IllegalStateException(String.format("Cannot update profile of name %s because it is not an object!", newProfileName));
			}

			@SuppressWarnings("unchecked")
			Map<String, Object> profile = (Map<String, Object>) rawProfile;

			profile.put("lastVersionId", name);
		} else {
			// Create a new profile
			Map<String, Object> profile = new LinkedHashMap<>();

			profile.put("name", newProfileName);
			profile.put("type", "custom");
			profile.put("created", ISO_8601.format(new Date()));
			profile.put("lastUsed", ISO_8601.format(new Date()));
			profile.put("icon", createProfileIcon());
			profile.put("lastVersionId", name);

			profiles.put(newProfileName, profile);
		}

		// Write out the new profiles
		try (JsonWriter writer = JsonWriter.json(Files.newBufferedWriter(launcherProfilesPath))) {
			writer.setIndent("  "); // Prettify it
			Gsons.write(writer, launcherProfiles);
		}
	}

	private static String createProfileIcon() {
		try (InputStream stream = LauncherProfiles.class.getClassLoader().getResourceAsStream("icon.png")) {
			if (stream != null) {
				byte[] ret = new byte[4096];
				int offset = 0;
				int len;

				while ((len = stream.read(ret, offset, ret.length - offset)) != -1) {
					offset += len;

					if (offset == ret.length) {
						ret = Arrays.copyOf(ret, ret.length * 2);
					}
				}

				return "data:image/png;base64," + Base64.getEncoder().encodeToString(Arrays.copyOf(ret, offset));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		// Failed, fallback to a non-vanilla icon
		return "TNT";
	}

	private LauncherProfiles() {
	}
}
