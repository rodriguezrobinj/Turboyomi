# Subproyecto 11b: seguridad y privacidad

## 1. Objetivo
Ajustes › Seguridad y privacidad, como en Mihon: exigir desbloqueo, bloquear por inactividad, ocultar el contenido de
las notificaciones y pantalla segura. Segundo de 11a–11f (ver spec 11a §1).

### Fuera de alcance
- Grupo de Firebase (Turboyomi no lleva telemetría).
- Cifrar los datos: el bloqueo protege la ventana, no los archivos. En Mihon pasa lo mismo.
- La ventana del navegador integrado: no se bloquea ni se oculta de las capturas.

## 2. Diseño

### Windows Hello (`desktop-core`, `mihon.desktop.core.security.WindowsHello`)
WinRT por JNA, sin dependencias nuevas:
- `availability()`: `UserConsentVerifier.CheckAvailabilityAsync` (`IUserConsentVerifierStatics`).
- `verify(hwnd, message)`: `IUserConsentVerifierInterop.RequestVerificationForWindowAsync`. Con la ventana como dueña,
  el diálogo de Windows Hello (PIN, huella o cara) sale delante de la app.
- Se espera a la operación asíncrona consultando `IAsyncInfo.Status` cada 50 ms en un hilo de IO con `RoInitialize`
  (MTA). Luego se lee `GetResults`.
- Los IID son constantes copiadas del Windows SDK 10.0.26100: `IUserConsentVerifierInterop`
  (`UserConsentVerifierInterop.idl`, disponible desde Windows 10 1709), `IUserConsentVerifierStatics`, `IAsyncInfo` e
  `IAsyncOperation<UserConsentVerificationResult>` (`fd596ffd-2318-558f-9dbe-d21df43764a5`, en
  `windows.security.credentials.ui.h`).
- Cualquier fallo de COM se registra en el log y cuenta como "no disponible" o "no verificado". Nunca desbloquea.

### Bloqueo (`mihon.desktop.app.security.AppLock`)
Es la lógica de `SecureActivityDelegate` de Mihon, sin Android:
- `locked: StateFlow<Boolean>`. Al arrancar está bloqueada si `useAuthenticator` está activo y Hello está disponible.
- "Segundo plano" en escritorio significa ventana minimizada u oculta en la bandeja. Al pasar a segundo plano se guarda
  `lastAppClosed` si `lockAppAfter > 0`. Al volver se bloquea según `lockAppAfter`: -1 nunca, 0 siempre, N si pasaron N
  minutos. Igual que Mihon.
- `unlock(hwnd)` llama a `verify`. Si el resultado es `Verified`, desbloquea.
- Las entradas son funciones (reloj, verificador), así que la lógica se prueba sin Windows.

Mientras está bloqueada, `Main` pone `UnlockScreen` encima de `AppContent`: el icono y el botón "Desbloquear
Turboyomi" (`unlock_app_title`). Pide Hello al mostrarse, y otra vez con el botón o con Intro.
- `AppContent` sigue compuesto debajo, así que al desbloquear vuelve la misma pantalla, como la actividad de Mihon.
- La capa es una `Surface` opaca que no deja pasar clics. Toma el foco y se queda con todas las teclas. Los atajos de la
  ventana no se procesan mientras está bloqueada.
- Las tareas en segundo plano siguen corriendo.
- La ventana dueña del diálogo y la disponibilidad de Hello (calculada una vez y guardada) están en `AppSecurity`.

Activar "Exigir desbloqueo" pide Hello antes de guardar, como en Mihon. Si no se verifica, la opción no se activa. El
interruptor se ve solo si Hello está disponible.

### Pantalla segura
`SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE)` cuando el modo es "Siempre", o "Incógnito" con el incógnito
global activo; `WDA_NONE` en otro caso. Así las capturas de pantalla, la grabación y la vista previa al compartir
pantalla no muestran la ventana. Si falla (Windows anterior a 2004), se reintenta con `WDA_MONITOR`, que la pinta
negra.

### Ocultar el contenido de las notificaciones
Con la opción activa:
- `newChaptersNotice` muestra solo el número de capítulos, sin títulos.
- El error de descarga de `DownloadNotifier` no lleva el título ni el capítulo.
- Los avisos de backup quedan igual: Mihon tampoco oculta la ruta del backup creado, y el progreso de la restauración
  se ve dentro de la app, no en una notificación.

### Pantalla de ajustes
Gemelo de `SettingsSecurityScreen` sin el grupo de Firebase. `FragmentActivity.authenticate` se cambia por
`AppLock.confirm`. Se enlaza en `SettingsTab` entre Datos y Avanzado, como en Mihon.

## 3. Pruebas
- `AppLockTest`: bloquea al arrancar; -1, 0 y N minutos al volver de segundo plano; un verificador que falla no
  desbloquea.
- `TrayNotifierTest` (existe): con la opción activa no salen títulos.
- `SecureScreenTest`: decide el modo (siempre / incógnito / nunca × incógnito activo o no).
- A mano: el diálogo de Hello con el `.exe` y un perfil aislado.
