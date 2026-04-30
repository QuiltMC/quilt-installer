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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.quiltmc.installer.util.Util;
import org.quiltmc.installer.util.meta.QuiltMeta;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public final class LaunchJson {
	public static final String LOADER_ARTIFACT_NAME = "quilt-loader";

	public static CompletableFuture<JsonObject> get(String gameVersion, String loaderVersion, String endpoint) {
		var rawUrl = URI.create(QuiltMeta.DEFAULT_META_URL + String.format(endpoint, gameVersion, loaderVersion));

		return CompletableFuture.supplyAsync(() -> {
			try (var reader = new BufferedReader(new InputStreamReader(rawUrl.toURL().openStream(), StandardCharsets.UTF_8))) {
				var json = Util.GSON.fromJson(reader, JsonObject.class);

				// Prevents a log warning about being unable to reach the active user beacon on stable versions.
				switch (loaderVersion) {
					case "0.19.2", "0.19.3", "0.19.4" -> {
						var arguments = json.getAsJsonObject("arguments");
						arguments.asMap()
								.computeIfAbsent("jvm", (key) -> new JsonArray())
								.getAsJsonArray()
								.add("-Dloader.disable_beacon=true");
					}
				}

				return json;
			} catch (IOException e) {
				throw new UncheckedIOException(e); // Handled via .exceptionally(...)
			}
		});
	}

	private LaunchJson() {}
}
