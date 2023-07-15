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
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Localization;
import org.quiltmc.installer.OsPaths;
import org.quiltmc.installer.VersionManifest;
import org.quiltmc.installer.action.Action;
import org.quiltmc.installer.action.InstallClient;

final class ClientPanel extends AbstractPanel implements Consumer<InstallClient.MessageType> {
	private final JComboBox<String> minecraftVersionSelector;
	private final JComboBox<String> loaderVersionSelector;
	private final JCheckBox showSnapshotsCheckBox;
	private final JCheckBox showLoaderBetasCheckBox;
	private final JTextField installLocation;
	private final JButton selectInstallationLocation;
	private final JButton installButton;
	private boolean showSnapshots;
	private boolean showLoaderBetas;
	private boolean generateProfile;

	ClientPanel(SwingInstaller gui) {
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

			row2.add(this.showLoaderBetasCheckBox = new JCheckBox(Localization.get("gui.loader.version.betas")));
			this.showLoaderBetasCheckBox.setEnabled(false);
			this.showLoaderBetasCheckBox.addItemListener(e -> {
				if (this.loaderVersions() != null) {
					this.showLoaderBetas = e.getStateChange() == ItemEvent.SELECTED;
					populateLoaderVersions(this.loaderVersionSelector, this.loaderVersions(), this.showLoaderBetas);
				}
			});
		}

		// Install location
		{
			JComponent row3 = this.addRow();

			row3.add(new JLabel(Localization.get("gui.install-location")));
			row3.add(this.installLocation = new JTextField());
			this.installLocation.setPreferredSize(new Dimension(300, 26));
			// For client use the default installation location
			this.installLocation.setText(OsPaths.getDefaultInstallationDir().toString());

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

		// Profile options (Client only)
		{
			JComponent row4 = this.addRow();

			JCheckBox generateProfile;
			row4.add(generateProfile = new JCheckBox(Localization.get("gui.client.generate-profile"), null, true));
			generateProfile.addItemListener(e -> {
				this.generateProfile = e.getStateChange() == ItemEvent.SELECTED;
			});
			this.generateProfile = true;
		}

		// Install button
		{
			JComponent row5 = this.addRow();

			row5.add(this.installButton = new JButton());
			this.installButton.setEnabled(false);
			this.installButton.setText(Localization.get("gui.install.loading"));
			this.installButton.addActionListener(this::install);
		}
	}

	private void install(ActionEvent event) {
		Action<InstallClient.MessageType> action = Action.installClient(
				(String) this.minecraftVersionSelector.getSelectedItem(),
				(String) this.loaderVersionSelector.getSelectedItem(),
				this.installLocation.getText(),
				this.generateProfile
		);

		action.run(this);

		showInstalledMessage();
	}

	@Override
	void receiveVersions(VersionManifest manifest, List<String> loaderVersions, Collection<String> intermediaryVersions) {
		super.receiveVersions(manifest, loaderVersions, intermediaryVersions);

		populateMinecraftVersions(this.minecraftVersionSelector, manifest, intermediaryVersions, this.showSnapshots);
		this.showSnapshotsCheckBox.setEnabled(true);
		populateLoaderVersions(this.loaderVersionSelector, loaderVersions, this.showLoaderBetas);
		this.showLoaderBetasCheckBox.setEnabled(true);

		this.installButton.setText(Localization.get("gui.install"));
		this.installButton.setEnabled(true);
	}

	@Override
	public void accept(InstallClient.MessageType messageType) {
	}
}
