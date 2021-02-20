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

package org.quiltmc.installer.cli;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.OsPaths;
import org.quiltmc.installer.QuiltMeta;
import org.quiltmc.installer.VersionManifest;
import org.quiltmc.installer.client.LaunchJson;
import org.quiltmc.installer.client.LauncherProfiles;
import org.quiltmc.lib.gson.JsonWriter;

/**
 * An action which installs a new client instance.
 */
final class InstallClient extends Action {
	private final String minecraftVersion;
	@Nullable
	private final String loaderVersion;

	InstallClient(String minecraftVersion, @Nullable String loaderVersion) {
		this.minecraftVersion = minecraftVersion;
		this.loaderVersion = loaderVersion;
	}

	@Override
	void run() {
		/*
		 * Installing the client involves a few steps:
		 * 1. Get the launcher directory from the OS
		 * 2. Lookup if the minecraftVersion specified exists and then if it has intermediary
		 * 3. Lookup if the specified loaderVersion exists, looking up the latest if null
		 * 4. Get the launch metadata for the specified version of loader
		 * 5. Game version and profile name into the launch json
		 * 6. Write it
		 * 7. (Optional) create profile if needed
		 */

		Path installationDir = OsPaths.getDefaultInstallationDir();

		CompletableFuture<String> minecraftVersion = VersionManifest.create().thenApply(manifest -> {
			if (manifest.getVersion(this.minecraftVersion) != null) {
				return this.minecraftVersion;
			}

			throw new IllegalArgumentException(String.format("Minecraft version %s does not exist", this.minecraftVersion));
		});

		Set<QuiltMeta.Endpoint<?>> endpoints = new HashSet<>();
		endpoints.add(QuiltMeta.LOADER_VERSIONS_ENDPOINT);
		endpoints.add(QuiltMeta.INTERMEDIARY_VERSIONS_ENDPOINT);

		CompletableFuture<QuiltMeta> metaFuture = QuiltMeta.create(QuiltMeta.DEFAULT_META_URL, endpoints);

		// Returns the maven url of the intermediary for the specified minecraft version
		CompletableFuture<String> intermediary = minecraftVersion.thenCompose(mcVersion -> metaFuture.thenApply(meta -> {
			Map<String, String> intermediaryVersions = meta.getEndpoint(QuiltMeta.INTERMEDIARY_VERSIONS_ENDPOINT);

			if (intermediaryVersions.get(this.minecraftVersion) == null) {
				throw new IllegalArgumentException(String.format("Minecraft version %s exists but has no intermediary", this.minecraftVersion));
			}

			return intermediaryVersions.get(this.minecraftVersion);
		}));

		CompletableFuture<String> loaderVersionFuture = metaFuture.thenApply(meta -> {
			List<String> versions = meta.getEndpoint(QuiltMeta.LOADER_VERSIONS_ENDPOINT);

			if (this.loaderVersion != null) {
				if (!versions.contains(this.loaderVersion)) {
					throw new IllegalStateException(String.format("Specified loader version %s was not found", this.loaderVersion));
				}

				return versions.get(versions.indexOf(this.loaderVersion));
			}

			if (versions.size() == 0) {
				throw new IllegalStateException("No loader versions were found");
			}

			// Choose latest version
			return versions.get(0);
		});

		CompletableFuture<LaunchJson> launchJsonFuture = loaderVersionFuture.thenCompose(LaunchJson::create);

		// Weave the chains together
		CompletableFuture.allOf(minecraftVersion, intermediary, loaderVersionFuture, launchJsonFuture).thenAccept(_v -> {
			try {
				String gameVersion = minecraftVersion.get();
				String intermediaryMaven = intermediary.get();
				String loaderVersion = loaderVersionFuture.get();
				LaunchJson launchJson = launchJsonFuture.get();

				println(gameVersion);
				println(intermediaryMaven);
				println(launchJson.toString());

				String profileName = String.format("%s-%s-%s",
						LaunchJson.LOADER_ARTIFACT_NAME,
						loaderVersion,
						gameVersion
				);

				launchJson.setId(profileName);
				launchJson.setInheritedFrom(gameVersion);

				// Loader
				launchJson.addLibrary(
						LaunchJson.ARTIFACT_GROUP.replaceAll("/", ".") + ":" + LaunchJson.LOADER_ARTIFACT_NAME + ":" + loaderVersion,
						LaunchJson.MAVEN_LINK
				);

				// Mappings
				launchJson.addLibrary(
						LaunchJson.ARTIFACT_GROUP.replaceAll("/", ".") + ":" + LaunchJson.MAPPINGS_ARTIFACT_NAME + ":" + gameVersion,
						LaunchJson.MAVEN_LINK
				);

				// Directories
				Path versionsDir = installationDir.resolve("versions");
				Path profileDir = versionsDir.resolve(profileName);
				Path profileJson = profileDir.resolve(profileName + ".json");

				try {
					Files.createDirectories(profileDir);
				} catch (IOException e) {
					throw new UncheckedIOException(e); // Handle via exceptionally
				}

				/*
				 * Abuse some of the vanilla launcher's undefined behavior:
				 *
				 * Assumption is the profile name is the same as the maven artifact.
				 * The profile name we set is a combination of two artifacts (loader + mappings).
				 * As long as the jar file exists of the same name the launcher won't complain.
				 */

				// Make our pretender jar
				try {
					Files.createFile(profileDir.resolve(profileName + ".jar"));
				} catch (FileAlreadyExistsException ignore) {
					// Pretender jar already exists
				} catch (IOException e) {
					throw new UncheckedIOException(e); // Handle via exceptionally
				}

				// Create our launch json
				try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(Files.newOutputStream(profileJson, StandardOpenOption.CREATE_NEW)))) {
					launchJson.write(writer);
				} catch (IOException e) {
					throw new UncheckedIOException(e); // Handle via exceptionally
				}

				// Create the profile
				// TODO: Check if creating the profile is enabled
				try {
					LauncherProfiles.updateProfiles(installationDir, profileName, gameVersion);
				} catch (IOException e) {
					throw new UncheckedIOException(e); // Handle via exceptionally
				}
			} catch (InterruptedException | ExecutionException e) {
				// Should not happen since we allOf'd it.
				// Anyways if it does happen let exceptionally deal with it
				throw new RuntimeException(e);
			}
		}).exceptionally(e -> {
			e.printStackTrace();
			return null;
		}).join();
	}
}
