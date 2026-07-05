/*
 * Copyright 2026 QuiltMC
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
