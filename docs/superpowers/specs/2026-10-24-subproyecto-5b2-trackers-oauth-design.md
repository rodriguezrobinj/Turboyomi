# Diseño: subproyecto 5b-2 — trackers con OAuth

- **Fecha:** 2026-10-24
- **Estado:** aprobado en conversación; pendiente de revisión escrita
- **Spec padre:** `2026-10-18-windows-port-design.md` (requisito 5 de la sección 1, sección 5.5)
- **Spec hermana:** `2026-10-23-subproyecto-5b1-seguimiento-design.md` (núcleo de seguimiento, credenciales DPAPI,
  seam `BaseTracker.testInterceptor`, `TrackerManager(extra)`, Apollo)
- **Base:** `master` en `d65152f8d` (subproyecto 5b-1 integrado; 470 tests)

## 1. Objetivo y alcance

### Objetivo
1. Los 7 trackers de Mihon que inician sesión con OAuth: AniList, MyAnimeList, Shikimori, Bangumi, Comick, MangaBaka y
   Hikka.
2. Login con el navegador del sistema, que vuelve a la app por un protocolo propio de Windows.

### Decisiones tomadas
| Decisión | Elección |
|---|---|
| Client IDs | Propios (spec padre §5.5), no los de Mihon. Los registra el usuario en cada servicio |
| Dónde viven | `tracker-keys.properties` en la raíz del repo, en `.gitignore`, más una plantilla `tracker-keys.example.properties` commiteada. Gradle genera `TrackerKeys` al compilar |
| Sin ID | El tracker no se registra en `TrackerManager`, así que no aparece. Sus tracks de un backup se quedan en la base de datos (como en 5b-1) |
| Redirect | `mangareader://<servicio>-auth` (por ejemplo, `mangareader://anilist-auth`). Comick exige dominio inverso: `app.mangareader://comick-auth` |
| Protocolo | La app registra `mangareader` y `app.mangareader` en `HKCU\Software\Classes` al arrancar, en cada arranque, apuntando al ejecutable actual. No hace falta instalador ni permisos de administrador. El empaquetado podrá registrarlo también |
| Vuelta a la app | La segunda instancia que lanza Windows reenvía la URL por el socket de instancia única (`SingleInstance.forward`, ya existe) y termina |
| IDs de los trackers | Los de Mihon: MyAnimeList 1, AniList 2, Shikimori 4, Bangumi 5, Hikka 10, MangaBaka 11, Comick 12 |

### Cambios respecto a la spec padre
- §5.5 dice que el instalador registra el protocolo. Lo registra la propia app al arrancar, porque no hay instalador
  hasta el empaquetado y así el login funciona también en desarrollo. El instalador podrá hacerlo además.
- §5.5 nombra el protocolo `<app>://`. Queda `mangareader://`, más `app.mangareader://` para Comick.

### Fuera de alcance (5b-2)
| Qué | Dónde |
|---|---|
| Registrar las apps en cada servicio y conseguir los IDs | El usuario |
| Registro del protocolo por el instalador | Empaquetado |

## 2. Arquitectura

### Piezas de Mihon (copiadas con su paquete)
- `eu/kanade/tachiyomi/data/track/{anilist,myanimelist,shikimori,bangumi,comick,mangabaka,hikka}/**`.
- Las consultas GraphQL de AniList y Shikimori (`app/src/main/graphql/mihon/graphql/{anilist,shikimori}`), como
  servicios Apollo, igual que Kitsu y Suwayomi en 5b-1.
- La lógica de `ui/setting/track/TrackLoginActivity` (y su base), sin la Activity: pasa a `TrackLoginHandler`.
- Los logos `brand_*.xml` y `brand_{comick,mangabaka}.webp` como recursos del classpath.

### Piezas de escritorio
| Pieza | Qué hace |
|---|---|
| `TrackerKeys` (generada) | Client ID y secret por servicio, leídos de `tracker-keys.properties` al compilar. `null` si falta |
| `ProtocolRegistrar` | Escribe `HKCU\Software\Classes\mangareader` y `app.mangareader`, con `URL Protocol` y `shell\open\command` = el comando de esta app + `"%1"`. Usa la línea de comandos del proceso actual (`ProcessHandle`): el exe en el MSI, la JVM con su classpath en desarrollo |
| `PendingLogins` | El login abierto por servicio: el `state` aleatorio y el `code_verifier` de PKCE, en memoria |
| `TrackLoginHandler` | Recibe una URL `mangareader://…` o `app.mangareader://…`, comprueba el `state`, intercambia el código o el token con el tracker que corresponda y guarda las credenciales (cifradas, 5b-1) |
| Entrada en `Main` | Las URLs que llegan por `SingleInstance.listen` o como argumento del primer arranque van a `TrackLoginHandler`; luego se trae la ventana al frente |

### Cambios en lo de 5b-1
- `TrackerManager` registra los 7, cada uno solo si `TrackerKeys` tiene su ID.
- `TrackPreferences` recupera `anilistScoreType` y `mangabakaScoreType`.
- Ajustes › Seguimiento recupera los 7 `TrackerPreference` de Mihon. Su `login` abre la URL de autorización con
  `PendingLogins`, en lugar del `openInBrowser` directo de Mihon.

