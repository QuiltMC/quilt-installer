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

package org.quiltmc.installer.action;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.QuiltMeta;
import org.quiltmc.installer.VersionManifest;

public final class MinecraftInstallation {
	/**
	 * Verifies the specified game version exists, has intermediary mappings.
	 * If the loader version is not specified, also looks up the loader version.
	 *
	 * @param gameVersion the game version
	 * @param loaderVersion the override for the loader version to use
	 * @return a future containing the loader version to use
	 */
	public static CompletableFuture<String> getInfo(String gameVersion, @Nullable String loaderVersion) {
		CompletableFuture<String> minecraftVersion = VersionManifest.create().thenApply(manifest -> {
			if (manifest.getVersion(gameVersion) != null) {
				return gameVersion;
			}

			throw new IllegalArgumentException(String.format("Minecraft version %s does not exist", gameVersion));
		});

		Set<QuiltMeta.Endpoint<?>> endpoints = new HashSet<>();
		endpoints.add(QuiltMeta.LOADER_VERSIONS_ENDPOINT);
		endpoints.add(QuiltMeta.INTERMEDIARY_VERSIONS_ENDPOINT);

		CompletableFuture<QuiltMeta> metaFuture = QuiltMeta.create(QuiltMeta.DEFAULT_META_URL, endpoints);

		// Verify we actually have intermediary for the specified version
		CompletableFuture<Void> intermediary = minecraftVersion.thenCompose(mcVersion -> metaFuture.thenAccept(meta -> {
			Map<String, String> intermediaryVersions = meta.getEndpoint(QuiltMeta.INTERMEDIARY_VERSIONS_ENDPOINT);

			if (intermediaryVersions.get(gameVersion) == null) {
				throw new IllegalArgumentException(String.format("Minecraft version %s exists but has no intermediary", gameVersion));
			}
		}));

		CompletableFuture<String> loaderVersionFuture = metaFuture.thenApply(meta -> {
			List<String> versions = meta.getEndpoint(QuiltMeta.LOADER_VERSIONS_ENDPOINT);

			if (loaderVersion != null) {
				if (!versions.contains(loaderVersion)) {
					throw new IllegalStateException(String.format("Specified loader version %s was not found", loaderVersion));
				}

				return versions.get(versions.indexOf(loaderVersion));
			}

			if (versions.size() == 0) {
				throw new IllegalStateException("No loader versions were found");
			}

			// Choose latest version
			return versions.get(0);
		});

		return CompletableFuture.allOf(minecraftVersion, intermediary, loaderVersionFuture).thenApply(_v -> {
			try {
				return loaderVersionFuture.get();
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private MinecraftInstallation() {
	}
}
