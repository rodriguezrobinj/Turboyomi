# Subproyecto 11e: primer arranque, "Apóyanos" y licencias

## 1. Objetivo
Las tres pantallas de presentación de Mihon que faltan. Quinto de 11a–11f (ver spec 11a §1).

### Fuera de alcance
- El paso de permisos del onboarding (notificaciones, batería, instalar APK: todo es de Android).

## 2. Diseño

### Primer arranque
Gemelos de `presentation/more/onboarding/` (`OnboardingScreen`, `OnboardingStep`, `ThemeStep`, `StorageStep`,
`GuidesStep`) y de `ui/more/OnboardingScreen`. Escritorio quita `PermissionStep`.
- `ThemeStep`: sin `setAppCompatDelegateThemeMode`; el tema de escritorio ya sigue a la preferencia.
- `StorageStep`: usa el selector de carpeta de escritorio de `SettingsDataScreen`. El paso cuenta como completo desde
  el principio, porque escritorio ya tiene una carpeta por defecto (`AppDirs`) y Mihon no.
- `GuidesStep`: igual. "Restaurar copia" abre Ajustes › Datos con la fila de restaurar resaltada, como en Mihon.
- `app_name` es "Mihon" y no se traduce, así que los pasos usan `AppDirs.APP_NAME` en su lugar.
- `AppContent` lo empuja sobre la pestaña de inicio si `basePreferences.shownOnboardingFlow` es falso, y Atrás no lo
  cierra hasta acabar. Igual que `MainActivity`.
- Quien ya usa la app no lo ve: al arrancar, si la base de datos ya tiene manga o categorías y la preferencia nunca se
  guardó, se marca como vista. Las pruebas existentes arrancan con `shownOnboardingFlow = true` en `startTestApp`.

### "Apóyanos"
Gemelo de `mihon.feature.support.SupportUsScreen`, con los enlaces de Mihon (Patreon, Open Collective, Discord) y una
línea más arriba: Turboyomi es un fork de Mihon y estas donaciones van al equipo de Mihon (`desktop_support_mihon_note`,
en `base` y `es`). Se abre desde Ajustes › Acerca de.

### Licencias
- Plugin JVM de AboutLibraries (`com.mikepenz.aboutlibraries.plugin`) en `desktop-app`. El jar es el mismo que el del
  plugin de Android, que la raíz ya pone en el classpath. En la JVM el plugin solo exporta, así que `exportLibraryDefinitions`
  escribe `build/generated/aboutlibraries/aboutlibraries.json`, esa carpeta se añade a los recursos y
  `processResources` depende de la tarea.
- Gemelo de `OpenSourceLicensesScreen` con `aboutlibraries-compose-m3`, que ya está en el catálogo y es multiplataforma.
  El JSON se lee de los recursos.
- La fila "Licencias" de Acerca de abre esta pantalla. `NOTICE` queda accesible desde la misma pantalla de Acerca de,
  en una fila aparte, porque cubre código que no es una dependencia (Suwayomi, muestras RAR).
- `scripts/check-licenses.sh` sigue igual.

## 3. Pruebas
- `OnboardingTest`: con la preferencia en falso, la app abre el onboarding. Tres "Siguiente" y "Terminar" llevan a la
  biblioteca y guardan la preferencia.
- `OnboardingTest` (segundo caso): una base de datos con manga marca el onboarding como visto.
- `SupportUsScreenTest`: aparece la nota del fork.
- `OpenSourceLicensesTest`: el JSON de los recursos incluye OkHttp.
