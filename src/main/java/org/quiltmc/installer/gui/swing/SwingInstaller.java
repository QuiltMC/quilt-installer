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

package org.quiltmc.installer.gui.swing;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

import org.quiltmc.installer.Localization;
import org.quiltmc.installer.VersionManifest;

/**
 * The logic side of the swing gui for the installer.
 */
public final class SwingInstaller extends JFrame {
	public static final Executor SWING_EXECUTOR = SwingUtilities::invokeLater;
	private final CompletableFuture<VersionManifest> versionManifestFuture;

	public static void run() {
		try {
			// Set to OS theme so Windows and Mac users see something that looks native.
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}

		SwingUtilities.invokeLater(SwingInstaller::new);
	}

	private SwingInstaller() {
		this.versionManifestFuture = VersionManifest.create();

		// Use a tabbed pane for client/server menus
		JTabbedPane contentPane = new JTabbedPane(JTabbedPane.TOP);
		this.setupContent(contentPane);

		this.setContentPane(contentPane);
		this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		this.setTitle(Localization.get("title"));
		// TODO: Set icon
		this.pack();
		this.setLocationRelativeTo(null); // Center on screen
		this.setVisible(true);
	}

	private void setupContent(JTabbedPane contentPane) {
		contentPane.addTab(Localization.get("tab.client"), null, new ClientPanel(this), Localization.get("tab.client.tooltip"));
		contentPane.addTab(Localization.get("tab.server"), null, new ServerPanel(this), Localization.get("tab.server.tooltip"));
	}
}
