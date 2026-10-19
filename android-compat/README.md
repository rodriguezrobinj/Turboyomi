# android-compat

Vendored from Suwayomi-Server `AndroidCompat/` and `AndroidCompat/Config/` at commit
`e2ce828abb81f0f5ce1a3fb86194a2746b519c85`. Licensed under MPL-2.0 (see `LICENSE`).

Modifications to these files must stay under MPL-2.0 and be listed below.

## Local modifications

- `build.gradle.kts` files rewritten for this repository's version catalog.
- `config/src/main/resources/server-reference.conf`: added. Suwayomi ships it from its server module; Config reads
  `server.debugLogsEnabled` from it at startup.
