# Diseño: subproyecto 4a — lectura local y almacenamiento

- **Fecha:** 2026-10-22
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (requisito 3 de la sección 1, secciones 4.1, 4.3 y 5.3)
- **Specs hermanas:** `2026-10-19-subproyecto-2a-ui-lector-design.md` (lector y pauta de portado),
  `2026-10-21-subproyecto-3b-migracion-proximos-design.md`
- **Base:** `master` en `27d8bfe01` (subproyecto 3 integrado; 365 tests)

## 1. Objetivo y alcance

### Objetivo
Leer mangas guardados en el disco, con el mismo layout que Mihon:
1. Una fuente "Local" en Explorar que lee la carpeta `local/` de la ubicación de almacenamiento.
2. El lector abre capítulos locales: carpetas de imágenes, archivos comprimidos y EPUB.
3. La ubicación de almacenamiento se puede elegir en Ajustes.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| División del subproyecto 4 | 4a (esta spec): lectura local y almacenamiento. 4b: descargas. 4b reutiliza la capa de archivos de 4a para leer los CBZ descargados. Cada uno con su spec, plan y merge |
| Librería de archivos comprimidos | 7-Zip-JBinding 16.02-2.01 con nativos de Windows x64. Lee ZIP, RAR4, RAR5, 7z y TAR, como libarchive en Mihon. Se descartan Commons Compress + junrar (sin RAR5) y libarchive por JNA (hay que compilar la DLL y escribir los bindings) |
| Enfoque | Portar la capa de Mihon sobre JVM con el mismo paquete y la misma API. Se descartan una fuente local propia (rompe la compatibilidad con las carpetas de Android) y descomprimir a una carpeta temporal (lento y deja basura en disco) |
| Patrón de portado de módulos | El de `core/common` en el subproyecto 1: el código de Mihon se queda en `src/main`; lo que es solo de Android se excluye y tiene su copia de escritorio en `src/desktop/kotlin`, con la misma ruta relativa |

### Fuera de alcance (4a)
| Qué | Dónde |
|---|---|
| Descargas, cola, `Downloader`, `DownloadPageLoader`, filtro "Descargados", `getComicInfo` | 4b |
| `ZipWriter` (escribir CBZ) | 4b |
| Copias de seguridad y limpieza de caché en "Datos y almacenamiento" | Subproyecto 5 |
| Incluir los nativos de 7-Zip en el MSI | Empaquetado |

## 2. Arquitectura

### Módulos que pasan a JVM
| Módulo | Qué se excluye de `src/main` | Copia de escritorio en `src/desktop/kotlin` |
|---|---|---|
| `core/archive` | `ArchiveReader.kt`, `ArchiveInputStream.kt`, `UniFileExtensions.kt`, `ZipWriter.kt` | `ArchiveReader` y `ArchiveInputStream` sobre 7-Zip-JBinding; `UniFileExtensions` (`archiveReader`, `epubReader`) que abre el `File` sin `ParcelFileDescriptor`. `ZipWriter` no se copia hasta 4b |
| `core-metadata` | Nada (no usa Android) | — |
| `source-local` | `LocalSource.kt` | La misma clase con un solo cambio: `AndroidXmlReader` pasa a ser el lector de xmlutil para JVM |

Los tres se añaden a `settings.gradle.kts` con el plugin `mihonx.plugins.kotlin.jvm`, como `core/common` y `domain`.

### API de archivos (la de Mihon, sin cambios de firma)
- `ArchiveReader : Closeable`:
  - `useEntries(block: (Sequence<ArchiveEntry>) -> T): T`;
  - `getInputStream(entryName: String): InputStream?`.
- `ArchiveEntry(name: String, isFile: Boolean)`.
- `UniFile.archiveReader(context)` y `UniFile.epubReader(context)`.
- Implementación de escritorio:
  - se abre con `SevenZip.openInArchive(null, RandomAccessFileInStream(RandomAccessFile(file, "r")))`;
  - las entradas salen de `simpleInterface.archiveItems`;
  - `getInputStream` extrae solo esa entrada a memoria (`extractSlow`) y devuelve un `ByteArrayInputStream`.
