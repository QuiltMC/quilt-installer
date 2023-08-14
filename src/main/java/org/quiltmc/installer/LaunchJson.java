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

import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.JsonWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

public final class LaunchJson {
	// TODO: Switch to quilt
	public static final String LOADER_ARTIFACT_NAME = "quilt-loader";

	public static CompletableFuture<String> get(String gameVersion, String loaderVersion, String endpoint, boolean beaconOptOut) {
		String rawUrl = QuiltMeta.DEFAULT_META_URL + String.format(endpoint, gameVersion, loaderVersion);

		return CompletableFuture.supplyAsync(() -> {
			try {
				URL url = new URL(rawUrl);
				URLConnection connection = Connections.openConnection(url);

				InputStreamReader stream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);

				try (BufferedReader reader = new BufferedReader(stream)) {
					StringBuilder builder = new StringBuilder();
					String line;

					while ((line = reader.readLine()) != null) {
						builder.append(line);
						builder.append('\n');
					}

					return builder.toString();
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e); // Handled via .exceptionally(...)
			}
		}).thenApplyAsync(raw -> {
			Map<String, Object> map;
			try {
				//noinspection unchecked
				map = (Map<String, Object>) Gsons.read(JsonReader.json(raw));
			} catch (IOException e) {
				throw new UncheckedIOException(e); // Handled via .exceptionally(...)
			}

			if (beaconOptOut) {
				@SuppressWarnings("unchecked")
				Map<String, List<Object>> arguments = (Map<String,List<Object>>)map.get("arguments");
				arguments
						.computeIfAbsent("jvm", (key) -> new ArrayList<>())
						.add("-Dloader.disable_beacon=true");
			}

			// TODO: HACK HACK HACK: inject intermediary instead of hashed
			Set<String> libraryNames = new HashSet<>();
			List<Map<String, String>> newLibraries = new ArrayList<>();
			@SuppressWarnings("unchecked") List<Map<String, String>> libraries = (List<Map<String, String>>) map.get("libraries");
			for (Map<String, String> library : libraries) {
				if (library.get("name").startsWith("org.quiltmc:hashed")) {
					library.replace("name", library.get("name").replace("org.quiltmc:hashed", "net.fabricmc:intermediary"));
					library.replace("url", "https://maven.fabricmc.net/");
				}
				if (!libraryNames.contains(library.get("name"))) {
        				libraryNames.add(library.get("name"));
        				newLibraries.add(library);
    				}
			}
			map.put("libraries", newLibraries);
			StringWriter writer = new StringWriter();
			try {
				Gsons.write(JsonWriter.json(writer), map);
			} catch (IOException e) {
				throw new UncheckedIOException(e); // Handled via .exceptionally(...)
			}
			return writer.toString();
		});
	}

	private LaunchJson() {
	}
}
