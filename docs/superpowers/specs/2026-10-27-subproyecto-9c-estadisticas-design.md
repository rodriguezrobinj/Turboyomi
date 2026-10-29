# Subproyecto 9c: estadísticas

## 1. Objetivo
Último de 9a–9c. La pantalla de estadísticas de Mihon: cuánto hay en la biblioteca, cuánto se ha leído y cómo se
puntúa en los trackers.

### Fuera de alcance
- Gráficas o estadísticas por periodo: Mihon no las tiene.

## 2. Diseño
- Gemelos de Mihon, sin cambios de lógica: `StatsViewModel`, `StatsScreen`, `StatsScreenContent`, `StatsScreenState`,
  `StatsItem` y `StatsData`. Secciones: resumen (títulos en biblioteca, completados, tiempo de lectura), títulos (en
  la actualización global, empezados, locales), capítulos (totales, leídos, descargados) y trackers (títulos con
  seguimiento, puntuación media sobre 10, trackers con sesión).
- Entrada: el escritorio no tiene la pestaña "Más" de Mihon; su papel lo hace la lista de Ajustes. "Estadísticas"
  (`label_stats`) va como primera entrada de esa lista, con el icono de Mihon (`QueryStats` si existe en
  `material-symbols`, si no `Info`).
- Textos: los de Mihon, todos con `es`.

## 3. Pruebas
- `StatsTest` (Compose): con dos mangas en la biblioteca, uno con un capítulo leído, la pantalla muestra 2 títulos y
  1 capítulo leído.
