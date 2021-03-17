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

import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.Collection;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Localization;
import org.quiltmc.installer.VersionManifest;

abstract class AbstractPanel extends JPanel {
	final SwingInstaller gui;
	@Nullable
	private VersionManifest manifest;
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

	void receiveVersions(VersionManifest manifest, List<String> loaderVersions, Collection<String> intermediaryVersions) {
		this.manifest = manifest;
		this.loaderVersions = loaderVersions;
		this.intermediaryVersions = intermediaryVersions;
	}

	@Nullable
	VersionManifest manifest() {
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

	static void populateMinecraftVersions(JComboBox<String> comboBox, VersionManifest manifest, Collection<String> intermediaryVersions, boolean snapshots) {
		// Setup the combo box for Minecraft version selection
		comboBox.removeAllItems();

		for (VersionManifest.Version version : manifest) {
			if (version.type().equals("release") || (version.type().equals("snapshot") && snapshots)) {
				if (intermediaryVersions.contains(version.id())) {
					comboBox.addItem(version.id());
				}
			}
		}

		comboBox.setEnabled(true);
	}

	static void populateLoaderVersions(JComboBox<String> comboBox, List<String> loaderVersions) {
		comboBox.removeAllItems();

		for (String loaderVersion : loaderVersions) {
			comboBox.addItem(loaderVersion);
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
	 * Show a popup with hyperlinks and full html formatting.
	 * @return if the user pressed "ok", "yes", etc. (showOptionDialog returned 0)
	 */
	protected static boolean showPopup(String title, String description, int optionType, int messageType) {
		JEditorPane pane = new JEditorPane("text/html",
				"<html><body style=\"" + buildEditorPaneStyle() + "\">" + description + "</body></html>");
		pane.setEditable(false);
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
				displayError(throwable);
			}
		});
		return JOptionPane.showOptionDialog(null, pane, title, optionType, messageType, null, null, null) == 0;
	}

	protected static void showInstalledMessage() {
		showPopup(Localization.get("dialog.install.successful"), Localization.createFrom("dialog.install.successful.description", "https://quiltmc.org/qsl"),
				JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE);
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

	static void displayError(Throwable throwable) {

	}
}
