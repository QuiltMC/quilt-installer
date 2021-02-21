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

import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Localization;
import org.quiltmc.installer.VersionManifest;

final class ServerPanel extends AbstractPanel {
	private final JComboBox<String> minecraftVersionSelector;
	private final JComboBox<String> loaderVersionSelector;
	private final JCheckBox showSnapshotsCheckBox;
	private final JTextField installLocation;
	private final JButton selectInstallationLocation;
	private final JButton installButton;
	private boolean showSnapshots;
	private boolean downloadServer;
	private boolean generateLaunchScripts;

	ServerPanel(SwingInstaller gui) {
		super(gui);

		// Minecraft version
		{
			JComponent row1 = this.addRow();

			row1.add(new JLabel(Localization.get("gui.game.version")));
			row1.add(this.minecraftVersionSelector = new JComboBox<>());
			// Set the preferred size so we do not need to repack the window
			// The chosen width is so we are wider than 3D Shareware v1.3.4
			this.minecraftVersionSelector.setPreferredSize(new Dimension(170, 26));
			this.minecraftVersionSelector.addItem(Localization.get("gui.install.loading"));
			this.minecraftVersionSelector.setEnabled(false);

			row1.add(this.showSnapshotsCheckBox = new JCheckBox(Localization.get("gui.game.version.snapshots")));
			this.showSnapshotsCheckBox.setEnabled(false);
			this.showSnapshotsCheckBox.addItemListener(e -> {
				// Versions are already loaded, repopulate the combo box
				if (this.manifest() != null) {
					this.showSnapshots = e.getStateChange() == ItemEvent.SELECTED;
					populateMinecraftVersions(this.minecraftVersionSelector, this.manifest(), this.intermediaryVersions(), this.showSnapshots);
				}
			});
		}

		// Loader version
		{
			JComponent row2 = this.addRow();

			row2.add(new JLabel(Localization.get("gui.loader.version")));
			row2.add(this.loaderVersionSelector = new JComboBox<>());
			this.loaderVersionSelector.setPreferredSize(new Dimension(200, 26));
			this.loaderVersionSelector.addItem(Localization.get("gui.install.loading"));
			this.loaderVersionSelector.setEnabled(false);
		}

		// Install location
		{
			JComponent row3 = this.addRow();

			row3.add(new JLabel(Localization.get("gui.install-location")));
			row3.add(this.installLocation = new JTextField());
			this.installLocation.setPreferredSize(new Dimension(300, 26));
			// For server create a server subdir relative to current running directory
			this.installLocation.setText(Paths.get(System.getProperty("user.dir")).resolve("server").toString());

			row3.add(this.selectInstallationLocation = new JButton());
			this.selectInstallationLocation.setText("...");
			this.selectInstallationLocation.addActionListener(e -> {
				@Nullable
				String newLocation = displayFileChooser(this.installLocation.getText());

				if (newLocation != null) {
					this.installLocation.setText(newLocation);
				}
			});
		}

		// Server options (Server only)
		{
			JComponent row4 = this.addRow();

			JCheckBox downloadServer;
			row4.add(downloadServer = new JCheckBox(Localization.get("gui.server.download.server")));
			downloadServer.addItemListener(e -> {
				this.downloadServer = e.getStateChange() == ItemEvent.SELECTED;
			});

			JCheckBox generateLaunchScripts;
			row4.add(generateLaunchScripts = new JCheckBox(Localization.get("gui.server.generate-scripts")));
			generateLaunchScripts.addItemListener(e -> {
				this.generateLaunchScripts = e.getStateChange() == ItemEvent.SELECTED;
			});
		}

		// Install button
		{
			JComponent row5 = this.addRow();

			row5.add(this.installButton = new JButton());
			this.installButton.setEnabled(false);
			this.installButton.setText(Localization.get("gui.install.loading"));
		}
	}

	@Override
	void receiveVersions(VersionManifest manifest, List<String> loaderVersions, Collection<String> intermediaryVersions) {
		super.receiveVersions(manifest, loaderVersions, intermediaryVersions);

		populateMinecraftVersions(this.minecraftVersionSelector, manifest, intermediaryVersions, this.showSnapshots);
		this.showSnapshotsCheckBox.setEnabled(true);
		populateLoaderVersions(this.loaderVersionSelector, loaderVersions);

		this.installButton.setText(Localization.get("gui.install"));
		this.installButton.setEnabled(true);
	}
}
