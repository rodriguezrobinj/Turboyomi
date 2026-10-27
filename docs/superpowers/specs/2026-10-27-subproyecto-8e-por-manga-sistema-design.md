# Subproyecto 8e: espaciado por manga y pantalla encendida

## 1. Objetivo
Último de 8a–8e: que un manga guarde su propio espaciado lateral en webtoon, y que la pantalla no se apague mientras
se lee.

### Fuera de alcance
- Otros ajustes por manga (el modo de lectura ya lo es desde Mihon).
- Copia del espaciado por manga en los backups `.tachibk`: va en las preferencias de la app, no en la DB.

## 2. Diseño
- **Espaciado por manga:** el esquema de la DB es el de Mihon (v20, compatible con los backups), así que no hay columna
  nueva: `ReaderPreferences.webtoonSidePaddingFor(mangaId)`, clave `pref_webtoon_side_padding_manga_<id>`, −1 mientras
  el manga sigue al global. El lector usa el del manga si existe; Ctrl+±/rueda y el slider de los ajustes rápidos
  cambian el del manga cuando lo tiene, y el global si no. En los ajustes rápidos, el chip "Solo para este manga"
  (`desktop_side_padding_this_manga`, nuevo con `es`) lo activa partiendo del valor en uso, o lo borra.
- **Pantalla encendida** (`keepScreenOn`, la preferencia de Mihon, apagada por defecto en escritorio): mientras el
  lector está abierto, `ScreenKeeper.keepOn(true)` llama a `SetThreadExecutionState(ES_CONTINUOUS |
  ES_DISPLAY_REQUIRED)` en el hilo de la UI (Windows ata la petición al hilo) y lo deja en `ES_CONTINUOUS` al salir o
  al apagarlo. Chip "Mantener la pantalla encendida" en "Visualización".

## 3. Pruebas
- `ScreenKeeperTest`: pedir la pantalla deja `ES_DISPLAY_REQUIRED` en el estado; soltarla lo quita.
- `WebtoonAndDualTest`: con espaciado propio 20 y global 5, Ctrl++ deja el del manga en 15 y el global en 5.
- Verificación visual de 8d y 8e juntas.
