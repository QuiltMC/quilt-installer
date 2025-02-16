package org.quiltmc.installer.util.mojang;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public record MinecraftMeta(List<MinecraftVersion> versions, Latest latest) implements Iterable<MinecraftMeta.MinecraftVersion> {

    private static final URI VERSION_MANIFEST_V2_URL = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    public static MinecraftMeta get(Gson gson) {
        try (var reader = new InputStreamReader(new BufferedInputStream(VERSION_MANIFEST_V2_URL.toURL().openStream()))) {
            return gson.fromJson(reader, MinecraftMeta.class);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Game Meta URL was invalid", e);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read game version manifest from Piston Meta", e);
        }
    }

    @Nullable
    public MinecraftVersion getVersion(String id) {
        return this.versions.stream().filter(version -> version.id().equals(id)).findFirst().orElse(null);
    }

    public MinecraftVersion latestRelease() {
        return Objects.requireNonNull(getVersion(latest().release()));
    }

    public MinecraftVersion latestSnapshot() {
        return Objects.requireNonNull(getVersion(latest().snapshot()));
    }

    @Override
    public @NotNull Iterator<MinecraftVersion> iterator() {
        return this.versions.iterator();
    }

    public record MinecraftVersion(String id, String type, URI url, Instant time, Instant releaseTime) {

        public static final String TYPE_RELEASE = "release";
        public static final String TYPE_SNAPSHOT = "snapshot";
        public static final String TYPE_OLD_BETA = "old_beta";
        public static final String TYPE_OLD_ALPHA = "old_alpha";

        public boolean isSnapshot() {
            return TYPE_SNAPSHOT.equals(type);
        }

        public boolean isRelease() {
            return TYPE_RELEASE.equals(type);
        }
    }

    public record Latest(String release, String snapshot) {}
}
