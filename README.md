# Turboyomi

Lector de manga para Windows, basado en [Mihon](https://github.com/mihonapp/mihon).
*A manga reader for Windows, based on Mihon.*

Turboyomi es un proyecto independiente: no está afiliado a Mihon ni lo mantiene su equipo. Los problemas de
Turboyomi se reportan aquí, no en Mihon.

## Descarga

En [Releases](https://github.com/rodriguezrobinj/turboyomi/releases/latest):

- `Turboyomi-<versión>.msi`: el instalador. Se instala solo para tu usuario, sin permisos de administrador, y se
  actualiza desde la propia app (Ajustes › Acerca de).
- `Turboyomi-<versión>.zip`: la misma app sin instalador.
- `SHA256SUMS`: los hashes de los dos.

Requiere Windows 10 o posterior (x64). Lleva su propio Java.

El instalador aún no está firmado, así que Windows SmartScreen avisa de un "editor desconocido". Para continuar,
pulsa "Más información" y luego "Ejecutar de todas formas".

## Qué hace

- Biblioteca con categorías y actualización periódica de capítulos.
- Lector de páginas y de tira vertical.
- Extensiones de Mihon de los repositorios que elijas.
- Descargas y lectura de archivos locales.
- Copias de seguridad compatibles con Mihon (`.tachibk`).
- Seguimiento con trackers (AniList, MyAnimeList, Kitsu y otros).

## Compilar

Con JDK 23:

```
gradlew :desktop-app:run          # la app
gradlew test                      # las pruebas
gradlew :desktop-app:packageZip   # la app con su Java, en desktop-app/build/packaging
gradlew :desktop-app:packageMsi   # el instalador; necesita WiX 3.14
```

## Licencia

Apache 2.0, como Mihon. Ver [LICENSE](LICENSE) y [NOTICE](NOTICE).
