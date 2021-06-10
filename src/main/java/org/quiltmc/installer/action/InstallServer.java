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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Gsons;
import org.quiltmc.installer.LaunchJson;
import org.quiltmc.installer.VersionManifest;
import org.quiltmc.json5.JsonReader;

/**
 * An action which creates the server launch jar and downloads the dedicated server.
 */
public final class InstallServer extends Action<InstallServer.MessageType> {
	public static final String SERVICES_DIR = "META-INF/services/";
	private final String minecraftVersion;
	@Nullable
	private final String loaderVersion;
	private final String installDir;
	private final boolean createScripts;
	private final boolean installServer;
	private MinecraftInstallation.InstallationInfo installationInfo;
	private Path installedDir;

	InstallServer(String minecraftVersion, @Nullable String loaderVersion, String installDir, boolean createScripts, boolean installServer) {
		this.minecraftVersion = minecraftVersion;
		this.loaderVersion = loaderVersion;
		this.installDir = installDir;
		this.createScripts = createScripts;
		this.installServer = installServer;
	}

	@Override
	public void run(Consumer<MessageType> statusTracker) {
		Path installDir;

		if (this.installDir == null) {
			// Make a new installation in `server` subfolder
			installDir = Paths.get(System.getProperty("user.dir")).resolve("server");
		} else {
			installDir = Paths.get(this.installDir);
		}

		this.installedDir = installDir;

		println(String.format("Installing server launcher at: %s", installDir));

		if (this.loaderVersion == null) {
			println(String.format("Installing server launcher for %s", this.minecraftVersion));
		} else {
			println(String.format("Installing server launcher for %s with loader %s", this.minecraftVersion, this.loaderVersion));
		}

		CompletableFuture<MinecraftInstallation.InstallationInfo> installationInfoFuture = MinecraftInstallation.getInfo(this.minecraftVersion, this.loaderVersion);

		installationInfoFuture.thenCompose(installationInfo -> {
			this.installationInfo = installationInfo;
			return LaunchJson.get(this.minecraftVersion, installationInfo.loaderVersion(), "/v3/versions/loader/%s/%s/server/json");
		}).thenCompose(launchJson -> {
			println("Installing libraries");

			// Now we read the server's launch json
			try (JsonReader reader = JsonReader.createStrict(new StringReader(launchJson))) {
				Object read = Gsons.read(reader);

				if (!(read instanceof Map)) {
					throw new IllegalStateException("Cannot create server installation due to server endpoint returning wrong type.");
				}

				@SuppressWarnings("unchecked")
				Map<String, Object> root = ((Map<String, Object>) read);
				String mainClass = (String) root.get("mainClass");

				if (mainClass == null) {
					throw new IllegalStateException("mainClass in server launch json was not present");
				}

				@SuppressWarnings("unchecked")
				List<Object> libraries = (List<Object>) root.get("libraries");

				if (libraries == null) {
					throw new IllegalStateException("No libraries were specified!");
				}

				Set<CompletableFuture<Path>> libraryFiles = new HashSet<>();

				for (Object library : libraries) {
					if (!(library instanceof Map)) {
						throw new IllegalStateException("All libraries must be json objects!");
					}

					@SuppressWarnings("unchecked")
					Map<String, String> libraryFields = ((Map<String, String>) library);

					String name = libraryFields.computeIfAbsent("name", k -> { throw new IllegalStateException("Library had no name!"); });
					String url = libraryFields.computeIfAbsent("url", k -> { throw new IllegalStateException("Library had no url!"); });

					libraryFiles.add(downloadLibrary(name, url));
				}

				return CompletableFuture.allOf(libraryFiles.toArray(new CompletableFuture[0])).thenAccept(_v -> {
					try {
						if (Files.notExists(installDir)) {
							Files.createDirectories(installDir);
						}

						createLaunchJar(installDir.resolve("quilt-server-launch.jar"), mainClass, libraryFiles);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					} catch (InterruptedException | ExecutionException e) {
						throw new RuntimeException(e);
					}
				});
			} catch (IOException e) {
				throw new UncheckedIOException(e); // exceptionally
			}
		}).thenCompose(_v -> {
			try {
				MinecraftInstallation.InstallationInfo installationInfo = installationInfoFuture.get();

				if (this.createScripts) {
					println("Creating launch scripts");
					// TODO: Make scripts
				}

				// Download Minecraft server and create scripts if specified
				if (this.installServer) {
					println("Downloading server");
					return downloadServer(installDir, minecraftVersion, installationInfo);
				}

				return CompletableFuture.completedFuture(null);
			} catch (InterruptedException | ExecutionException e) {
				throw new RuntimeException(e);
			}
		}).exceptionally(e -> {
			e.printStackTrace();
			return null;
		}).join();
	}

