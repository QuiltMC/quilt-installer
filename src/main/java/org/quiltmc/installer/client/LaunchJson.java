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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public final class LaunchJson {
	// TODO: Switch theses
	public static final String MAVEN_LINK = "https://maven.fabricmc.net";
	public static final String ARTIFACT_GROUP = "net/fabricmc";
	public static final String LOADER_ARTIFACT_NAME = "fabric-loader";
	// TODO: Switch to quilt once we publish loader
	//public static final String MAVEN_LINK = "https://maven.quiltmc.org/repository/release";
	public static final String MAPPINGS_ARTIFACT_NAME = "intermediary";
//	public static final String LOADER_ARTIFACT_GROUP = "org/quiltmc";
//	public static final String LOADER_ARTIFACT_NAME = "quilt-loader";

	public static CompletableFuture<String> get(String gameVersion, String loaderVersion) {
		// TODO: Move to v3 when this endpoint is deployed
		// String rawUrl = QuiltMeta.DEFAULT_META_URL + String.format("/v3/versions/loader/%s/%s/profile/json", gameVersion, loaderVersion);
		String rawUrl = "https://meta.fabricmc.net" + String.format("/v2/versions/loader/%s/%s/profile/json", gameVersion, loaderVersion);

		return CompletableFuture.supplyAsync(() -> {
			try {
				URL url = new URL(rawUrl);
				URLConnection connection = url.openConnection();

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
		});
	}

	private LaunchJson() {
	}
}
