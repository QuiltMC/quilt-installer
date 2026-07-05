package org.quiltmc.installer.util.modrinth.v2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.jspecify.annotations.Nullable;
import org.quiltmc.installer.Connections;
import org.quiltmc.installer.util.modrinth.v2.model.ModrinthVersionV2;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ModrinthApiV2 {

    private static final String BASE_API_URL = "https://api.modrinth.com/v2";
    private static final Gson GSON = new GsonBuilder().create();

    public static CompletableFuture<List<ModrinthVersionV2>> getProjectVersions(String projectId, @Nullable List<String> gameVersionFilter) {
        return CompletableFuture.supplyAsync(() -> {
            var responseType = new TypeToken<List<ModrinthVersionV2>>(){};
            var url = new StringBuilder(BASE_API_URL).append("/project/%s/version".formatted(projectId));
            url.append("?include_changelog=false&featured=false");

            if(gameVersionFilter != null) {
                var array = "[%s]".formatted(gameVersionFilter.stream().map("\"%s\""::formatted).collect(Collectors.joining(",")));
                url.append("&game_versions=").append(URLEncoder.encode(array, StandardCharsets.UTF_8));
            }

            try (var reader = Connections.openReader(URI.create(url.toString()))) {
                return GSON.fromJson(reader, responseType);
            } catch (IOException e) {
                throw new RuntimeException("Unable to connect to Modrinth API: " + url, e);
            }
        });
    }
}
