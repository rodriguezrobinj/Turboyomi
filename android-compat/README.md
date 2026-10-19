# android-compat

Vendored from Suwayomi-Server `AndroidCompat/` and `AndroidCompat/Config/` at commit
`e2ce828abb81f0f5ce1a3fb86194a2746b519c85`. Licensed under MPL-2.0 (see `LICENSE`).

Modifications to these files must stay under MPL-2.0 and be listed below.

## Local modifications

- `build.gradle.kts` files rewritten for this repository's version catalog.
- `config/src/main/resources/server-reference.conf`: added. Suwayomi ships it from its server module; Config reads
  `server.debugLogsEnabled` from it at startup.
- `extension-loader/.../vendor/ChildFirstURLClassLoader.kt` and `BytecodeEditor.kt`: copied from
  `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/util/`, package renamed to `mihon.desktop.extension.vendor`.
  `ChildFirstURLClassLoader.kt` had no license header upstream; the project's MPL-2.0 header was added.
- `extension-loader/.../vendor/Dex2Jar.kt`: derived from `PackageTools.dex2jar`; errors throw instead of being logged.
- `extension-loader/.../vendor/BytecodeEditor.kt`: reformatted by the repository's ktlint (no behavior change).
- `extension-loader/.../vendor/Dex2Jar.kt`: wraps the dex reader with `DexConstructorFixer` (Apache-2.0, not vendored).
