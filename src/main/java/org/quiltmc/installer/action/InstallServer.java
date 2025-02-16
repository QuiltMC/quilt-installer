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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.installer.Connections;
import org.quiltmc.installer.LaunchJson;
import org.quiltmc.installer.util.Util;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

            String mainClass = Optional.ofNullable(launchJson.getAsJsonPrimitive("launcherMainClass")).map(JsonPrimitive::getAsString).orElseThrow(() -> new IllegalStateException("launcherMainClass in server launch json was not present"));

            JsonArray libraries = Optional.ofNullable(launchJson.getAsJsonArray("libraries")).orElseThrow(() -> new IllegalStateException("No libraries were specified!"));

            Set<CompletableFuture<Path>> libraryFiles = new HashSet<>();

            for (JsonElement library : libraries) {
                JsonObject libraryFields = library.getAsJsonObject();

                String name = Optional.ofNullable(libraryFields.getAsJsonPrimitive("name")).map(JsonPrimitive::getAsString).orElseThrow(() -> new IllegalStateException("Library had no name!"));
                String url = Optional.ofNullable(libraryFields.getAsJsonPrimitive("url")).map(JsonPrimitive::getAsString).orElseThrow(() -> new IllegalStateException("Library had no url!"));

                libraryFiles.add(downloadLibrary(installDir.resolve("libraries"), name, url));
            }

            return CompletableFuture.allOf(libraryFiles.toArray(CompletableFuture[]::new)).thenAccept(_v -> {
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
        return CompletableFuture.runAsync(() -> {
            // Get the info from the manifest
            var version = Objects.requireNonNull(info.manifest().getVersion(minecraftVersion), "No manifest version for " + minecraftVersion);
            try (var reader = new BufferedReader(new InputStreamReader(Connections.openConnection(version.url()), StandardCharsets.UTF_8))) {

                JsonObject read = Optional.ofNullable(Util.GSON.fromJson(reader, JsonObject.class)).orElseThrow(() -> new IllegalStateException(String.format("launchermeta for %s is not an object!", minecraftVersion)));
                JsonObject downloads = read.getAsJsonObject("downloads");
                JsonObject server = downloads.getAsJsonObject("server");

                String url = Optional.ofNullable(server.getAsJsonPrimitive("url")).map(JsonPrimitive::getAsString).orElseThrow(() -> new IllegalStateException("Server download url must be present"));

                println(String.format("Downloading %s server jar from %s", minecraftVersion, url));

                try (InputStream serverDownloadStream = Connections.openConnection(URI.create(url))) {
                    Files.copy(serverDownloadStream, installDir.resolve("server.jar"), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e); // Handled via .exceptionally(...)
            }
        });
    }

    private static CompletableFuture<Path> downloadLibrary(Path librariesDir, String name, String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path path = librariesDir.resolve(splitArtifact(name));
                // Convert to maven url
                URI rawUrl = URI.create(mavenToUrl(url, name));
                println("Downloading library at: " + rawUrl);

                try (InputStream stream = Connections.openConnection(rawUrl)) {
                    Files.createDirectories(path.getParent());
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
            zipStream.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
            manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, libraries.stream().map(CompletableFuture::join).map(p -> path.getParent().relativize(p).toString().replace("\\", "/")).collect(Collectors.joining(" ")));
            manifest.write(zipStream);
            zipStream.closeEntry();
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
        return mavenUrl + splitArtifact(artifactNotation);
    }

    private static String splitArtifact(String artifactNotation) {
        String[] parts = artifactNotation.split(":", 3);
        String path = parts[0].replace(".", "/") + // Group
                      "/" + parts[1] +                                    // Artifact name
                      "/" + parts[2] +                                    // Version
                      "/" + parts[1] + "-" + parts[2] + ".jar";                            // Artifact
        return path;
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
