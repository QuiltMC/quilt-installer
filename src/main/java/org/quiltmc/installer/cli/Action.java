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

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Localization;

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
			// TODO: Detect the platform's executable name
			String platformExecutableName = "quilt-installer";

			InputStream usageStream = Action.class.getClassLoader().getResourceAsStream("lang/" + Locale.getDefault().toLanguageTag() + ".usage");

			if (usageStream == null) {
				usageStream = Action.class.getClassLoader().getResourceAsStream("lang/en-US.usage");

				if (usageStream == null) {
					throw new RuntimeException("Could not find usage translation for English locale");
				}
			}

			StringBuilder usage = new StringBuilder();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(usageStream))) {
				String s;

				while ((s = reader.readLine()) != null) {
					usage.append(s);
					usage.append('\n');
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Depending on the environment, we show the appropriate no-args usage.
			// Headless JVMs can't use swing, so if we have a headless JVM we just show the message describing the help text
			String noArgsUsage = (GraphicsEnvironment.isHeadless() ?
					Localization.get("cli.usage.description.headless") :
					Localization.get("cli.usage.description"));

			println(Localization.get("title") + " v" + CliInstaller.INSTALLER_VERSION);
			println("");
			println(new MessageFormat(usage.toString()).format(new String[] { platformExecutableName, noArgsUsage }));
		}
	};

	static Action listVersions(boolean snapshots) {
		return new ListVersions(snapshots);
	}

	static Action installClient(String minecraftVersion, @Nullable String loaderVersion, boolean generateProfile) {
		return new InstallClient(minecraftVersion, loaderVersion, generateProfile);
	}

	static Action installServer(String minecraftVersion, @Nullable String loaderVersion, @Nullable String serverDir) {
		return new InstallServer(minecraftVersion, loaderVersion, serverDir);
	}

	static void println(String message) {
		System.out.println(message);
	}

	static void eprintln(String message) {
		System.err.println(message);
	}

	/**
	 * Runs the action.
	 */
	abstract void run();

}
