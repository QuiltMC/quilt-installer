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

package org.quiltmc.installer.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.lib.gson.JsonReader;
import org.quiltmc.lib.gson.JsonWriter;

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

		try (JsonReader reader = new JsonReader(new InputStreamReader(Files.newInputStream(launcherProfilesPath)))) {
			launcherProfiles = read(reader);
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
		try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(launcherProfilesPath))) {
			writer.setIndent("  "); // Prettify it
			write(writer, launcherProfiles);
		}
	}

	private static Object read(JsonReader reader) throws IOException {
		switch (reader.peek()) {
		case BEGIN_ARRAY:
			List<Object> list = new ArrayList<>();

			reader.beginArray();

			while (reader.hasNext()) {
				list.add(read(reader));
			}

			reader.endArray();

			return list;
		case BEGIN_OBJECT:
			Map<String, Object> object = new LinkedHashMap<>();

			reader.beginObject();

			while (reader.hasNext()) {
				String key = reader.nextName();
				object.put(key, read(reader));
			}

			reader.endObject();

			return object;
		case STRING:
			return reader.nextString();
		case NUMBER:
			return reader.nextDouble();
		case BOOLEAN:
			return reader.nextBoolean();
		case NULL:
			return null;
		// Unused, probably a sign of malformed json
		case NAME:
		case END_DOCUMENT:
		case END_ARRAY:
		case END_OBJECT:
		default:
			throw new IllegalStateException();
		}
	}

	private static void write(JsonWriter writer, @Nullable Object input) throws IOException {
		// Object
		if (input instanceof Map) {
			writer.beginObject();

			for (Map.Entry<?, ?> entry : ((Map<?, ?>) input).entrySet()) {
				writer.name(entry.getKey().toString());
				write(writer, entry.getValue());
			}

			writer.endObject();
		// Array
		} else if (input instanceof List) {
			writer.beginArray();

			for (Object element : ((List<?>) input)) {
				write(writer, element);
			}

			writer.endArray();
		} else if (input instanceof Number) {
			writer.value((Number) input);
		} else if (input instanceof String) {
			writer.value((String) input);
		} else if (input instanceof Boolean) {
			writer.value((boolean) input);
		} else if (input == null) {
			writer.nullValue();
		} else {
			throw new IllegalArgumentException(String.format("Don't know how to convert %s to json", input));
		}
	}

	private static String createProfileIcon() {
		// TODO decide on logo lmao and create the file
		try (InputStream stream = LauncherProfiles.class.getClassLoader().getResourceAsStream("logo.png")) {
			if (stream != null) {
				byte[] ret = new byte[4096];
				int offset = 0;
				int len;

				while ((len = stream.read(ret, offset, ret.length - offset)) != -1) {
					offset += len;
					if (offset == ret.length) ret = Arrays.copyOf(ret, ret.length * 2);
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
