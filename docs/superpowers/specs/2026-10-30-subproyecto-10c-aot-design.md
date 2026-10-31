# Subproyecto 10c: arranque con la caché AOT de JDK 25

## 1. Objetivo
Que la ventana salga antes. Tercero de 10a–10d. En JDK 23 quedó bloqueado (rama `perf/startup-cds-10c`, sin subir):
el archivo CDS dinámico no era reubicable, y desde la carpeta instalada la JVM lo ignoraba sin avisar. JDK 25 trae la
caché AOT (JEP 483, 514 y 515), que también guarda los perfiles de ejecución, y acepta jar en otra carpeta mientras
mantengan la misma estructura relativa.

Evidencia (2026-10-30, equipo de 4 hilos con OneDrive y otros procesos activos, imágenes alternadas, perfil temporal
en caliente):
- Runtime de JDK 25 sin caché: ventana a 30–34 s.
- Con la caché, en la misma carpeta: 12,5–18 s, ~20 MB menos.
- Imagen copiada a otra carpeta (con espacio en la ruta) y la original renombrada: la caché se abre y 11 428 clases
  salen de ella, solo 5 de los jar (`-Xlog:class+load`). Las medidas de esa copia quedaron dentro del ruido del equipo.
- Entrenar es lento: la primera pantalla tarda ~9 minutos con el registro activo, y montar la caché (90 MB) otros ~40 s.
- Imagen de `appImage -Paot=true` copiada fuera de `build/` (con menos carga en el equipo): ventana a 6,2–7,1 s, frente
  a 13,9–14,2 s de la misma imagen sin la línea del `.cfg`.
- jpackage de JDK 25 deja `Turboyomi.exe` en solo lectura. El `deleteRecursively` de Java no lo borra, `image.old` se
  quedaba y el siguiente `appImage` fallaba con «is in use». `appImage` quita ese atributo antes de borrar.

## 2. Diseño
- **Runtime:** `jdkHome` (jlink y jpackage) pasa de 23 a 25. La compilación sigue con el JDK de Gradle (23).
- **`--enable-native-access=ALL-UNNAMED`** en el lanzador y en `run`: JDK 25 avisa de las llamadas de JNA y una
  versión futura las bloqueará.
- **`--aot-train`** (Main): tras el primer frame de la ventana, espera 10 s y sale por la salida normal. Al salir, la
  JVM monta la caché.
- **`appImage -Paot=true`:** arranca la imagen nueva con `--aot-train`, `TURBOYOMI_HOME` en una carpeta temporal y
  `JAVA_TOOL_OPTIONS=-XX:AOTCacheOutput=app/turboyomi.aot`. Después añade
  `java-options=-XX:AOTCache=$APPDIR\turboyomi.aot` al `Turboyomi.cfg`. Falla si el arranque falla o pasa de 30 min.
  Sin la propiedad no entrena: un `appImage` local sigue igual de rápido.
- **CI:** `build.yml` y `release.yml` empaquetan con `-Paot=true`. `setup-java` instala 25 y 23 (23 queda en
  `JAVA_HOME` para Gradle), y `gradle.properties` apunta los toolchains a `JAVA_HOME_25_X64`.
- **`packageMsi`** empaqueta esa imagen (`--app-image`) en vez de crear otra desde `input/`, así el MSI lleva la caché.
- Si los jar cambian, la JVM ignora la caché (`AOTMode=auto`) y arranca como antes. Nada escribe en la carpeta
  instalada.

### Fuera de alcance
- Bajar el tiempo del entrenamiento: solo cuesta en la CI.

## 3. Pruebas
- `appImage -Paot=true`: `app/turboyomi.aot` y la línea del `.cfg` existen, y la imagen arranca más rápido que sin
  ellos.
- La imagen copiada a otra carpeta usa la caché (`-Xlog:class+load`: clases de la app con `source: shared objects
  file`).
- `packageMsi` en la CI: el MSI instala `app\turboyomi.aot`.
