# Minecraft 1.21.1 server test resources

`build.gradle` selects this resource directory for the `gametest` source set. The original `src/gametest/resources` still describes the newer Fabric client GameTest API and Java 25 mixins; those resources cannot be loaded by Minecraft 1.21.1.

- `fabric.mod.json` registers the two server test entry points.
- `chainveinfabric-gametest.mixins.json` uses Java 21 and includes the required test-only switch for exercising Quick Shulker's legacy adapter.
- `data/chainveinfabric-gametest/structure/empty.nbt` is an empty 12 × 6 × 12 vanilla structure (DataVersion 3955), used by the native 1.21.1 `@GameTest` annotations.

These resources are excluded from the published mod JAR. See [the backport notes](../../BACKPORT_1.21.1.md) for test commands and optional integration dependencies.
