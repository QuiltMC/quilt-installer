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

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Localization;
import org.quiltmc.installer.ParseException;
import org.quiltmc.installer.QuiltMeta;
import org.quiltmc.installer.VersionManifest;

/**
 * Represents a command line action.
 */
abstract class Action {
	/**
	 * An action which displays the help menu along with example usages.
	 */
	static final Action DISPLAY_HELP = new Action() {
		@Override
		void run() {
			this.printHelp();
			System.exit(1);
		}

		private void printHelp() {
			println(Localization.get("title") + " v" + CliInstaller.INSTALLER_VERSION);
			println(Localization.createFrom("cli.usage", CliInstaller.USAGE));
			println("");

			println(Localization.get("cli.usage.example.title"));
			println("");

			println(Localization.get("cli.usage.example.install.server.title"));
			println("\tinstall server 1.16.5");
			println("");

			println(Localization.get("cli.usage.example.listversions.snapshots.title"));
			println("\tlistVersions --snapshots");
		}
	};

	static Action listVersions(boolean snapshots) {
		return new ListVersions(snapshots);
	}

	static Action installClient(String minecraftVersion, @Nullable String loaderVersion) {
		return new InstallClient(minecraftVersion, loaderVersion);
	}

	static Action installServer(String minecraftVersion, @Nullable String loaderVersion, @Nullable String serverDir) {
		return new InstallServer(minecraftVersion, loaderVersion, serverDir);
	}

	private static void println(String message) {
		System.out.println(message);
	}

	private static void eprintln(String message) {
		System.err.println(message);
	}

	/**
	 * Runs the action.
	 */
	abstract void run();

	/**
	 * An action which lists all installable versions of Minecraft.
	 */
	private static final class ListVersions extends Action {
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
			List<QuiltMeta.Version> endpoint = meta.getEndpoint(QuiltMeta.LOADER_VERSIONS_ENDPOINT);
			println(Localization.createFrom("cli.latest.loader", endpoint.get(0).version()));
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
				// TODO: Fill in the proper link
				eprintln(Localization.createFrom("cli.lookup.failed.minecraft.malformed.2", "<__TODO_LINK_HERE>"));
				exc.printStackTrace();
			} else {
				// Don't know, just spit it out
				exc.printStackTrace();
			}

			System.exit(2);
			return null;
		}
	}

	/**
	 * An action which installs a new client instance.
	 */
	private static final class InstallClient extends Action {
		private final String minecraftVersion;
		@Nullable
		private final String loaderVersion;

		InstallClient(String minecraftVersion, @Nullable String loaderVersion) {
			this.minecraftVersion = minecraftVersion;
			this.loaderVersion = loaderVersion;
		}

		@Override
		void run() {
			// TODO
		}
	}

	/**
	 * An action which creates the server launch jar and downloads the dedicated server.
	 */
	private static final class InstallServer extends Action {
		private final String minecraftVersion;
		@Nullable
		private final String loaderVersion;
		@Nullable
		private final String serverDir;

		InstallServer(String minecraftVersion, @Nullable String loaderVersion, @Nullable String serverDir) {
			this.minecraftVersion = minecraftVersion;
			this.loaderVersion = loaderVersion;
			this.serverDir = serverDir;
		}

		@Override
		void run() {
			// TODO
		}
	}
}
