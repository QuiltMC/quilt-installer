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

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Localization;
import org.quiltmc.installer.util.modrinth.v2.ModrinthApiV2;
import org.quiltmc.installer.util.mojang.MinecraftMeta;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

abstract class AbstractPanel extends JPanel {
	final SwingInstaller gui;
	@Nullable
	private MinecraftMeta manifest;
	@Nullable
	private List<String> loaderVersions;
	@Nullable
	private Collection<String> intermediaryVersions;

	AbstractPanel(SwingInstaller gui) {
		this.gui = gui;

		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
	}

	JComponent addRow() {
		JPanel rowPanel = new JPanel(new FlowLayout());
		this.add(rowPanel);
		return rowPanel;
	}

	void receiveVersions(MinecraftMeta manifest, List<String> loaderVersions, Collection<String> intermediaryVersions) {
		this.manifest = manifest;
		this.loaderVersions = loaderVersions;
		this.intermediaryVersions = intermediaryVersions;
	}

	@Nullable
	MinecraftMeta manifest() {
		return this.manifest;
	}

	@Nullable
	public List<String> loaderVersions() {
		return this.loaderVersions;
	}

	@Nullable
	public Collection<String> intermediaryVersions() {
		return this.intermediaryVersions;
	}

	static void populateMinecraftVersions(JComboBox<String> comboBox, MinecraftMeta manifest, Collection<String> intermediaryVersions, boolean allowSnapshots) {
		// Set up the combo box for Minecraft version selection
		comboBox.removeAllItems();

		for (var version : manifest) {
			if (shouldAddVersion(version, intermediaryVersions, allowSnapshots)) comboBox.addItem(version.id());
		}
		comboBox.setEnabled(true);
	}

	static void populateLoaderVersions(JComboBox<String> comboBox, List<String> loaderVersions, boolean betas) {
		comboBox.removeAllItems();

		for (String loaderVersion : loaderVersions) {
			if (betas || !loaderVersion.contains("-")) {
				comboBox.addItem(loaderVersion);
			}
		}

		comboBox.setEnabled(true);
	}

	@Nullable
	static String displayFileChooser(String initialDir) {
		JFileChooser chooser = new JFileChooser();
		chooser.setCurrentDirectory(new File(initialDir));
		chooser.setDialogTitle(Localization.get("gui.install-location.select"));
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);

		if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			return chooser.getSelectedFile().getAbsolutePath();
		}

		return null;
	}

	/**
	 * Show a popup with hyperlinks and full HTML formatting.
	 * @return if the user pressed "ok", "yes", etc. (showOptionDialog returned JOptionPane.OK_OPTION)
	 */
	protected static boolean showPopup(String title, String description, int optionType, int messageType) {
		JEditorPane pane = new JEditorPane("text/html",
				"<html><body style=\"" + buildEditorPaneStyle() + "\">" + description + "</body></html>");
		pane.setEditable(false);
		pane.setFocusable(false);
		pane.addHyperlinkListener(e -> {
			try {
				if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
					if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
						Desktop.getDesktop().browse(e.getURL().toURI());
					} else {
						throw new UnsupportedOperationException("Failed to open " + e.getURL().toString());
					}
				}
			} catch (Throwable throwable) {
				displayError(pane, throwable);
			}
		});
		return JOptionPane.showOptionDialog(null, pane, title, optionType, messageType, null, null, null) == JOptionPane.OK_OPTION;
	}

	protected static void showInstalledMessage(String minecraftVersion) {
		var qslModrinthId = "qvIfYCYJ";

		// check whether to recommend QSL or FAPI
		var split = minecraftVersion.split("\\.", 3);
		int major;
		int minor;
		try {
			var idx = 0;
			if(split[0].equals("1")) {
				idx = 1;
			}

			major = Integer.parseInt(split[idx]);
			minor = split.length > idx + 1 ? Integer.parseInt(split[idx + 1]) : 0;
		} catch (NumberFormatException e) {
            throw new RuntimeException("Unable to decode current Minecraft version: " + minecraftVersion, e);
        }

		CompletableFuture<String> qslCheck;
		if(major < 21 || (major == 21 && minor == 0)) {
			qslCheck =  ModrinthApiV2.getProjectVersions(qslModrinthId, List.of(minecraftVersion)).thenApply(qslVersions -> {
				if(qslVersions.isEmpty()) {
					return Localization.createFrom("dialog.install.successful.description.no-qsl", "https://quiltmc.org/qsl", "https://modrinth.com/mod/fabric-api");
				}
				else {
					return Localization.createFrom("dialog.install.successful.description.qsl", "https://quiltmc.org/qsl");
				}
			});
		}
		else {
			qslCheck = CompletableFuture.completedFuture(Localization.createFrom("dialog.install.successful.description.fabric-api", "https://modrinth.com/mod/fabric-api"));
		}

		qslCheck.thenAccept(downloadMessage -> SwingUtilities.invokeLater(() -> showPopup(Localization.get("dialog.install.successful"), downloadMessage,
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE))).join();
	}

	private static String buildEditorPaneStyle() {
		JLabel label = new JLabel();
		Font font = label.getFont();
		Color color = label.getBackground();

		return String.format(
				"font-family:%s;font-weight:%s;font-size:%dpt;background-color: rgb(%d,%d,%d);",
				font.getFamily(), (font.isBold() ? "bold" : "normal"), font.getSize(), color.getRed(), color.getGreen(), color.getBlue()
		);
	}

	 static void displayError(Component parent, Throwable throwable) {
		JOptionPane.showMessageDialog(parent, throwable.toString(), "Error!", JOptionPane.ERROR_MESSAGE);
		throwable.printStackTrace();
	}

	private static boolean shouldAddVersion(MinecraftMeta.MinecraftVersion version, Collection<String> intermediaryVersions, boolean allowSnapshots) {
		if(!intermediaryVersions.contains(version.id()) && version.isObfuscated()) return false;

		boolean isRelease = version.type().equals(MinecraftMeta.MinecraftVersion.TYPE_RELEASE);
		boolean isSnapshot = version.type().equals(MinecraftMeta.MinecraftVersion.TYPE_SNAPSHOT);

		return isRelease || (allowSnapshots && isSnapshot);
	}
}
