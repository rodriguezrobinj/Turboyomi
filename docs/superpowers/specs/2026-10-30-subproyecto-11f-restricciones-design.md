# Subproyecto 11f: restricciones de dispositivo para la actualización automática

## 1. Objetivo
Ajustes › Biblioteca › "Restricciones de dispositivo para actualización automática", como en Mihon: solo con Wi-Fi,
solo en red sin límite de datos, solo cargando. Sexto y último de 11a–11f (ver spec 11a §1).

## 2. Diseño
- **Preferencia:** `libraryPreferences.autoUpdateDeviceRestrictions`, que ya está en `domain` con los valores de Mihon
  (`wifi`, `network_not_metered`, `ac`). Lista múltiple en `SettingsLibraryScreen`, visible si la actualización
  automática está activa. Mismo sitio que en Mihon.
- **"Wi-Fi" en escritorio:** Mihon trae `wifi` marcado de serie. Si se leyera al pie de la letra, un PC con cable no
  actualizaría nunca. Por eso en escritorio significa "sin datos móviles": vale el Wi-Fi o el cable, y se rechaza la
  banda ancha móvil (WWAN). El texto lo dice: `desktop_connected_to_wifi_or_cable` ("Solo con Wi-Fi o cable (sin datos
  móviles)"). En los teléfonos el propósito era el mismo: no gastar datos móviles.
- **Estado del equipo** (`desktop-core`, `mihon.desktop.core.DeviceState`):
  - Carga: `GetSystemPowerStatus` (JNA). `ACLineStatus == 1` es "enchufado". Un PC de sobremesa sin batería da 1.
  - Red: WinRT `NetworkInformation.GetInternetConnectionProfile()`, con la ayuda de WinRT de 11b.
    `IConnectionProfile2.IsWwanConnectionProfile` da los datos móviles. `GetConnectionCost().NetworkCostType` en `Fixed` o
    `Variable` es una conexión de uso medido; `Unknown` cuenta como sin límite. Sin perfil de Internet no se cumple
    ninguna de las dos, y la tarea espera a que haya conexión.
  - Las llamadas WinRT de 11b pasan a `mihon.desktop.core.winrt.WinRt`, compartido por `WindowsHello` y `DeviceState`.
  - Si una consulta falla, la restricción cuenta como cumplida y se registra en el log: un fallo no debe parar las
    actualizaciones para siempre.
- **Aplicar:** `PeriodicJob` gana `canRun: () -> Boolean` (por defecto `true`). `JobScheduler` no lanza una tarea
  vencida mientras `canRun` sea falso, ni toca su `lastRun`. Así se lanza en cuanto se cumple, como las restricciones
  de WorkManager. Solo la actualización automática de la biblioteca usa `canRun`. La actualización manual y la de la
  bandeja no miran las restricciones, igual que en Mihon.

## 3. Pruebas
- `JobSchedulerTest`: una tarea vencida con `canRun = false` no se lanza ni guarda `lastRun`, y se lanza cuando pasa a
  `true`.
- `UpdateRestrictionsTest`: cada restricción contra un estado de equipo falso, incluido un fallo de WinRT.
- `DeviceStateTest`: las llamadas reales a WinRT y `kernel32` responden sin fallar.
- A mano: Wi-Fi y uso medido en Windows (Configuración › Red › propiedades › conexión de uso medido).
