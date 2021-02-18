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
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.ParseException;
import org.quiltmc.lib.gson.JsonReader;
import org.quiltmc.lib.gson.JsonToken;

public final class LaunchJson {
	public static final String MAVEN_LINK = "https://maven.fabricmc.net/";
	public static final String LOADER_ARTIFACT_GROUP = "net/fabricmc";
	public static final String LOADER_ARTIFACT_NAME = "fabric-loader";
	// TODO: Switch to quilt once we publish loader
//	public static final String MAVEN_LINK = "https://maven.quiltmc.org/repository/release";
//	public static final String LOADER_ARTIFACT_GROUP = "org/quiltmc";
//	public static final String LOADER_ARTIFACT_NAME = "quilt-loader";

	private static String createInstallerMetaUrl(String loaderVersion) {
		Objects.requireNonNull(loaderVersion, "Loader version cannot be null");

		return String.format("%s/%s/%s/%s/%3$s-%4$s.json", MAVEN_LINK, LOADER_ARTIFACT_GROUP, LOADER_ARTIFACT_NAME, loaderVersion);
	}

	public static CompletableFuture<LaunchJson> create(String loaderVersion) {
		String rawUrl = createInstallerMetaUrl(loaderVersion);

		return CompletableFuture.supplyAsync(() -> {
			try {
				URL url = new URL(rawUrl);
				URLConnection connection = url.openConnection();

				InputStreamReader stream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);

				try (JsonReader reader = new JsonReader(stream)) {
					return new LaunchJson(reader);
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e); // Handled via .exceptionally(...)
			}
		});
	}

	@Nullable
	private final String mainClass;
	@Nullable
	private final String mainServerClass;
	private final List<String> arguments;
	private final List<Library> libraries;

	/**
	 * Reads a fabric-launch.json and converts it to Minecraft format.
	 *
	 * @param reader the json reader for the fabric-launch.json
	 */
	public LaunchJson(JsonReader reader) throws IOException {
		// Whether loader should use legacy-launcher to start the game.
		@Nullable
		Boolean legacyLauncher = null;

		if (reader.peek() != JsonToken.BEGIN_OBJECT) {
			throw new ParseException("quilt-launch.json must be an object", reader);
		}

		// Main classes - not used if legacy-launcher is in use
		@Nullable
		String mainClass = null;
		@Nullable
		String mainServerClass = null;

		List<String> arguments = new ArrayList<>();
		List<Library> libraries = new ArrayList<>();

		reader.beginObject();

		while (reader.hasNext()) {
			String key = reader.nextName();

			switch (key) {
			case "mainClass":
				if (legacyLauncher != null) {
					throw new ParseException("Launch json specifies both use of mainClass and legacy-launcher", reader);
				}

				// Not legacy launcher
				legacyLauncher = false;

				// Simple mainClass
				if (reader.peek() == JsonToken.STRING) {
					mainClass = reader.nextString();
				// Client and server mainClass
				} else if (reader.peek() == JsonToken.BEGIN_OBJECT) {
					reader.beginObject();

					while (reader.hasNext()) {
						String mainClassSide = reader.nextName();

						if (mainClassSide.equals("common")) {
							if (reader.peek() != JsonToken.STRING) {
								throw new ParseException("common mainClass must be a string", reader);
							}

							mainClass = reader.nextString();
						} else if (mainClassSide.equals("server")) {
							if (reader.peek() != JsonToken.STRING) {
								throw new ParseException("server mainClass must be a string", reader);
							}

							mainServerClass = reader.nextString();
						} else {
							reader.skipValue(); // Unsupported side, skip it
						}
					}

					reader.endObject();
				} else {
					throw new ParseException("\"mainClass\" must be an object or string", reader);
				}

				break;
			case "launchwrapper":
				if (legacyLauncher != null) {
					throw new ParseException("Launch json specifies both use of mainClass and legacy-launcher", reader);
				}

				// Use legacy-launcher
				legacyLauncher = true;

				reader.beginObject();

				String tweakClass = null;

				while (reader.hasNext()) {
					if (reader.nextName().equals("tweakers")) {
						if (reader.peek() != JsonToken.BEGIN_OBJECT) {
							throw new ParseException("tweakers must be in an object", reader);
						}

						reader.beginObject();

						while (reader.hasNext()) {
							if (reader.nextName().equals("client")) {
								if (reader.peek() != JsonToken.BEGIN_ARRAY) {
									throw new ParseException("Client tweak classes must be in an array", reader);
								}

								boolean first = true;
								reader.beginArray();

								while (reader.hasNext()) {
									if (first) {
										if (reader.peek() != JsonToken.STRING) {
											throw new ParseException("Tweak classes array must only contain strings", reader);
										}

										first = false;
										tweakClass = reader.nextString();
									} else {
										reader.skipValue();
									}
								}

								reader.endArray();
							} else {
								reader.skipValue();
							}
						}

						reader.endObject();
					} else {
						reader.skipValue();
					}
				}

				reader.endObject();

				if (tweakClass == null) {
					throw new ParseException("No tweak class was present", reader);
				}

				arguments.add("--tweakClass");
				arguments.add(tweakClass);

				break;
			case "libraries":
				if (reader.peek() != JsonToken.BEGIN_OBJECT) {
					throw new ParseException("libraries field must be an object", reader);
				}

				reader.beginObject();

				while (reader.hasNext()) {
					String side = reader.nextName();

					if (side.equals("client") || side.equals("server")) {
						if (reader.peek() != JsonToken.BEGIN_ARRAY) {
							throw new ParseException("a side's libraries must be in an array", reader);
						}

						reader.beginArray();

						while (reader.hasNext()) {
							if (reader.peek() != JsonToken.BEGIN_OBJECT) {
								throw new ParseException("Library must be an object", reader);
							}

							reader.beginObject();

							String name = null;
							@Nullable
							String url = null;

							while (reader.hasNext()) {
								switch (reader.nextName()) {
								case "name":
									if (reader.peek() != JsonToken.STRING) {
										throw new ParseException("Library name must be a string", reader);
									}

									name = reader.nextString();
									break;
								case "url":
									if (reader.peek() != JsonToken.STRING) {
										throw new ParseException("Library url must be a string", reader);
									}

									url = reader.nextString();
									break;
								default:
									reader.skipValue();
								}
							}

							if (name == null) {
								throw new ParseException("Library did not have a name", reader);
							}

							reader.endObject();

							libraries.add(new Library(name, url));
						}

						reader.endArray();
					} else {
						reader.skipValue();
					}
				}

				reader.endObject();

				break;
			default:
				// Unsupported key - skip value
				reader.skipValue();
			}
		}

		reader.endObject();

		// Validate we have a complete launch json
		if (legacyLauncher == null) {
			throw new ParseException("quilt-launch.json did not contain mainClass or tweakClass", reader);
		}

		this.mainClass = mainClass;
		this.mainServerClass = mainServerClass;
		this.arguments = arguments;
		this.libraries = libraries;
	}

	public static final class Library {
		public Library(String name, @Nullable String url) {

		}
	}
}
