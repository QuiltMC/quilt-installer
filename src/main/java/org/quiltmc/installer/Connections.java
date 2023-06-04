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