- Nunca se carga el archivo entero en memoria.
- Si los nativos no cargan, `ArchiveReader` lanza una excepción con el texto `desktop_archive_reader_unavailable` ("No se pudo cargar el lector de archivos") y el detalle va al log.

### Piezas en desktop-app y desktop-core
| Pieza | Qué hace |
|---|---|
| `DesktopSourceManager` | Pone `LocalSource` primera en la lista de fuentes, como `AndroidSourceManager` |
| `DesktopFolderProvider` (`@ContributesBinding`) | Carpeta por defecto `Documentos\MangaReader`, resuelta con `FileSystemView.getFileSystemView().defaultDirectory`, que respeta la redirección de OneDrive |
| `StorageManager`, `StoragePreferences` | Los de `domain`, sin cambios. Al cambiar la ubicación crean `local/`, `downloads/` y `autobackup/` |
| `ChapterLoader` | El de Mihon completo: la rama de `LocalSource` usa `DirectoryPageLoader`, `ArchivePageLoader` o `EpubPageLoader` según `Format`. La rama de descargas sigue fuera hasta 4b |
| `DirectoryPageLoader`, `ArchivePageLoader`, `EpubPageLoader` | Copiados de Mihon |
| Ajustes › "Datos y almacenamiento" | `SettingsDataScreen` de Mihon solo con la ubicación, su selector y el texto de ayuda. Se añade a la lista de `SettingsTab` |
| `FileDialogs.chooseDirectory(title)` | Selector de carpetas de Windows |
| `MangaExtensions.editCover` | La versión de Mihon: un manga local guarda su portada en su carpeta con `LocalCoverManager` |
| Stub `LocalSource` | Se borra de `desktop-app/src/stubs` |

### Dependencias nuevas
- `net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01` y `sevenzipjbinding-windows-amd64:16.02-2.01` en `core/archive`.
- Licencia LGPL 2.1 con la restricción unRAR (prohíbe crear RAR, que la app no hace). Se añade a `NOTICE`.
- Las muestras RAR de los tests vienen de la suite de tests de libarchive (licencia BSD-2), también citada en `NOTICE`.

## 3. Flujos

### A. Fuente local en Explorar
- "Local" aparece primera en Explorar › Fuentes, con el icono y el botón de ayuda de Mihon (`LocalSource.HELP_URL`).
- **Populares:** las carpetas de `local/` en orden alfabético natural. **Recientes:** por fecha de modificación.
  **Búsqueda:** filtra por nombre, con los filtros de orden de Mihon (nombre o fecha).
- **Ficha:**
  - título, autor, artista, descripción, géneros y estado salen de `details.json` o de `ComicInfo.xml`; si no hay ninguno, del nombre de la carpeta;
  - la portada sale de `cover.jpg` y, si no hay, de la primera imagen del primer capítulo.
- **Capítulos:** uno por archivo soportado o subcarpeta, con nombre y número sacados del nombre del archivo (Mihon).
- F5 vuelve a leer la carpeta.
- Las carpetas `local/` copiadas desde Android se leen sin conversión.

### B. Lectura
`ChapterLoader` elige el cargador según `LocalSource.getFormat`:
| Formato | Cargador |
|---|---|
| Carpeta | `DirectoryPageLoader`: imágenes en orden natural |
| `zip`, `cbz`, `rar`, `cbr`, `7z`, `cb7`, `tar`, `cbt` | `ArchivePageLoader` |
| `epub` | `EpubPageLoader`: imágenes en el orden del OPF |

El progreso, el historial y "marcar como leído" funcionan como con las fuentes online.

### C. Ajustes › Datos y almacenamiento
- Muestra la ruta actual de la carpeta base.
- "Cambiar" abre el selector de carpetas de Windows. Al elegir una se guarda en `storage_dir` y `StorageManager`
  crea `local/`, `downloads/` y `autobackup/` dentro.
- Lo que había en la ubicación anterior no se mueve (como en Mihon). El texto de ayuda de Mihon
  (`pref_storage_location_info`) lo explica.

### D. Portada personalizada de un manga local
Editar la portada de un manga de la fuente local la guarda como `cover.jpg` en su carpeta (`LocalCoverManager`),
no en la caché de portadas.

