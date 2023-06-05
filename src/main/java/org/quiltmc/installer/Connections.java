/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.installer;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

public class Connections {
    public static final String INSTALLER_VERSION = getInstallerVersion();

    private static String getInstallerVersion() {
        String version = QuiltMeta.class.getPackage().getImplementationVersion();
        if (version != null) {
            return version;
        }

        return "dev";
    }

    public static URLConnection openConnection(URL url) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Quilt-Installer/"+INSTALLER_VERSION);

        return connection;
    }
}
