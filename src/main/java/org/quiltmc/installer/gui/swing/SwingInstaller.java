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

import org.quiltmc.installer.Localization;
import org.quiltmc.installer.QuiltMeta;
import org.quiltmc.installer.VersionManifest;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The logic side of the swing gui for the installer.
 */
public final class SwingInstaller extends JFrame {
	public static final Executor SWING_EXECUTOR = SwingUtilities::invokeLater;
	private final ClientPanel clientPanel;
	private final ServerPanel serverPanel;

	public static void run() {
		try {
			String clazz = UIManager.getSystemLookAndFeelClassName();

			if (clazz.equals(UIManager.getCrossPlatformLookAndFeelClassName()) && useGtk()) {
				// TODO: it may be worth poking around internals a bit more to confirm this class actually exists
				clazz = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
			}

			UIManager.setLookAndFeel(clazz);
		} catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}

		SwingUtilities.invokeLater(SwingInstaller::new);
	}

	private static boolean useGtk() {
		Toolkit toolkit = Toolkit.getDefaultToolkit();

		try {
			Class<?> clazz = Class.forName("sun.awt.SunToolkit");

			if (!clazz.isInstance(toolkit)) {
				return false;
			}
			// note: this will throw an exception in later java versions, ask glitch if an uglier version is needed that doesn't break
			return (boolean) clazz.getDeclaredMethod("isNativeGTKAvailable").invoke(toolkit);
		} catch (Throwable e) {
			e.printStackTrace();
		}

		return false;
	}

	private SwingInstaller() {
		try {
			// Use a tabbed pane for client/server menus
			JTabbedPane contentPane = new JTabbedPane(JTabbedPane.TOP);
			contentPane.addTab(Localization.get("tab.client"), null, this.clientPanel = new ClientPanel(this), Localization.get("tab.client.tooltip"));
			contentPane.addTab(Localization.get("tab.server"), null, this.serverPanel = new ServerPanel(this), Localization.get("tab.server.tooltip"));

			// Start version lookup before we show the window
			// Lookup loader and intermediary
			Set<QuiltMeta.Endpoint<?>> endpoints = new HashSet<>();
			endpoints.add(QuiltMeta.LOADER_VERSIONS_ENDPOINT);
			endpoints.add(QuiltMeta.INTERMEDIARY_VERSIONS_ENDPOINT);

			QuiltMeta.create(QuiltMeta.DEFAULT_META_URL, endpoints).thenAcceptBothAsync(VersionManifest.create(), ((quiltMeta, manifest) -> {
				List<String> loaderVersions = quiltMeta.getEndpoint(QuiltMeta.LOADER_VERSIONS_ENDPOINT);
				Collection<String> intermediaryVersions = quiltMeta.getEndpoint(QuiltMeta.INTERMEDIARY_VERSIONS_ENDPOINT).keySet();

				this.clientPanel.receiveVersions(manifest, loaderVersions, intermediaryVersions);
				this.serverPanel.receiveVersions(manifest, loaderVersions, intermediaryVersions);
			}), SWING_EXECUTOR).exceptionally(e -> {
				e.printStackTrace();
				AbstractPanel.displayError(e);
				return null;
			});

			this.setContentPane(contentPane);
			this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			this.setTitle(Localization.get("title"));
			// TODO: Set icon
			this.pack();
			this.setLocationRelativeTo(null); // Center on screen
			this.setResizable(false);
			this.setVisible(true);
		} catch (HeadlessException e){
			System.exit(1); // Don't know how we got here
			throw new IllegalStateException(); // Make javac happy
		} catch (Throwable t) {
			AbstractPanel.displayError(t);
			System.exit(1); // TODO: May be overkill?
			throw new IllegalStateException(); // Make javac happy
		}
	}
}
