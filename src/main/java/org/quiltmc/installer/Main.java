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

import java.awt.GraphicsEnvironment;

import org.quiltmc.installer.gui.swing.SwingInstaller;

public final class Main {
	public static void main(String[] args) {
		// Only use CLI mode if there are any arguments or we have a headless JVM
		if (GraphicsEnvironment.isHeadless() || args.length != 0) {
			CliInstaller.run(args);
			return;
		}

		SwingInstaller.run();
	}

	private Main() {
	}
}
