# Minecraft 1.21.10 backport

Modern ChainVeinFabric 4.1.0 behavior adapted from the existing 1.21.1 branch.
Uses Java 21, Mojang mappings, Fabric Loom remapping and matching MaLiLib.
Existing upstream Minecraft branches are left unchanged.

Validation commands:

- `./gradlew test`
- `./gradlew runGameTest -PquickshulkerJar=<absolute v4 jar> -PquickshulkerGameTestMode=new`
- `./gradlew runGameTest -PquickshulkerJar=<absolute v4 jar> -PquickshulkerGameTestMode=new-legacy`

Run unit tests without Quick Shulker on the optional runtime classpath; integration
GameTests intentionally install it. The real item-frame tests use the server
hurt API and retain removal/drop assertions. Batched overflow checks retain the
unstackable-item limit and original drop positions.

Local direct integration passed 24 required GameTests (including Fabric's test)
after the matching Quick Shulker component-capacity fix. Forced legacy and
production-client results are reported with the final validation record. GitHub
Actions checks are separate from those local results.
