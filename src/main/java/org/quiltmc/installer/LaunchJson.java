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

import org.quiltmc.json5.JsonReader;
import org.quiltmc.json5.JsonWriter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class LaunchJson {
	// TODO: Switch to quilt
	public static final String LOADER_ARTIFACT_NAME = "quilt-loader";

	public static CompletableFuture<String> get(String gameVersion, String loaderVersion, String endpoint) {
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
			// TODO: HACK HACK HACK: inject intermediary instead of hashed
		}).thenApplyAsync(raw -> {
			Map<String, Object> map;
			try {
				//noinspection unchecked
				map = (Map<String, Object>) Gsons.read(JsonReader.json(raw));
			} catch (IOException e) {
				throw new UncheckedIOException(e); // Handled via .exceptionally(...)
			}
			@SuppressWarnings("unchecked") List<Map<String, String>> libraries = (List<Map<String, String>>) map.get("libraries");
			for (Map<String, String> library : libraries) {
				if (library.get("name").startsWith("org.quiltmc:hashed")) {
					library.replace("name", library.get("name").replace("org.quiltmc:hashed", "net.fabricmc:intermediary"));
					library.replace("url", "https://maven.fabricmc.net/");
				}
			}
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
