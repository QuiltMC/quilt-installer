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

import java.io.UncheckedIOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.quiltmc.installer.Localization;
import org.quiltmc.installer.ParseException;
import org.quiltmc.installer.QuiltMeta;
import org.quiltmc.installer.VersionManifest;

/**
 * An action which lists all installable versions of Minecraft.
 */
final class ListVersions extends Action {
	/**
	 * Whether to display snapshot Minecraft versions.
	 */
	private final boolean snapshots;

	ListVersions(boolean snapshots) {
		this.snapshots = snapshots;
	}

	@Override
	void run() {
		CompletableFuture<Void> versionManifest = VersionManifest.create()
				.thenAccept(this::displayMinecraftVerions)
				.exceptionally(this::handleMinecraftVersionExceptions);

		CompletableFuture<Void> quiltMeta = QuiltMeta.create(QuiltMeta.DEFAULT_META_URL, Collections.singleton(QuiltMeta.LOADER_VERSIONS_ENDPOINT))
				.thenAccept(this::displayLoaderVersions)
				.exceptionally(e -> {
					e.printStackTrace();
					return null;
				});

		println(Localization.get("cli.lookup.versions"));

		// Wait for the lookups to complete
		CompletableFuture.allOf(versionManifest, quiltMeta).join();
	}

	private void displayMinecraftVerions(VersionManifest manifest) {
		println(Localization.createFrom("cli.latest.minecraft.release", manifest.latestRelease().id()));

		if (this.snapshots) {
			println(Localization.createFrom("cli.latest.minecraft.snapshot", manifest.latestSnapshot().id()));
		}
	}

	private void displayLoaderVersions(QuiltMeta meta) {
		List<String> endpoint = meta.getEndpoint(QuiltMeta.LOADER_VERSIONS_ENDPOINT);
		println(Localization.createFrom("cli.latest.loader", endpoint.get(0)));
	}

	private Void handleMinecraftVersionExceptions(Throwable exc) {
		eprintln(Localization.get("cli.lookup.failed.minecraft"));

		// Unwrap the completion exception.
		if (exc instanceof CompletionException) {
			exc = exc.getCause();
		}

		if (exc instanceof UncheckedIOException) {
			if (exc.getCause() instanceof UnknownHostException) {
				eprintln(Localization.get("cli.lookup.failed.connection"));
			} else {
				// IO issue?
				exc.printStackTrace();
			}
		} else if (exc instanceof ParseException) {
			eprintln(Localization.get("cli.lookup.failed.minecraft.malformed.1"));
			eprintln(Localization.createFrom("cli.lookup.failed.minecraft.malformed.2", "https://github.com/QuiltMC/quilt-installer"));
			exc.printStackTrace();
		} else {
			// Don't know, just spit it out
			exc.printStackTrace();
		}

		System.exit(2);
		return null;
	}
}
