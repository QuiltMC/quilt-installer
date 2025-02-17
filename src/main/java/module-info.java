module org.quiltmc.installer {
    requires java.desktop;
    requires org.jetbrains.annotations;
    requires com.google.gson;

    // needed so we can use x25519 when connecting to quilt meta
    uses java.security.Provider;
}