## 3. Comportamiento

### A. Login
1. En Ajustes › Seguimiento, "Iniciar sesión" en un tracker OAuth crea un login pendiente (`state` y, si toca, PKCE)
   y abre la URL de autorización en el navegador del sistema (`Desktop.browse`).
2. Tras autorizar, el servicio redirige a `mangareader://<servicio>-auth?code=…&state=…`, o a
   `#access_token=…` en AniList.
3. Windows lanza la app con esa URL. Si ya hay una abierta, la nueva instancia le reenvía la URL y termina. Si no, la
   nueva instancia es la primera y procesa la URL al arrancar.
4. `TrackLoginHandler` comprueba el login pendiente y el `state`, intercambia el código por el token (o toma el token
   de AniList), lo guarda cifrado, lee el usuario del servicio y avisa "Sesión iniciada en X". La ventana pasa al
   frente.

### B. Flujos por servicio (los de Mihon)
| Servicio | Flujo |
|---|---|
| AniList | Implícito: el token llega en el fragmento. Sin renovación; dura un año |
| MyAnimeList | Código con PKCE; `refresh_token` |
| Shikimori | Código con client secret; `refresh_token` |
| Bangumi | Código con client secret; `refresh_token` |
| Comick | Código con PKCE; esquema `app.mangareader://` |
| MangaBaka | Código con PKCE |
| Hikka | `reference` de cliente y secret; token por la `reference` que devuelve |

El `state` se añade donde el servicio lo admite. En AniList y Hikka, si su autorización no lo acepta, la protección
es el login pendiente: un redirect sin login abierto se ignora.

### C. Renovación
- MyAnimeList, Shikimori, Bangumi, Comick, MangaBaka y Hikka renuevan el token como en Mihon.
- Si MyAnimeList no puede renovarlo, marca `trackAuthExpired` y Ajustes › Seguimiento muestra "volver a iniciar
  sesión", como en Mihon. Los demás muestran el error.
- AniList, cuando su token caduca, pide volver a iniciar sesión.

## 4. Errores
| Caso | Comportamiento |
|---|---|
| El usuario cancela (`error=access_denied`) | Aviso "No se pudo iniciar sesión en X" con el mensaje del servicio; no se guarda nada |
| Código caducado o rechazado | Igual, con el mensaje del servicio |
| Redirect sin login pendiente o con un `state` que no coincide | Se ignora y se apunta en el log. Un enlace `mangareader://` ajeno no vincula ninguna cuenta |
| No se puede escribir en `HKCU` (políticas del sistema) | Aviso en Ajustes › Seguimiento: "El navegador no puede volver a la app; los trackers OAuth no funcionarán". El resto sigue |
| Falta `tracker-keys.properties` o un ID | Compila igual; esos trackers no aparecen |
| La app se cierra a mitad del login | El redirect la abre; sin login pendiente en memoria, se ignora y el usuario repite el login. El aviso lo explica |

La última fila es la consecuencia de guardar el login pendiente solo en memoria. Un `code_verifier` en disco sería un
secreto más que proteger, para ahorrar repetir un login.

## 5. Pruebas

### Automáticas
- **`TrackLoginHandler`, contra `MockWebServer`** (seam `BaseTracker.testInterceptor` de 5b-1), con IDs de prueba
  propios. Un test por cada flujo distinto:
  - el token implícito de AniList;
  - el código con PKCE de MyAnimeList: el `code_verifier` enviado es el de la URL de autorización;
  - el código con secret de Shikimori y Bangumi;
  - el PKCE de Comick con su esquema `app.mangareader://`, y el de MangaBaka;
  - la `reference` de Hikka.
  - En cada uno, la sesión queda iniciada y la credencial cifrada.
- **Seguridad:** un `state` que no coincide se ignora; un redirect sin login pendiente se ignora;
  `error=access_denied` avisa y no guarda nada.
- **Renovación:** un 401 de MyAnimeList renueva con el `refresh_token`; si la renovación falla, la sesión queda
  caducada.
- **Instancia única:** una segunda instancia con `mangareader://anilist-auth#…` entrega la URL a la primera y termina.
- **Arranque con URL:** la app procesa la URL que recibe como argumento al arrancar.
- **Registro del protocolo:** escribe `URL Protocol` y `shell\open\command` con el comando y `"%1"`. Se prueba contra
  una clave temporal de `HKCU\Software\Classes`, no contra la real, y se borra después.
- **Claves:** sin un ID el tracker no se registra y Ajustes no lo muestra; con el fichero de test, sí.

### Manual (al usuario, con sus IDs)
1. Iniciar sesión en cada servicio desde Ajustes, con el navegador volviendo a la app.
2. Cerrar la app a mitad de un login y comprobar el aviso al volver.
3. Vincular un manga, leer un capítulo y verlo en la web del tracker.
4. Cancelar un login en el navegador y comprobar el aviso.
