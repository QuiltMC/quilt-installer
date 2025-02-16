/*
 * Copyright 2025 QuiltMC
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

package org.quiltmc.installer.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.quiltmc.installer.util.json.MojangInstantTypeAdapter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class Util {

    // @formatter:off
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, new MojangInstantTypeAdapter())
            .create();
    // @formatter:on

    public static final DateTimeFormatter MOJANG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx").withZone(ZoneOffset.UTC);

    public static boolean isValidLoaderVersion(String version) {
        // TODO HACK HACK HACK
        // This is a hack to filter out old versions of Loader which we know will not support finding the main class.
        return !(version.startsWith("0.16.0-beta.") && version.length() == 13 && version.charAt(12) != '9');
    }
}