	public static CompletableFuture<Void> downloadServer(Path installDir, String minecraftVersion, MinecraftInstallation.InstallationInfo info) {
		return CompletableFuture.supplyAsync(() -> {
			// Get the info from the manifest
			VersionManifest.Version version = info.manifest().getVersion(minecraftVersion);
			// Not gonna be null cause we already validated this
			@SuppressWarnings("ConstantConditions")
			String rawUrl = version.url();

			try {
				URL url = new URL(rawUrl);
				URLConnection connection = url.openConnection();

				InputStreamReader stream = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8);

				try (BufferedReader reader = new BufferedReader(stream)) {
					StringBuilder builder = new StringBuilder();
					String line;

					while ((line = reader.readLine()) != null) {
						builder.append(line);
						builder.append('\n');
					}

					String content = builder.toString();

					try (JsonReader json = JsonReader.createStrict(new StringReader(content))) {
						Object read = Gsons.read(json);

						if (!(read instanceof Map)) {
							throw new IllegalStateException(String.format("launchermeta for %s is not an object!", minecraftVersion));
						}

						Object rawDownloads = ((Map<?, ?>) read).get("downloads");

						if (!(rawDownloads instanceof Map)) {
							throw new IllegalStateException("Downloads in launcher meta must be present and an object");
						}

						Object rawServer = ((Map<?, ?>) rawDownloads).get("server");

						if (!(rawServer instanceof Map)) {
							throw new IllegalStateException("Server downloads in launcher meta must be present and an object");
						}

						Object rawServerUrl = ((Map<?, ?>) rawServer).get("url");

						if (rawServerUrl == null) {
							throw new IllegalStateException("Server download url must be present");
						}

						println(String.format("Downloading %s server jar from %s", minecraftVersion, rawServerUrl.toString()));

						try (InputStream serverDownloadStream = new URL(rawServerUrl.toString()).openConnection().getInputStream()) {
							Files.copy(serverDownloadStream, installDir.resolve("server.jar"), StandardCopyOption.REPLACE_EXISTING);
						}
					}

					return null;
				}
			} catch (IOException e) {
				throw new UncheckedIOException(e); // Handled via .exceptionally(...)
			}
		});
	}

	private static CompletableFuture<Path> downloadLibrary(String name, String url) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				// Windows does not accept semicolons in filenames, so, we remove them
				String[] splitName = name.split(":");
				String shortName = splitName[1] + splitName[2];
				Path path = Files.createTempFile(shortName, null);

				// Convert to maven url
				String rawUrl = mavenToUrl(url, name);
				println("Downloading library at: " + rawUrl);

				URLConnection connection = new URL(rawUrl).openConnection();

				try (InputStream stream = connection.getInputStream()) {
					Files.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
				}

				return path;
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	// Combine all the jars into one file for the quilt-server-launch.jar
	private static void createLaunchJar(Path path, String mainClass, Set<CompletableFuture<Path>> libraries) throws IOException, ExecutionException, InterruptedException {
		if (Files.exists(path)) {
			Files.delete(path);
		}

		try (ZipOutputStream zipStream = new ZipOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
			Set<String> addedEntries = new HashSet<>();

			// Manifest
			addedEntries.add("META-INF/MANIFEST.MF");
			zipStream.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));

			Manifest manifest = new Manifest();
			manifest.getMainAttributes().put(new Attributes.Name("Manifest-Version"), "1.0");
			manifest.getMainAttributes().put(new Attributes.Name("Main-Class"), "org.quiltmc.loader.impl.launch.server.QuiltServerLauncher");
			manifest.write(zipStream);

			zipStream.closeEntry();

			// server launch properties
			// TODO: Generate quilt file, but also read old fabric files
			addedEntries.add("fabric-server-launch.properties");
			zipStream.putNextEntry(new ZipEntry("fabric-server-launch.properties"));
			zipStream.write(("launch.mainClass=" + mainClass + "\n").getBytes(StandardCharsets.UTF_8));
			zipStream.closeEntry();

			Map<String, Set<String>> services = new HashMap<>();
			byte[] buffer = new byte[32768];

			// Combine services and copy other files
			for (CompletableFuture<Path> library : libraries) {
				Path libraryJar = library.get();

				try (JarInputStream jarStream = new JarInputStream(Files.newInputStream(libraryJar))) {
					JarEntry entry;

					while ((entry = jarStream.getNextJarEntry()) != null) {
						if (entry.isDirectory()) {
							continue;
						}

						String name = entry.getName();

						if (name.startsWith(SERVICES_DIR) && name.indexOf('/', SERVICES_DIR.length()) < 0) { // service definition file
							parseServiceDefinition(name, jarStream, services);
						} else if (!addedEntries.add(name)) {
							System.out.printf("duplicate file: %s%n", name);
						} else {
							JarEntry newEntry = new JarEntry(name);
							zipStream.putNextEntry(newEntry);

							int r;
							while ((r = jarStream.read(buffer, 0, buffer.length)) >= 0) {
								zipStream.write(buffer, 0, r);
							}

							zipStream.closeEntry();
						}
					}
				}
			}

			// write service definitions
			for (Map.Entry<String, Set<String>> entry : services.entrySet()) {
				JarEntry newEntry = new JarEntry(entry.getKey());
				zipStream.putNextEntry(newEntry);

				writeServiceDefinition(entry.getValue(), zipStream);

				zipStream.closeEntry();
			}
		}
	}

	private static void parseServiceDefinition(String name, InputStream rawIs, Map<String, Set<String>> services) throws IOException {
		Collection<String> out = null;
		BufferedReader reader = new BufferedReader(new InputStreamReader(rawIs, StandardCharsets.UTF_8));
		String line;

		while ((line = reader.readLine()) != null) {
			// Drop comments
			int pos = line.indexOf('#');

			if (pos >= 0) {
				line = line.substring(0, pos);
			}

			line = line.trim();

			if (!line.isEmpty()) {
				if (out == null) {
					out = services.computeIfAbsent(name, ignore -> new LinkedHashSet<>());
				}

				out.add(line);
			}
		}
	}

	private static void writeServiceDefinition(Collection<String> entries, OutputStream stream) throws IOException {
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8));

		for (String def : entries) {
			writer.write(def);
			writer.write('\n');
		}

		writer.flush();
	}

	private static String mavenToUrl(String mavenUrl, String artifactNotation) {
		String[] parts = artifactNotation.split(":", 3);

		String path = parts[0].replace(".", "/") + // Group
				"/" + parts[1] +									// Artifact name
				"/" + parts[2] +									// Version
				"/" + parts[1] +
				"-" + parts[2] + ".jar";							// Artifact

		return mavenUrl + path;
	}

	public String minecraftVersion() {
		return this.minecraftVersion;
	}

	public MinecraftInstallation.InstallationInfo installationInfo() {
		return this.installationInfo;
	}

	public Path installedDir() {
		return this.installedDir;
	}

	public enum MessageType {
	}
}
