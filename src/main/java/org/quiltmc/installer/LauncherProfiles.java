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

import com.google.gson.JsonObject;
import org.quiltmc.installer.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

public final class LauncherProfiles {
	private static final DateFormat ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
	private static final String LOADER_NAME = "Quilt";

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

		JsonObject launcherProfiles;

		try (var reader = Files.newBufferedReader(launcherProfilesPath)) {
			launcherProfiles = Optional.ofNullable(Util.GSON.fromJson(reader, JsonObject.class)).orElseThrow(() -> new IllegalArgumentException("launcher_profiles.json must have a root object!"));
		}

		JsonObject profiles = launcherProfiles.getAsJsonObject("profiles");

		String newProfileName = LOADER_NAME + " " + gameVersion;

		// Modify the profile
		if (profiles.has(newProfileName)) {
			JsonObject profile = profiles.getAsJsonObject(newProfileName);

			profile.addProperty("lastVersionId", name);
		} else if (profiles.has("quilt-loader-" + gameVersion)) { // old style
			JsonObject profile = profiles.getAsJsonObject("quilt-loader-" + gameVersion);

			profile.addProperty("lastVersionId", name);
			profile.addProperty("name", newProfileName); // update name too
		} else {
			// Create a new profile
			JsonObject profile = new JsonObject();

			var now = new Date();

			profile.addProperty("name", newProfileName);
			profile.addProperty("type", "custom");
			profile.addProperty("created", ISO_8601.format(now));
			profile.addProperty("lastUsed", ISO_8601.format(now));
			profile.addProperty("icon", createProfileIcon());
			profile.addProperty("lastVersionId", name);

			profiles.add(newProfileName, profile);
		}

		// Write out the new profiles
		try (var writer = Files.newBufferedWriter(launcherProfilesPath)) {
			Util.GSON.toJson(launcherProfiles, writer);
		}
	}

	private static String createProfileIcon() {
		try (var stream = LauncherProfiles.class.getClassLoader().getResourceAsStream("icon.png")) {
			if (stream != null) {
				return "data:image/png;base64," + Base64.getEncoder().encodeToString(stream.readAllBytes());
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