## 4. Errores
| Caso | Comportamiento |
|---|---|
| Archivo dañado o protegido con contraseña | Al listar, se salta y los demás capítulos siguen (Mihon). Al abrirlo, el lector muestra el error con el texto de la excepción |
| Archivo de formato no soportado en `local/` (`.pdf`, por ejemplo) | No sale como capítulo |
| La carpeta base no existe o no se puede escribir | La fuente local sale vacía con el `EmptyScreen` de Mihon. En Ajustes se ve la ruta, para poder cambiarla |
| Nombres con caracteres no ASCII (`José`, japonés) | Funcionan: 7-Zip-JBinding lee las rutas en Unicode |
| Los nativos de 7-Zip no cargan (temp bloqueado, antivirus) | Los capítulos comprimidos y los EPUB dan "No se pudo cargar el lector de archivos" y el log guarda el detalle. Las carpetas siguen funcionando |
| Archivo muy grande (más de 1 GB) | Se lee entrada a entrada. Cada página se extrae cuando se pide |

## 5. Testing y aceptación

### Tests unitarios (en `./gradlew test`)
- `core/archive`, `ArchiveReaderTest`:
  - lista y lee las entradas de un ZIP, un 7z y un TAR generados en el test (con `java.util.zip` y con
    7-Zip-JBinding, que crea 7z y TAR);
  - lee muestras RAR4 y RAR5 de la suite de libarchive, en `src/test/resources`;
  - rutas con `José Pérez` y entradas en subcarpetas;
  - `getInputStream` de una entrada que no existe devuelve `null`;
  - un archivo dañado lanza una excepción y no se cuelga.
- `core/archive`, `EpubReaderTest`: un EPUB generado en el test (OPF y XHTML con imágenes) devuelve sus páginas en orden.
- desktop-app, `LocalSourceTest` (`TestAppGraph`, carpeta base temporal con `José Pérez`):
  - listado, búsqueda y orden;
  - detalles desde `details.json` y desde `ComicInfo.xml`;
  - portada `cover.jpg` y, si no hay, la de la primera imagen;
  - capítulos de carpeta, CBZ, CB7 y EPUB; un `.pdf` se ignora; un CBZ dañado se salta.
- desktop-app, `ChapterLoaderTest`: un capítulo local de carpeta, uno de CBZ y uno de EPUB cargan sus páginas en el
  `ReaderViewModel`.
- `DesktopFolderProvider`: la carpeta por defecto es `Documentos\MangaReader`. Cambiar la ubicación crea las tres
  subcarpetas.

### UI (`runComposeUiTest`)
- Explorar › Fuentes muestra "Local" primera; al abrirla salen los mangas de la carpeta.
- Ajustes › Datos y almacenamiento muestra la ruta actual.
- `SpanishStringsTest` cubre las cadenas nuevas.

### Prueba manual (última tarea del plan)
1. Copiar una carpeta `local/` de Android a `Documentos\MangaReader\local` y verla en Explorar › Local con portadas
   y detalles.
2. Leer un CBR (RAR5), un CB7, un EPUB y una carpeta.
3. Añadir un manga local a la biblioteca, leer un capítulo, cerrar y volver: el progreso se conserva.
4. Cambiar la ubicación en Ajustes a otra carpeta: se crean `local/`, `downloads/` y `autobackup/`.
5. Poner una portada personalizada a un manga local: aparece `cover.jpg` en su carpeta.

### Criterios de aceptación
- Todos los tests verdes, `spotlessCheck` limpio y los checks de licencias y de "sin Android" pasan.
- La prueba manual pasa.

## 6. Riesgos
- La última versión de 7-Zip-JBinding es de 2016 (16.02). Si no funciona en JDK 21, la salida es junrar más Commons
  Compress, perdiendo RAR5. Se comprueba en la primera tarea del plan, antes de portar lo demás.
- En un 7z sólido, extraer cada página por separado obliga a descomprimir desde el inicio del bloque y puede ser
  lento. Si se nota, se cachea la extracción del capítulo abierto; queda fuera de 4a.
- El MSI tendrá que incluir los nativos de 7-Zip (7-Zip-JBinding los extrae a una carpeta temporal en el primer uso).
  Queda anotado para el empaquetado.
