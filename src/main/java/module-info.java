module org.quiltmc.installer {
    requires java.desktop;
    requires org.jetbrains.annotations;
    requires org.quiltmc.parsers.json;
    // Quilt Meta is too fancy!
    // this is needed so that the executable can use x25519 when connecting to quilt maven
    requires jdk.crypto.ec;
}