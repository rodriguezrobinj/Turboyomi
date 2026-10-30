# Subproyecto 11c: enlaces externos

## 1. Objetivo
Lo que en Mihon llega por intents, en escritorio llega como argumentos del `.exe`: enlaces de repos de extensiones,
archivos de backup y enlaces o búsquedas de manga. Tercero de 11a–11f (ver spec 11a §1).

### Fuera de alcance
- Compartir desde otras apps (Windows no tiene un `ACTION_SEND` para apps Win32 sin empaquetar).
- Accesos directos de la barra de tareas (los `SHORTCUT_*` de Mihon).

## 2. Diseño

### Registro (HKCU, sin admin)
`ProtocolRegistrar` ya registra `turboyomi://` y `app.turboyomi://` en cada arranque. Ahora registra también:
- `tachiyomi://` y `mihon://`: los botones "Añadir a Mihon/Tachiyomi" de las webs de repos.
- `.tachibk`: ProgID `Turboyomi.Backup` con el mismo comando. "Abrir con" y el doble clic abren la restauración.

Usa la misma línea de comando (`"%1"`), así que la instancia única ya reenvía el argumento a la ventana abierta.

### Enrutado (`mihon.desktop.app.main.DeepLinks`)
Una función pura `parse(arg): DeepLink?`, que se prueba sin UI:
- `tachiyomi://add-repo?url=X` o `mihon://extension-store?url=X` → `AddRepo(X)`. Es el `isAddExtensionStoreIntent` de
  Mihon.
- Ruta que termina en `.tachibk` → `RestoreBackup(ruta)` si el archivo existe; si no, nada.
- `http(s)://…` u otro texto que no empiece por `-` → `Search(texto)`. Es el `ACTION_SEARCH` / `ACTION_VIEW` de Mihon.
- Los esquemas de OAuth (`turboyomi://`, `app.turboyomi://`) y las opciones (`--smoke`…) → `null`. Ya los trata
  `LoginUrls` o el arranque.

`Main` pasa los argumentos del primer arranque y los reenviados a un `Channel` de `DeepLinks`. `AppContent` lo recoge
cuando la UI está lista, igual que `AppEvents`. Con la app bloqueada (11b), la pantalla se abre debajo de la de desbloqueo y se ve al
desbloquear.

Destino de cada uno, como en `MainActivity.handleIntentAction`:
- `AddRepo` → `ExtensionStoresScreen(url)`. Ese gemelo ya trae `addFromDeeplink`, que pide confirmación.
- `RestoreBackup` → `RestoreBackupScreen(ruta)`.
- `Search` → `DeepLinkScreen(query)`: gemelo de Mihon con `DeepLinkViewModel`. Busca una `ResolvableSource` que
  reconozca el enlace. Si encuentra un manga, abre `MangaScreen`; si es un capítulo, el lector. Si no, abre
  `GlobalSearchScreen(query)`.

Antes de navegar se vuelve a la raíz (`popUntilRoot`), como Mihon.

## 3. Pruebas
- `DeepLinksTest`: cada forma de `parse`, incluidos un `add-repo` sin `url`, un `.tachibk` que no existe y una opción
  `--x`.
- `ProtocolRegistrarTest` (existe): escribe las claves nuevas en una raíz de prueba de HKCU.
- A mano, con el ejecutable empaquetado y un perfil aislado: un enlace `tachiyomi://add-repo` desde el navegador,
  doble clic en un `.tachibk` y una búsqueda pasada como argumento con la app ya abierta.
