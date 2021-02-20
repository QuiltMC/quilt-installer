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

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Localization;
import org.quiltmc.installer.cli.CliInstaller;

/**
 * Represents an installer action to be performed.
 *
 * <M> the status message type indicating the progress of an action
 */
public abstract class Action<M> {
	/**
	 * An action which displays the help menu along with example usages.
	 */
	public static final Action<Void> DISPLAY_HELP = new Action<Void>() {
		@Override
		public void run(Executor displayExecutor, Consumer<Void> statusTracker) {
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

	public static Action<Void> listVersions(boolean snapshots) {
		return new ListVersions(snapshots);
	}

	public static Action<InstallClient.MessageType> installClient(String minecraftVersion, @Nullable String loaderVersion, boolean generateProfile) {
		return new InstallClient(minecraftVersion, loaderVersion, generateProfile);
	}

	public static Action<InstallServer.MessageType> installServer(String minecraftVersion, @Nullable String loaderVersion, @Nullable String serverDir) {
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
	 *
	 * @param displayExecutor the executor used to schedule asynchronous calls to update the status
	 * @param statusTracker the consumer to send updates about the progress of this action.
	 */
	public abstract void run(Executor displayExecutor, Consumer<M> statusTracker);
}
