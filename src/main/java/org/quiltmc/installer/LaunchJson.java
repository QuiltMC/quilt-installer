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

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class LaunchJson {
	public static final String LOADER_ARTIFACT_NAME = "quilt-loader";

	public static CompletableFuture<String> get(String gameVersion, String loaderVersion, String endpoint) {
		var rawUrl = URI.create(QuiltMeta.DEFAULT_META_URL + String.format(endpoint, gameVersion, loaderVersion));

		return CompletableFuture.supplyAsync(() -> {
			try (var reader = new BufferedReader(new InputStreamReader(rawUrl.toURL().openStream(), StandardCharsets.UTF_8))) {
				//noinspection unchecked
				return (Map<String, Object>) Gsons.read(JsonReader.json(reader));
			} catch (IOException e) {
				throw new UncheckedIOException(e); // Handled via .exceptionally(...)
			}
		}).thenApplyAsync(map -> {
			// Prevents a log warning about being unable to reach the active user beacon on stable versions.
			switch (loaderVersion) {
				case "0.19.2", "0.19.3", "0.19.4" -> {
					@SuppressWarnings("unchecked")
					Map<String, List<Object>> arguments = (Map<String,List<Object>>)map.get("arguments");
					arguments
							.computeIfAbsent("jvm", (key) -> new ArrayList<>())
							.add("-Dloader.disable_beacon=true");
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
