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
import java.nio.file.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.swing.*;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Gsons;
import org.quiltmc.installer.Localization;
import org.quiltmc.installer.VersionManifest;
import org.quiltmc.installer.action.Action;
import org.quiltmc.installer.action.InstallServer;
import org.quiltmc.json5.JsonReader;

final class ServerPanel extends AbstractPanel implements Consumer<InstallServer.MessageType> {
	private final JComboBox<String> minecraftVersionSelector;
	private final JComboBox<String> loaderVersionSelector;
	private final JCheckBox showSnapshotsCheckBox;
	private final JTextField installLocation;
	private final JButton selectInstallationLocation;
	private final JButton installButton;
	private final JCheckBox downloadServerJarButton;
	private final JCheckBox generateLaunchScriptsButton;
	private boolean showSnapshots;
	private boolean downloadServer = true;
	private boolean generateLaunchScripts = false;

	private boolean downloadServerAutoSelected = true;
	private boolean generateLaunchScriptsAutoSelected = true;

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
			this.minecraftVersionSelector.addActionListener(e -> updateFlags());
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
					updateFlags();
				}
			});
		}

		// Server options (Server only)
		{
			JComponent row4 = this.addRow();

			row4.add(downloadServerJarButton = new JCheckBox(Localization.get("gui.server.download.server"), this.downloadServer));
			downloadServerJarButton.addItemListener(e -> {
				this.downloadServer = e.getStateChange() == ItemEvent.SELECTED;
			});

			row4.add(generateLaunchScriptsButton = new JCheckBox(Localization.get("gui.server.generate-script"), this.generateLaunchScripts));
			generateLaunchScriptsButton.addItemListener(e -> {
				this.generateLaunchScripts = e.getStateChange() == ItemEvent.SELECTED;
			});
			generateLaunchScriptsButton.setToolTipText("Coming soon!");
			generateLaunchScriptsButton.setEnabled(false);
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
		updateFlags();
		this.showSnapshotsCheckBox.setEnabled(true);
		populateLoaderVersions(this.loaderVersionSelector, loaderVersions);

		this.installButton.setText(Localization.get("gui.install"));
		this.installButton.setEnabled(true);
		this.installButton.addActionListener(this::install);
	}

	private void install(ActionEvent event) {
		boolean cancel = false;

		if (!downloadServer && downloadServerAutoSelected) {
			cancel = !AbstractPanel.showPopup(Localization.get("dialog.install.server.no-jar"), Localization.get("dialog.install.server.no-jar.description"),
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		} else if (downloadServer && !downloadServerAutoSelected) {
		 	cancel = !AbstractPanel.showPopup(Localization.get("dialog.install.server.overwrite-jar"), Localization.get("dialog.install.server.overwrite-jar.description"),
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		}

		if (!generateLaunchScripts && generateLaunchScriptsAutoSelected) {
			cancel = cancel | !AbstractPanel.showPopup(Localization.get("dialog.server.noscript"), Localization.get("dialog.server.noscript.description"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		} else if (generateLaunchScripts && !generateLaunchScriptsAutoSelected) {
			cancel = cancel | !AbstractPanel.showPopup(Localization.get("dialog.server.overwritescript"), Localization.get("dialog.server.overwritescript.description"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		}

		if (cancel) {
			return;
		}

		InstallServer action = Action.installServer(
				(String) this.minecraftVersionSelector.getSelectedItem(),
				(String) this.loaderVersionSelector.getSelectedItem(),
				this.installLocation.getText(),
				this.generateLaunchScripts,
				this.downloadServer
		);

		action.run(this);

		showInstalledMessage();
	}

	private void updateFlags() {
		// in case someone has an exceptionally slow disk
		CompletableFuture.supplyAsync(() -> {
			Path serverJar = Paths.get(this.installLocation.getText()).resolve("server.jar");

			if (Files.exists(serverJar)) {
				try (FileSystem fs = FileSystems.newFileSystem(serverJar, (ClassLoader) null)) {
					Path versionJson = fs.getPath("version.json");
					// because of type erasure this should work even if other things are added in the format
					//noinspection unchecked
					Map<String, String> map = (Map<String, String>) Gsons.read(JsonReader.json(Files.newBufferedReader(versionJson)));
					return map.get("id");
				} catch (Throwable ex) {
					// It's corrupt, not available, whatever, let's just overwrite it
				}
			}

			return "";
		}).thenAcceptAsync(version ->
				this.downloadServerJarButton.setSelected(!version.equals(this.minecraftVersionSelector.getSelectedItem())),
				SwingUtilities::invokeLater);

		// TODO detect install script
	}

	@Override
	public void accept(InstallServer.MessageType messageType) {
	}
}
