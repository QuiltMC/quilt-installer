# 0.9.2
- Fix download URLs for Java 21 runtime

# 0.10.0
- Dynamically set minimum Java version

# 0.10.1
- Fix native installer publishing

# 0.10.2
- Fix native installer working directory

# 0.11.0
- Do not replace hashed artifacts with fabric-intermediary
- Fetch intermediary versions from Quilt Meta instead of Fabric Meta

# 0.11.1
- Add support for future intermediary v2 URLs
- Exclude JetBrains annotations from final jar

# 0.11.2
- Change relocation prefix for included libraries
- Remove dependency on quilt-parsers

# 0.11.3
- Update Java module info
- Exclude google annotations from final jar

# 0.12.0
- Add BouncyCastle security provider for runtimes that do not have proper TLS support
- Add detection for Java runtimes that do not provide Swing classes (fix [#54])
- Add better error message if running on a too minimal Java runtime

# 0.12.1
- Fix GUI immediately closing

# 0.13.0
- Update dependencies
- Add support for unobfuscated Minecraft versions

# 0.13.1
- Fix unobfuscated versions not showing in installer GUI
