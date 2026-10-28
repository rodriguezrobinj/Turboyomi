# Subproyecto 9b: buscar en ajustes

## 1. Objetivo
Segundo de 9a–9c. Encontrar un ajuste escribiendo parte de su nombre, como en Mihon, en lugar de recorrer cada
categoría. Con 9a la pantalla Avanzado creció y es donde más se nota.

### Fuera de alcance
- Atajo de teclado propio: Ctrl+F ya es la búsqueda global de la app (spec 7).
- Buscar dentro de pantallas secundarias (información de depuración, crear backup, categorías).

## 2. Diseño
- `SettingsSearchScreen`, gemelo de la de Mihon: campo de texto en la barra con el foco al abrir, hasta 10 resultados
  con título y ruta ("Avanzado > Red") y `no_results_found` si no hay ninguno. Elegir un resultado sustituye la
  búsqueda por la pantalla de ese ajuste y lo resalta: `SearchableSettings.highlightKey`, que el escritorio ya tiene
  de Mihon.
- El índice son las mismas pantallas que lista `SettingsTab`, en el mismo orden. Para no mantener dos listas,
  `SettingsTab` expone `screens` (título, icono y pantalla) y la búsqueda usa las que son `SearchableSettings`.
- Entrada: icono de lupa en la barra de `SettingsTab` (`action_search_settings`, con `es`).
- Esc o la flecha atrás cierran la búsqueda, como cualquier pantalla.

## 3. Pruebas
- `SettingsSearchTest` (Compose): escribir "DNS" muestra "DNS sobre HTTPS" con la ruta de Avanzado; pulsarlo abre
  Avanzado. Escribir algo sin coincidencias muestra `no_results_found`.
