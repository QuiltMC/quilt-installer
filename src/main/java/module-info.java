module org.quiltmc.installer {
    requires java.desktop;
    requires org.jetbrains.annotations;
    requires jdk.crypto.ec; // glitch: not having this was potentially causing issues on java 8, keeping it just in case.
    requires com.google.gson;
}
