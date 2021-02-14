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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.quiltmc.lib.gson.JsonReader;
import org.quiltmc.lib.gson.JsonToken;

public final class QuiltMeta {
	public static final Endpoint<List<Version>> LOADER_VERSIONS_ENDPOINT = createVersion("/v2/versions/loader");
	// TODO: Link to the actual meta
	public static final String DEFAULT_META_URL = "https://meta.fabricmc.net";
	private final String baseMetaUrl;
	private final Map<Endpoint<?>, Object> endpoints;

	public static CompletableFuture<QuiltMeta> create(String baseMetaUrl, Set<Endpoint<?>> endpoints) {
		Map<Endpoint<?>, CompletableFuture<?>> futures = new HashMap<>();

		for (Endpoint<?> endpoint : endpoints) {
			futures.put(endpoint, CompletableFuture.supplyAsync(() -> {
				try {
					URL url = new URL(baseMetaUrl + endpoint.endpointPath);
					URLConnection connection = url.openConnection();

					InputStreamReader stream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);

					try (JsonReader reader = new JsonReader(new BufferedReader(stream))) {
						return endpoint.deserializer.apply(reader);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e); // Handled via .exceptionally(...)
				}
			}));
		}

		CompletableFuture<Void> future = CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]));

		return future.thenApply(_v -> {
			Map<Endpoint<?>, Object> resolvedEndpoints = new HashMap<>();

			for (Map.Entry<Endpoint<?>, CompletableFuture<?>> entry : futures.entrySet()) {
				resolvedEndpoints.put(entry.getKey(), entry.getValue().join());
			}

			return new QuiltMeta(baseMetaUrl, resolvedEndpoints);
		});
	}

	private static Endpoint<List<Version>> createVersion(String endpointPath) {
		return new Endpoint<>(endpointPath, reader -> {
			if (reader.peek() != JsonToken.BEGIN_ARRAY) {
				throw new ParseException("Result of endpoint must be an object", reader);
			}

			List<Version> versions = new ArrayList<>();
			reader.beginArray();

			while (reader.hasNext()) {
				if (reader.peek() != JsonToken.BEGIN_OBJECT) {
					throw new ParseException("Version entry must be an object", reader);
				}

				String version = null;
				Boolean stable = null;
				reader.beginObject();

				while (reader.hasNext()) {
					String key = reader.nextName();

					switch (key) {
					case "version":
						if (reader.peek() != JsonToken.STRING) {
							throw new ParseException("\"version\" in entry must be a string", reader);
						}

						version = reader.nextString();
						break;
					case "stable":
						if (reader.peek() != JsonToken.BOOLEAN) {
							throw new ParseException("\"stable\" in entry must be a boolean", reader);
						}

						stable = reader.nextBoolean();
						break;
					default:
						reader.skipValue();
					}
				}

				if (version == null) {
					throw new ParseException("\"version\" field is required in a version entry", reader);
				}

				if (stable == null) {
					throw new ParseException("\"stable\" field is required in a version entry", reader);
				}

				versions.add(new Version(version, stable));

				reader.endObject();
			}

			reader.endArray();

			return versions;
		});
	}

	private QuiltMeta(String baseMetaUrl, Map<Endpoint<?>, Object> endpoints) {
		this.baseMetaUrl = baseMetaUrl;
		this.endpoints = endpoints;
	}

	public <T> T getEndpoint(Endpoint<T> endpoint) {
		Objects.requireNonNull(endpoint, "Endpoint cannot be null");

		@SuppressWarnings("unchecked")
		T result = (T) this.endpoints.get(endpoint);

		if (result == null) {
			throw new IllegalArgumentException("Endpoint had no value!");
		}

		return result;
	}

	public static final class Endpoint<T> {
		private final String endpointPath;
		private final ThrowingFunction<JsonReader, T, ParseException> deserializer;

		Endpoint(String endpointPath, ThrowingFunction<JsonReader, T, ParseException> deserializer) {
			this.endpointPath = endpointPath;
			this.deserializer = deserializer;
		}

		@Override
		public String toString() {
			return "Endpoint{endpointPath=\"" + this.endpointPath + "\"}";
		}
	}

	public static final class Version {
		private final String version;
		private final boolean stable;

		private Version(String version, boolean stable) {
			this.version = version;
			this.stable = stable;
		}

		public String version() {
			return this.version;
		}

		public boolean stable() {
			return this.stable;
		}

		@Override
		public String toString() {
			return "Version{version=\"" + this.version + "\", stable=" + this.stable + '}';
		}
	}
}
