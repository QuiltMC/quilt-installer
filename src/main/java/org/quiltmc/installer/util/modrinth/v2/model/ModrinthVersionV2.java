package org.quiltmc.installer.util.modrinth.v2.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record ModrinthVersionV2(String name, @SerializedName("version_number") String versionNumber, String changelog, @SerializedName("game_versions") List<String> gameVersions, @SerializedName("version_type") String versionType) {

    public static class VersionTypes {
        public static final String ALPHA = "alpha";
        public static final String BETA = "beta";
        public static final String RELEASE = "release";
    }
}
