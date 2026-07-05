module org.quiltmc.installer {
    requires java.desktop;
    requires org.jetbrains.annotations;
    requires org.jspecify;
    requires com.google.gson;

    requires org.bouncycastle.provider;
    requires java.logging; // required by bouncycastle

    // needed so we can use x25519 when connecting to quilt meta
    uses java.security.Provider;
}
