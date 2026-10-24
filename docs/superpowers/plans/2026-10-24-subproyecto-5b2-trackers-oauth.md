# Subproyecto 5b-2: trackers con OAuth — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AniList, MyAnimeList, Shikimori, Bangumi, Comick, MangaBaka y Hikka, con login en el navegador del sistema que vuelve a la app por `mangareader://`.

**Architecture:** Se copian los 7 trackers de Mihon y se cambia solo lo de Android:
- `android.net.Uri` pasa a un shim de escritorio sobre `HttpUrl`.
- Los client IDs salen de `TrackerKeys`, que lee un recurso generado a partir de `tracker-keys.properties` (fuera de git).
- Los redirects son `mangareader://…`.

La lógica de `TrackLoginActivity` pasa a `TrackLoginHandler`, que recibe la URL:
- desde el socket de instancia única (ya existe),
- o como argumento del primer arranque.

`ProtocolRegistrar` registra los esquemas en `HKCU` al arrancar. `PendingLogins` guarda en memoria los logins abiertos y su `state`.

**Tech Stack:** Kotlin/JVM, Compose Desktop, Metro, Apollo Kotlin 5 (AniList, Shikimori), OkHttp, JNA `Advapi32Util` (registro), JUnit 5 + kotest + MockWebServer.

**Spec:** `docs/superpowers/specs/2026-10-24-subproyecto-5b2-trackers-oauth-design.md`

## Global Constraints

- Ficheros de Mihon copiados con su paquete a `desktop-app/src/main/kotlin/<ruta>`. Cada cambio lleva `// desktop:`.
- `scripts/check-no-android.sh` debe pasar. Se quitan `android.net.Uri`, `androidx.core.net.toUri`, `androidx.annotation.*`, `eu.kanade.tachiyomi.R`, `BuildConfig` e Injekt donde no esté expuesto.
- Strings nuevos con prefijo `desktop_`, en base y es. Los de Mihon sin traducción van a la sección "Mihon strings with no Spanish translation upstream".
- Tests `= runBlocking {}` con `: Unit`; `waitUntil(timeoutMillis = …)` con nombre.
- Scripts de edición con la herramienta Write y `PYTHONPATH=<workspace> python`.
- Commits con `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`; nunca push a `upstream`.
- IDs de los trackers: MyAnimeList 1, AniList 2, Shikimori 4, Bangumi 5, Hikka 10, MangaBaka 11, Comick 12 (spec §1).
- Redirects: `mangareader://<servicio>-auth`; Comick `app.mangareader://comick-auth` (spec §1).
- `tracker-keys.properties` fuera de git, con plantilla `tracker-keys.example.properties` (spec §1).

## Desviaciones respecto a la spec

1. **`state` y PKCE (§2, §3 B).** Comick y MangaBaka ya generan y comprueban su `state` y su PKCE en su API (`verifyOAuthState`), como en Mihon, y se quedan así.
   - `PendingLogins` guarda el login abierto por servicio.
   - Añade un `state` a la URL solo en MyAnimeList, Shikimori y Bangumi, que lo devuelven según OAuth 2.
   - AniList y Hikka solo exigen el login pendiente (spec §3 B ya lo prevé).
2. **Errores de login.** El `login(code)` de cada tracker de Mihon captura cualquier error y cierra la sesión sin lanzar nada. `TrackLoginHandler` decide si salió bien mirando `isLoggedIn` después. El aviso de error dice "No se pudo iniciar sesión en X", sin el mensaje del servicio salvo cuando el redirect trae `error` / `error_description`.
3. **Claves en los tests.** Una máquina con `tracker-keys.properties` lo empaqueta en los recursos.
   - Para que los tests no dependan de él, `TrackerKeys` no lo lee cuando la propiedad de sistema `mangareader.tracker-keys` vale `none`. `tasks.test` la fija.
   - Los tests que necesitan claves usan el seam `TrackerKeys.override`.
4. **Instancia única (§5).** `SingleInstanceTest` (desktop-core) ya prueba que una segunda instancia entrega sus argumentos y termina. Lo nuevo se prueba en `LoginUrls.route`: el reparto de esos argumentos (URLs de login al handler, el resto se ignora).

## Review Focus

1. Una URL `mangareader://` con caracteres codificados (`%2B`, `+`) en el código o el token: se decodifica una sola vez. Test en la Task 2.
2. Dos logins abiertos a la vez en servicios distintos: cada redirect cierra solo el suyo. Test en la Task 2.
3. El mismo redirect recibido dos veces (doble clic en el navegador): el segundo se ignora porque ya no hay login pendiente. Test en la Task 2.
4. Una ruta de la app con espacios o acentos (`C:\Users\José Pérez\…`) en el comando del registro: queda entre comillas. Test en la Task 3.
5. Login con la app cerrada: la URL del primer arranque se procesa cuando el grafo ya existe, no antes. Lo cubren `LoginUrls.route` y el orden en `Main` (Task 3), y la prueba manual.

---

### Task 1: los 7 trackers, sus claves y el shim de `Uri`

**Files:**
- Copy:
  - `eu/kanade/tachiyomi/data/track/{anilist,myanimelist,shikimori,bangumi,comick,mangabaka,hikka}/**`;
  - `eu/kanade/tachiyomi/util/PkceUtil.kt`;
  - `app/src/main/graphql/mihon/graphql/{anilist,shikimori}/*` a `desktop-app/src/main/graphql/mihon/graphql/`;
  - los logos `app/src/main/res/drawable/brand_{anilist,myanimelist,shikimori,bangumi,hikka}.xml` y `app/src/main/res/drawable-nodpi/brand_{comick,mangabaka}.webp` a `desktop-app/src/main/resources/drawable/`.
- Create:
  - `desktop-app/src/main/kotlin/mihon/desktop/app/track/Uri.kt` (shim);
  - `desktop-app/src/main/kotlin/mihon/desktop/app/track/TrackerKeys.kt`;
  - `tracker-keys.example.properties`.
- Modify:
  - `.gitignore`;
  - `desktop-app/build.gradle.kts` (servicios Apollo, recurso de claves, propiedad de sistema en `tasks.test`);
  - `TrackerManager.kt` y `TrackPreferences.kt`.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/track/OAuthTrackersTest.kt`

**Interfaces:**
- Produces:
  - `TrackerKeys`: `data class Keys(clientId: String, secret: String? = null)`, con `val anilist: Keys?`, `myAnimeList`, `shikimori`, `bangumi`, `comick`, `mangaBaka` y `hikka` (en Hikka, `clientId` es la `reference`), y `@Volatile var override: Map<String, Keys>?` para tests.
  - `TrackerManager`: `aniList`, `myAnimeList`, `shikimori`, `bangumi`, `comick`, `mangaBaka` y `hikka`, como `val … : X?`, `null` si no hay clave.
  - Cada API: `authUrl(...)`, que devuelve un `Uri` del shim (`toString()` da la URL), con el client ID de `TrackerKeys` y el redirect `mangareader://…`.

- [ ] **Step 1: Copiar**

```bash
A=app/src/main/java/eu/kanade/tachiyomi; D=desktop-app/src/main/kotlin/eu/kanade/tachiyomi
for t in anilist myanimelist shikimori bangumi comick mangabaka hikka; do cp -r $A/data/track/$t $D/data/track/; done
mkdir -p $D/util desktop-app/src/main/graphql/mihon/graphql desktop-app/src/main/resources/drawable
cp $A/util/PkceUtil.kt $D/util/
cp -r app/src/main/graphql/mihon/graphql/anilist app/src/main/graphql/mihon/graphql/shikimori desktop-app/src/main/graphql/mihon/graphql/
cp app/src/main/res/drawable/brand_{anilist,myanimelist,shikimori,bangumi,hikka}.xml desktop-app/src/main/resources/drawable/
cp app/src/main/res/drawable-nodpi/brand_{comick,mangabaka}.webp desktop-app/src/main/resources/drawable/
```

- [ ] **Step 2: Escribir los tests**

```kotlin
package mihon.desktop.app.track

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.comick.ComickApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

class OAuthTrackersTest {
    // Keys must be in place before the graph builds TrackerManager (once per test JVM)
    private val graph: TestAppGraph = TrackerKeys.withTestKeys { startTestApp() }

    @Test
    fun `with keys the seven OAuth trackers are registered with Mihon's ids`() {
        mapOf(1L to "MyAnimeList", 2L to "AniList", 4L to "Shikimori", 5L to "Bangumi", 10L to "Hikka",
            11L to "MangaBaka", 12L to "Comick").forEach { (id, name) ->
            graph.trackerManager.get(id)!!.name shouldBe name
        }
    }

    @Test
    fun `the authorization urls carry our client id and the mangareader redirect`() {
        AnilistApi.authUrl().toString() shouldContain "client_id=test-anilist"
        MyAnimeListApi.authUrl(state = "s1").toString().let {
            it shouldStartWith "https://myanimelist.net/v1/oauth2/authorize"
            it shouldContain "client_id=test-mal"
            it shouldContain "state=s1"
            it shouldContain "code_challenge="
        }
        ComickApi.authUrl().toString() shouldContain "redirect_uri=app.mangareader%3A%2F%2Fcomick-auth"
    }

    @Test
    fun `the Uri shim builds paths and encodes query values once`() {
        val url = "https://api.example.org/v2/manga".toUri().buildUpon()
            .appendPath("42")
            .appendQueryParameter("q", "José + María")
            .build()

        url.toString() shouldBe "https://api.example.org/v2/manga/42?q=Jos%C3%A9%20%2B%20Mar%C3%ADa"
    }
}
```

Y un segundo fichero para comprobar que, sin claves, no hay trackers OAuth. Tiene que ser otra clase, porque el grafo es uno por JVM y la tarea de test hace fork por clase:

```kotlin
package mihon.desktop.app.track

import io.kotest.matchers.shouldBe
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import org.junit.jupiter.api.Test

class OAuthTrackersWithoutKeysTest {
    private val graph: TestAppGraph = startTestApp()

    @Test
    fun `without keys no OAuth tracker is registered`() {
        listOf(1L, 2L, 4L, 5L, 10L, 11L, 12L).forEach { graph.trackerManager.get(it) shouldBe null }
    }
}
```

`TrackerKeys.withTestKeys(block)` pone en `override` las claves de prueba (`test-anilist`, `test-mal`, `test-shikimori` + `test-shikimori-secret`, `test-bangumi` + secret, `test-comick`, `test-mangabaka` y `test-hikka` + secret) y devuelve `block()`. Es una función `internal` para tests.

- [ ] **Step 3: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.OAuthTrackers*"`
Expected: FAIL de compilación (`TrackerKeys`, `toUri` y los trackers no compilan; usan `android.net.Uri`, `R` y `BuildConfig`).

- [ ] **Step 4: El shim, las claves y la configuración**

`mihon/desktop/app/track/Uri.kt`:

```kotlin
package mihon.desktop.app.track

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The slice of android.net.Uri the trackers use to build http(s) urls (buildUpon, appendPath, appendQueryParameter),
 * over OkHttp's HttpUrl. Ported tracker code imports this instead of android.net.Uri (plan 5b-2, task 1).
 */
class Uri private constructor(private val url: HttpUrl) {
    fun buildUpon(): Builder = Builder(url.newBuilder())

    override fun toString(): String = url.toString()

    class Builder internal constructor(private val builder: HttpUrl.Builder) {
        fun appendPath(segment: String): Builder = apply { builder.addPathSegment(segment) }

        fun appendQueryParameter(key: String, value: String?): Builder = apply { builder.addQueryParameter(key, value) }

        fun build(): Uri = Uri(builder.build())
    }

    companion object {
        fun of(url: String): Uri = Uri(url.toHttpUrl())
    }
}

fun String.toUri(): Uri = Uri.of(this)
```

`mihon/desktop/app/track/TrackerKeys.kt`:

```kotlin
package mihon.desktop.app.track

import java.util.Properties

/**
 * The client ids (and secrets where a service needs one) registered for this app, from tracker-keys.properties: kept
 * out of git, packed as a resource at build time (spec 5b-2 §1). A service without its key isn't registered.
 */
object TrackerKeys {
    data class Keys(val clientId: String, val secret: String? = null)

    /** Tests' keys; tests also set mangareader.tracker-keys=none so a developer's own file never leaks in. */
    @Volatile
    internal var override: Map<String, Keys>? = null

    private val loaded: Map<String, Keys> by lazy {
        if (System.getProperty("mangareader.tracker-keys") == "none") return@lazy emptyMap()
        val properties = Properties()
        TrackerKeys::class.java.getResourceAsStream("/tracker-keys.properties")?.use { properties.load(it) }
        SERVICES.mapNotNull { service ->
            val id = properties.getProperty("$service.client_id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            service to Keys(id, properties.getProperty("$service.client_secret")?.takeIf { it.isNotBlank() })
        }.toMap()
    }

    private fun get(service: String): Keys? = (override ?: loaded)[service]

    val anilist get() = get("anilist")
    val myAnimeList get() = get("myanimelist")
    val shikimori get() = get("shikimori")
    val bangumi get() = get("bangumi")
    val comick get() = get("comick")
    val mangaBaka get() = get("mangabaka")
    val hikka get() = get("hikka")

    internal fun <T> withTestKeys(block: () -> T): T {
        override = mapOf(
            "anilist" to Keys("test-anilist"),
            "myanimelist" to Keys("test-mal"),
            "shikimori" to Keys("test-shikimori", "test-shikimori-secret"),
            "bangumi" to Keys("test-bangumi", "test-bangumi-secret"),
            "comick" to Keys("test-comick"),
            "mangabaka" to Keys("test-mangabaka"),
            "hikka" to Keys("test-hikka", "test-hikka-secret"),
        )
        return block()
    }

    private val SERVICES = listOf("anilist", "myanimelist", "shikimori", "bangumi", "comick", "mangabaka", "hikka")
}
```

`TrackerKeys.withTestKeys` es `internal` en `main`. Si el compilador no lo deja ver desde los tests del mismo módulo, se mueve a una extensión en `src/test` que escribe `override`, que también es `internal`.

`tracker-keys.example.properties` (raíz):

```properties
# Copy to tracker-keys.properties (git-ignored) and fill in the apps you registered (spec 5b-2 §1).
# Redirect to register in each service: mangareader://<service>-auth, Comick: app.mangareader://comick-auth
# A service left empty doesn't show up in the app.
anilist.client_id=
myanimelist.client_id=
shikimori.client_id=
shikimori.client_secret=
bangumi.client_id=
bangumi.client_secret=
comick.client_id=
mangabaka.client_id=
# Hikka: the client reference and its secret
hikka.client_id=
hikka.client_secret=
```

`.gitignore`: añadir `/tracker-keys.properties`.

`desktop-app/build.gradle.kts`:
- en el bloque `apollo { … }`, los servicios de Mihon, sin `introspection`:

```kotlin
    service("anilist") {
        packageName.set("mihon.graphql.anilist")
        srcDir(file("src/main/graphql/mihon/graphql/anilist"))
        // ISO 3166-1 alpha-2 country code
        mapScalarToKotlinString("CountryCode")
    }

    service("shikimori") {
        packageName.set("mihon.graphql.shikimori")
        srcDir(file("src/main/graphql/mihon/graphql/shikimori"))
        // An ISO 8601-encoded date
        mapScalarToKotlinString("ISO8601Date")
    }
```

  Se cambia el comentario del bloque a `// Mihon's GraphQL services for its trackers`.
- el recurso de claves:

```kotlin
// The tracker keys the developer registered (spec 5b-2 §1), when the git-ignored file is there
tasks.processResources {
    from(rootProject.file("tracker-keys.properties")) { into("") }
}
```

  (`from` de un fichero que no existe no copia nada).
- `tasks.test { forkEvery = 1 }` pasa a `tasks.test { forkEvery = 1; systemProperty("mangareader.tracker-keys", "none") }`.

- [ ] **Step 5: Adaptar los copiados**

En los 7 paquetes y en `PkceUtil`:
- **Imports:** `import android.net.Uri` pasa a `import mihon.desktop.app.track.Uri`, y `import androidx.core.net.toUri` a `import mihon.desktop.app.track.toUri`. En el primer fichero de cada paquete se añade `// desktop:` en el primer uso.
- **Logos:** `getLogo()` devuelve `"drawable/brand_<x>.xml"` o `"drawable/brand_<x>.webp"`, con `// desktop: classpath resource`. Se quita `import eu.kanade.tachiyomi.R`.
- **`BuildConfig.*`:** pasa a `DesktopBuildInfo.*`.
- **Client IDs:**
  - `CLIENT_ID`, `CLIENT_SECRET` y `CLIENT_REFERENCE` dejan de ser `const val` y salen de `TrackerKeys`, con `// desktop: our app's keys (spec 5b-2 §1)`. En AniList:

```kotlin
        private val CLIENT_ID get() = TrackerKeys.anilist?.clientId.orEmpty()
```

  - Lo mismo con `TrackerKeys.myAnimeList`, `shikimori` (`clientId` / `secret.orEmpty()`), `bangumi`, `comick`, `mangaBaka` y `hikka` (`CLIENT_REFERENCE` = `clientId`, `CLIENT_SECRET` = `secret.orEmpty()`).
- **Redirects:** `"mihon://…-auth"` pasa a `"mangareader://…-auth"`, y `"app.mihon://comick-auth"` a `"app.mangareader://comick-auth"`.
- **`state` en `authUrl`** de MyAnimeList, Shikimori y Bangumi (desviación 1): `fun authUrl(): Uri` pasa a `fun authUrl(state: String): Uri`, con `.appendQueryParameter("state", state)` antes de `.build()` y `// desktop: checked by TrackLoginHandler (spec 5b-2 §3 B)`.
- **`toast` y `Context`:** se quedan, porque son del shim de escritorio. `injectLazy()` de `Json` se queda, porque Injekt lo expone.

`TrackerManager.kt`:

```kotlin
    // desktop: Mihon's OAuth trackers, each only with its key (spec 5b-2 §1)
    val myAnimeList = MyAnimeList(1L).takeIf { TrackerKeys.myAnimeList != null }
    val aniList = Anilist(2L).takeIf { TrackerKeys.anilist != null }
    val shikimori = Shikimori(4L).takeIf { TrackerKeys.shikimori != null }
    val bangumi = Bangumi(5L).takeIf { TrackerKeys.bangumi != null }
    val hikka = Hikka(10L).takeIf { TrackerKeys.hikka != null }
    val mangaBaka = MangaBaka(11L).takeIf { TrackerKeys.mangaBaka != null }
    val comick = Comick(12L).takeIf { TrackerKeys.comick != null }
```

- La lista queda en el orden de Mihon: `listOfNotNull(mangaBaka, myAnimeList, aniList, kitsu, shikimori, bangumi, komga, mangaUpdates, kavita, suwayomi, hikka, comick) + extra`.
- Los usos de `trackerManager.aniList` y el resto en código portado (Ajustes, Task 3) pasan a manejar el `null`.

`TrackPreferences.kt`: vuelven `anilistScoreType` (`Anilist.POINT_10`) y `mangabakaScoreType` (`MangaBaka.STEP_1`), en lugar del comentario de 5b-1.

- [ ] **Step 6: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.*" --tests "mihon.desktop.app.backup.*"`
Expected: PASS.

Run: `bash scripts/check-no-android.sh`
Expected: OK.

- [ ] **Step 7: Commit**

```bash
git add .gitignore tracker-keys.example.properties desktop-app
git commit -m "feat(desktop-app): Mihon's seven OAuth trackers, keyed by our own git-ignored client ids

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `TrackLoginHandler` y `PendingLogins`

**Files:**
- Create:
  - `desktop-app/src/main/kotlin/mihon/desktop/app/track/PendingLogins.kt`;
  - `desktop-app/src/main/kotlin/mihon/desktop/app/track/TrackLoginHandler.kt`.
- Modify: `mihon/app/di/AppGraph.kt`, `i18n/.../base/strings.xml` y `es/strings.xml`.
- Test: `desktop-app/src/test/kotlin/mihon/desktop/app/track/TrackLoginHandlerTest.kt`

**Interfaces:**
- Consumes: de la Task 1, `TrackerManager.{aniList,…}`, los `authUrl` y `TrackerKeys.withTestKeys`.
- Produces:
  - `PendingLogins`: `fun open(service: String): String` (devuelve el `state`) y `fun take(service: String, state: String?): Take`, con `enum Take { Ok, None, WrongState }`.
  - `TrackLoginHandler`: `suspend fun handle(url: String)` y `fun authorizationUrl(service: String): String?`, que abre el login pendiente y devuelve la URL.
  - `val SERVICES`: `anilist`, `myanimelist`, `shikimori`, `bangumi`, `comick`, `mangabaka` y `hikka`. El host del redirect es `"$service-auth"`.
  - En `AppGraph`: `trackLoginHandler`.

- [ ] **Step 1: Escribir los tests**

```kotlin
package mihon.desktop.app.track

import eu.kanade.tachiyomi.data.track.BaseTracker
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import mihon.desktop.app.testing.TestAppGraph
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrackLoginHandlerTest {
    private val graph: TestAppGraph = TrackerKeys.withTestKeys { startTestApp() }
    private val handler get() = graph.trackLoginHandler
    private val server = MockWebServer()
    private val responses = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private val notices = CopyOnWriteArrayList<Notice>()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @BeforeAll
    fun redirect() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val key = "${request.method} ${request.requestUrl!!.host.removePrefix("www.")}${request.requestUrl!!.encodedPath}"
                return responses[key]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        val local = server.url("/")
        // Each service's host goes to the test server; the original host travels in a header the dispatcher reads back
        BaseTracker.testInterceptor = Interceptor { chain ->
            val original = chain.request().url
            val url = original.newBuilder().scheme("http").host(local.host).port(local.port)
                .encodedPath("/" + original.host + original.encodedPath).build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
    }

    @AfterAll
    fun stop() {
        BaseTracker.testInterceptor = null
        server.shutdown()
    }

    @BeforeEach
    fun capture() {
        notices.clear()
        requests.clear()
        TrayNotifier.sink = { notices += it }
    }

    private fun stateOf(url: String) = url.toHttpUrl().queryParameter("state")!!

    @Test
    fun `AniList takes the implicit token from the fragment and reads the viewer`(): Unit = runBlocking {
        responses["POST graphql.anilist.co/"] = {
            json("""{"data":{"Viewer":{"id":5,"name":"José","mediaListOptions":{"scoreFormat":"POINT_10"}}}}""")
        }
        handler.authorizationUrl("anilist")

        handler.handle("mangareader://anilist-auth#access_token=tok%2Ben&token_type=Bearer&expires_in=31536000")

        graph.trackerManager.aniList!!.isLoggedIn shouldBe true
        graph.trackerManager.aniList!!.getPassword() shouldBe "tok+en"
        notices.single().isError shouldBe false
        graph.trackerManager.aniList!!.logout()
    }

    @Test
    fun `MyAnimeList exchanges the code with the PKCE verifier of its authorization url`(): Unit = runBlocking {
        responses["POST myanimelist.net/v1/oauth2/token"] = {
            json("""{"refresh_token":"r","access_token":"a","expires_in":3600}""")
        }
        responses["GET api.myanimelist.net/v2/users/@me"] = { json("""{"name":"josé"}""") }
        val auth = handler.authorizationUrl("myanimelist")!!
        val challenge = auth.toHttpUrl().queryParameter("code_challenge")!!

        handler.handle("mangareader://myanimelist-auth?code=c1&state=${stateOf(auth)}")

        val form = requests.single { it.path!!.endsWith("/token") }.body.readUtf8()
        form shouldContain "code=c1"
        // MAL's PKCE is "plain": the verifier sent is the challenge of the authorization url
        form shouldContain "code_verifier=$challenge"
        graph.trackerManager.myAnimeList!!.isLoggedIn shouldBe true
        graph.trackerManager.myAnimeList!!.logout()
    }

    @Test
    fun `Bangumi exchanges the code with its secret and our redirect`(): Unit = runBlocking {
        responses["POST bgm.tv/oauth/access_token"] = {
            json("""{"access_token":"a","expires_in":3600,"refresh_token":"r"}""")
        }
        responses["GET api.bgm.tv/v0/me"] = { json("""{"username":"jose","nickname":"José"}""") }
        val auth = handler.authorizationUrl("bangumi")!!

        handler.handle("mangareader://bangumi-auth?code=c2&state=${stateOf(auth)}")

        val form = requests.single { it.path!!.endsWith("/oauth/access_token") }.body.readUtf8()
        form shouldContain "client_secret=test-bangumi-secret"
        form shouldContain "redirect_uri=mangareader%3A%2F%2Fbangumi-auth"
        graph.trackerManager.bangumi!!.isLoggedIn shouldBe true
        graph.trackerManager.bangumi!!.logout()
    }

    @Test
    fun `Comick takes its own state and PKCE through app mangareader`(): Unit = runBlocking {
        responses["POST comick.dev/api/auth/oauth2/token"] = {
            json(
                """{"access_token":"a","expires_at":${System.currentTimeMillis() / 1000 + 3600},"refresh_token":"r",""" +
                    """"scope":"library:read library:write offline_access"}""",
            )
        }
        responses["GET api.comick.dev/integrations/v1/me"] = { json("""{"id":"u1","username":"jose"}""") }
        val auth = handler.authorizationUrl("comick")!!

        handler.handle("app.mangareader://comick-auth?code=c3&state=${stateOf(auth)}")

        graph.trackerManager.comick!!.isLoggedIn shouldBe true
        graph.trackerManager.comick!!.logout()
    }

    @Test
    fun `Hikka takes its reference for a token`(): Unit = runBlocking {
        responses["POST api.hikka.io/auth/token"] = { json("""{"secret":"a","expiration":9999999999,"created":1}""") }
        responses["GET api.hikka.io/user/me"] = { json("""{"reference":"u1","username":"jose"}""") }
        handler.authorizationUrl("hikka")

        handler.handle("mangareader://hikka-auth?reference=ref-1")

        requests.single { it.path!!.endsWith("/auth/token") }.body.readUtf8() shouldContain "ref-1"
        graph.trackerManager.hikka!!.isLoggedIn shouldBe true
        graph.trackerManager.hikka!!.logout()
    }

    @Test
    fun `a redirect without a pending login is ignored with a notice to try again`(): Unit = runBlocking {
        handler.handle("mangareader://anilist-auth#access_token=intruso")

        graph.trackerManager.aniList!!.isLoggedIn shouldBe false
        notices.single().isError shouldBe true
    }

    @Test
    fun `a wrong state is ignored, and the right one still works afterwards`(): Unit = runBlocking {
        responses["POST bgm.tv/oauth/access_token"] = {
            json("""{"access_token":"a","expires_in":3600,"refresh_token":"r"}""")
        }
        responses["GET api.bgm.tv/v0/me"] = { json("""{"username":"jose","nickname":null}""") }
        val auth = handler.authorizationUrl("bangumi")!!

        handler.handle("mangareader://bangumi-auth?code=c&state=otro")
        graph.trackerManager.bangumi!!.isLoggedIn shouldBe false
        requests.none { it.path!!.endsWith("/oauth/access_token") } shouldBe true

        handler.handle("mangareader://bangumi-auth?code=c&state=${stateOf(auth)}")
        graph.trackerManager.bangumi!!.isLoggedIn shouldBe true
        graph.trackerManager.bangumi!!.logout()
    }

    @Test
    fun `a cancelled login warns and saves nothing; the same redirect twice is handled once`(): Unit = runBlocking {
        val auth = handler.authorizationUrl("shikimori")!!

        handler.handle("mangareader://shikimori-auth?error=access_denied&state=${stateOf(auth)}")
        handler.handle("mangareader://shikimori-auth?error=access_denied&state=${stateOf(auth)}")

        graph.trackerManager.shikimori!!.isLoggedIn shouldBe false
        notices.size shouldBe 2
        notices[0].message shouldContain "access_denied"
    }

    @Test
    fun `two logins open at once each close only their own`(): Unit = runBlocking {
        responses["POST graphql.anilist.co/"] = {
            json("""{"data":{"Viewer":{"id":5,"name":"José","mediaListOptions":{"scoreFormat":"POINT_10"}}}}""")
        }
        handler.authorizationUrl("anilist")
        handler.authorizationUrl("hikka")

        handler.handle("mangareader://anilist-auth#access_token=t")

        graph.trackerManager.aniList!!.isLoggedIn shouldBe true
        graph.pendingLogins.take("hikka", null) shouldBe PendingLogins.Take.Ok
        graph.trackerManager.aniList!!.logout()
    }
}
```

Al escribir este paso se ajustan dos cosas: las rutas exactas que esperan los `responses`, comprobadas contra las `*Api.kt` copiadas, y la forma de PKCE de MyAnimeList, que en Mihon es `plain` (`code_challenge` = `code_verifier`). Si es S256, la aserción pasa a comparar el `code_verifier` enviado con el que generó la URL. Lo que se ajuste se apunta como ruling.

`TestAppGraph` gana `val pendingLogins: PendingLogins`.

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.TrackLoginHandlerTest"`
Expected: FAIL de compilación (`trackLoginHandler` y `PendingLogins` no existen).

- [ ] **Step 3: Implementación**

`PendingLogins.kt`:

```kotlin
package mihon.desktop.app.track

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * The OAuth logins opened from this app, by service, with the state their redirect must bring back (spec 5b-2 §3 A,
 * §4). In memory only: a login opened before the app closed has to be started again.
 */
@Inject
@SingleIn(AppScope::class)
class PendingLogins {
    enum class Take { Ok, None, WrongState }

    private val states = ConcurrentHashMap<String, String>()

    fun open(service: String): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also { states[service] = it }
    }

    /** Closes [service]'s pending login if [state] matches; services that bring no state back pass null. */
    fun take(service: String, state: String?): Take {
        val expected = states[service] ?: return Take.None
        if (state != null && state != expected) return Take.WrongState
        states.remove(service)
        return Take.Ok
    }
}
```

`TrackLoginHandler.kt`:

```kotlin
package mihon.desktop.app.track

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.comick.ComickApi
import eu.kanade.tachiyomi.data.track.comick.ComickMissingScopesException
import eu.kanade.tachiyomi.data.track.hikka.HikkaApi
import eu.kanade.tachiyomi.data.track.mangabaka.MangaBakaApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import logcat.LogPriority
import mihon.desktop.app.tray.Notice
import mihon.desktop.app.tray.TrayNotifier
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.net.URI
import java.net.URLDecoder

/**
 * Desktop counterpart of Mihon's TrackLoginActivity (spec 5b-2 §2): opens a login in the browser and finishes it when
 * the service's redirect comes back to the app as a mangareader:// (or app.mangareader:// for Comick) url.
 */
@Inject
@SingleIn(AppScope::class)
class TrackLoginHandler(
    private val context: Context,
    private val trackerManager: TrackerManager,
    private val pendingLogins: PendingLogins,
) {
    /** Opens a pending login for [service] and gives the url to send the browser to; null if it has no key. */
    fun authorizationUrl(service: String): String? {
        if (tracker(service) == null) return null
        val state = pendingLogins.open(service)
        return when (service) {
            "anilist" -> AnilistApi.authUrl()
            "myanimelist" -> MyAnimeListApi.authUrl(state)
            "shikimori" -> ShikimoriApi.authUrl(state)
            "bangumi" -> BangumiApi.authUrl(state)
            "comick" -> ComickApi.authUrl()
            "mangabaka" -> MangaBakaApi.authUrl()
            "hikka" -> HikkaApi.authUrl()
            else -> null
        }?.toString()
    }

    suspend fun handle(url: String) {
        val uri = runCatching { URI(url) }.getOrNull() ?: return
        if (uri.scheme !in SCHEMES) return
        val service = uri.host?.removeSuffix("-auth")?.takeIf { it in SERVICES } ?: return
        val tracker = tracker(service) ?: return
        val params = (uri.rawQuery?.takeIf { it.isNotBlank() } ?: uri.rawFragment.orEmpty())
            .split("&")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=", limit = 2).map { part -> URLDecoder.decode(part, Charsets.UTF_8) }
                parts[0] to parts.getOrNull(1)
            }

        // Comick and MangaBaka check their own state, as in Mihon; the others' is PendingLogins'
        val state = params["state"].takeIf { service in STATE_CHECKED_HERE }
        when (pendingLogins.take(service, state)) {
            PendingLogins.Take.None -> {
                logcat(LogPriority.WARN) { "A $service login redirect came back with no login pending" }
                notify(tracker, context.stringResource(MR.strings.desktop_tracker_login_expired), isError = true)
                return
            }
            PendingLogins.Take.WrongState -> {
                logcat(LogPriority.WARN) { "A $service login redirect came back with the wrong state" }
                return
            }
            PendingLogins.Take.Ok -> {}
        }

        params["error"]?.let { error ->
            notify(tracker, params["error_description"] ?: error, isError = true)
            return
        }

        try {
            login(service, params)
        } catch (e: ComickMissingScopesException) {
            notify(tracker, e.message.orEmpty(), isError = true)
            return
        }

        if (tracker.isLoggedIn) {
            notify(tracker, context.stringResource(MR.strings.desktop_tracker_login_done), isError = false)
        } else {
            notify(tracker, context.stringResource(MR.strings.desktop_tracker_login_failed), isError = true)
        }
    }

    /** Mihon's handlers: each tracker's login logs out on any failure, so the outcome is read from isLoggedIn. */
    private suspend fun login(service: String, params: Map<String, String?>) {
        when (service) {
            "anilist" -> params["access_token"]?.let { trackerManager.aniList!!.login(it) }
            "myanimelist" -> params["code"]?.let { trackerManager.myAnimeList!!.login(it) }
            "shikimori" -> params["code"]?.let { trackerManager.shikimori!!.login(it) }
            "bangumi" -> params["code"]?.let { trackerManager.bangumi!!.login(it) }
            "hikka" -> params["reference"]?.let { trackerManager.hikka!!.login(it) }
            "comick" -> {
                val state = params["state"] ?: return
                val code = params["code"] ?: return
                if (trackerManager.comick!!.verifyOAuthState(state)) trackerManager.comick!!.login(code)
            }
            "mangabaka" -> {
                val state = params["state"] ?: return
                val code = params["code"] ?: return
                if (trackerManager.mangaBaka!!.verifyOAuthState(state)) trackerManager.mangaBaka!!.login(code)
            }
        }
    }

    private fun tracker(service: String): Tracker? = when (service) {
        "anilist" -> trackerManager.aniList
        "myanimelist" -> trackerManager.myAnimeList
        "shikimori" -> trackerManager.shikimori
        "bangumi" -> trackerManager.bangumi
        "comick" -> trackerManager.comick
        "mangabaka" -> trackerManager.mangaBaka
        "hikka" -> trackerManager.hikka
        else -> null
    }

    private fun notify(tracker: Tracker, message: String, isError: Boolean) {
        TrayNotifier.send(Notice(tracker.name, message, isError = isError, opensUpdates = false))
    }

    companion object {
        val SCHEMES = setOf("mangareader", "app.mangareader")
        val SERVICES = setOf("anilist", "myanimelist", "shikimori", "bangumi", "comick", "mangabaka", "hikka")
        private val STATE_CHECKED_HERE = setOf("myanimelist", "shikimori", "bangumi")
    }
}
```

Strings nuevos, en base y es (bloque `<!-- Desktop -->`):

```xml
    <string name="desktop_tracker_login_done">Signed in</string>
    <string name="desktop_tracker_login_failed">Couldn\'t sign in</string>
    <string name="desktop_tracker_login_expired">That sign-in was opened before the app closed; start it again from Settings › Tracking</string>
    <string name="desktop_tracker_protocol_failed">The browser can\'t come back to the app, so the services that sign in through it won\'t work</string>
```

```xml
    <string name="desktop_tracker_login_done">Sesión iniciada</string>
    <string name="desktop_tracker_login_failed">No se pudo iniciar sesión</string>
    <string name="desktop_tracker_login_expired">Ese inicio de sesión se abrió antes de cerrar la app; vuelve a empezarlo desde Ajustes › Seguimiento</string>
    <string name="desktop_tracker_protocol_failed">El navegador no puede volver a la app, así que los servicios que inician sesión con él no funcionarán</string>
```

`AppGraph`: `val trackLoginHandler: TrackLoginHandler` y `val pendingLogins: PendingLogins`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop-app i18n
git commit -m "feat(desktop-app): finish OAuth logins from the mangareader:// redirect, checking the pending login and its state

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: protocolo de Windows, entrada de URLs y Ajustes

**Files:**
- Create:
  - `desktop-app/src/main/kotlin/mihon/desktop/app/track/ProtocolRegistrar.kt`;
  - `desktop-app/src/main/kotlin/mihon/desktop/app/track/LoginUrls.kt`.
- Modify:
  - `mihon/desktop/app/Main.kt`;
  - `eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt`.
- Test:
  - `desktop-app/src/test/kotlin/mihon/desktop/app/track/ProtocolRegistrarTest.kt`;
  - `desktop-app/src/test/kotlin/mihon/desktop/app/track/LoginUrlsTest.kt`;
  - `desktop-app/src/test/kotlin/mihon/desktop/app/track/OAuthSettingsTest.kt`.

**Interfaces:**
- Consumes: de la Task 2, `TrackLoginHandler.handle(url)` y `authorizationUrl(service)`.
- Produces:
  - `ProtocolRegistrar`: `fun register(root: String = "Software\\Classes"): Boolean` y `fun command(executable: String, arguments: List<String>): String`; además `@Volatile var registered: Boolean?`.
  - `LoginUrls.route(args: List<String>, handle: (String) -> Unit)`.

- [ ] **Step 1: Escribir los tests**

`ProtocolRegistrarTest.kt`:

```kotlin
package mihon.desktop.app.track

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ProtocolRegistrarTest {
    @Test
    fun `the command quotes paths with spaces and accents and passes the url as %1`() {
        ProtocolRegistrar.command("C:\\Users\\José Pérez\\MangaReader\\MangaReader.exe", emptyList()) shouldBe
            "\"C:\\Users\\José Pérez\\MangaReader\\MangaReader.exe\" \"%1\""
        ProtocolRegistrar.command("C:\\jdk\\bin\\java.exe", listOf("-cp", "C:\\a b\\x.jar", "MainKt")) shouldBe
            "\"C:\\jdk\\bin\\java.exe\" -cp \"C:\\a b\\x.jar\" MainKt \"%1\""
    }

    @Test
    fun `both schemes are registered as url protocols under the given root`() {
        val root = "Software\\MangaReaderTest-${System.nanoTime()}"
        try {
            ProtocolRegistrar.register(root) shouldBe true

            listOf("mangareader", "app.mangareader").forEach { scheme ->
                val key = "$root\\$scheme"
                Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, "URL Protocol") shouldBe true
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "$key\\shell\\open\\command", "")
                    .endsWith("\"%1\"") shouldBe true
            }
        } finally {
            Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, root, true)
        }
    }
}
```

Si `registryDeleteKey(…, recursive)` no existe en esta versión de JNA, se borra en orden inverso: `shell\open\command`, `shell\open`, `shell`, el esquema y la raíz.

`LoginUrlsTest.kt`:

```kotlin
package mihon.desktop.app.track

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LoginUrlsTest {
    @Test
    fun `only the login redirects among the arguments go to the handler`() {
        val handled = mutableListOf<String>()

        LoginUrls.route(
            listOf("--focus", "mangareader://anilist-auth#access_token=t", "app.mangareader://comick-auth?code=c", "x"),
        ) { handled += it }

        handled shouldBe listOf("mangareader://anilist-auth#access_token=t", "app.mangareader://comick-auth?code=c")
    }
}
```

`OAuthSettingsTest.kt`:

```kotlin
package mihon.desktop.app.track

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.more.settings.screen.SettingsTrackingScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import mihon.desktop.app.main.AppProviders
import mihon.desktop.app.testing.startTestApp
import mihon.desktop.app.testing.str
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

@OptIn(ExperimentalTestApi::class)
class OAuthSettingsTest {
    private val graph = TrackerKeys.withTestKeys { startTestApp() }

    @Test
    fun `with keys Settings › Tracking lists the OAuth trackers, and warns when the protocol isn't registered`() =
        runComposeUiTest {
            ProtocolRegistrar.registered = false
            setContent {
                AppProviders(graph) { Navigator(SettingsTrackingScreen) { DefaultNavigatorScreenTransition(it) } }
            }

            waitUntil(timeoutMillis = 15_000) { onAllNodes(hasText("MyAnimeList")).fetchSemanticsNodes().isNotEmpty() }
            listOf("AniList", "Shikimori", "Bangumi", "Hikka", "Comick", "MangaBaka").forEach {
                onNode(hasScrollToNodeAction()).performScrollToNode(hasText(it, substring = true))
            }
            onNode(hasScrollToNodeAction()).performScrollToNode(
                hasText(str(MR.strings.desktop_tracker_protocol_failed), substring = true),
            )
            ProtocolRegistrar.registered = null
        }
}
```

- [ ] **Step 2: Comprobar que fallan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.ProtocolRegistrarTest" --tests "mihon.desktop.app.track.LoginUrlsTest" --tests "mihon.desktop.app.track.OAuthSettingsTest"`
Expected: FAIL de compilación (`ProtocolRegistrar` y `LoginUrls` no existen; Ajustes no tiene los OAuth).

- [ ] **Step 3: Implementación**

`ProtocolRegistrar.kt`:

```kotlin
package mihon.desktop.app.track

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Registers mangareader:// and app.mangareader:// for this user (HKCU, no admin rights) so the browser can hand an
 * OAuth redirect back to the app (spec 5b-2 §1). Written on every start, so it points at the app that ran last: the
 * exe once packaged, the JVM with its classpath during development.
 */
object ProtocolRegistrar {
    /** Null until [register] runs; false makes Settings › Tracking warn (spec 5b-2 §4). */
    @Volatile
    var registered: Boolean? = null

    fun register(root: String = "Software\\Classes"): Boolean {
        val info = ProcessHandle.current().info()
        val executable = info.command().orElse(null) ?: return false.also { registered = it }
        val command = command(executable, info.arguments().map { it.toList() }.orElse(emptyList()))
        return try {
            TrackLoginHandler.SCHEMES.forEach { scheme ->
                val key = "$root\\$scheme"
                Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, "$key\\shell\\open\\command")
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, "", "URL:MangaReader")
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, "URL Protocol", "")
                Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, "$key\\shell\\open\\command", "", command)
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Couldn't register the mangareader:// protocol" }
            false
        }.also { registered = it }
    }

    fun command(executable: String, arguments: List<String>): String =
        (listOf(executable) + arguments).joinToString(" ") { quote(it) } + " \"%1\""

    private fun quote(part: String) = if (part.any { it == ' ' || it == '\t' }) "\"$part\"" else part
}
```

`command("C:\\jdk\\bin\\java.exe", …)` deja `C:\jdk\bin\java.exe` sin comillas porque no tiene espacios. El test espera comillas en el ejecutable siempre, así que `quote` se aplica al ejecutable sin condición:

```kotlin
    fun command(executable: String, arguments: List<String>): String =
        (listOf("\"$executable\"") + arguments.map(::quote)).joinToString(" ") + " \"%1\""
```

`LoginUrls.kt`:

```kotlin
package mihon.desktop.app.track

/** Picks the OAuth redirects out of the app's arguments: the first start's, or a second instance's (spec 5b-2 §3 A). */
object LoginUrls {
    fun route(args: List<String>, handle: (String) -> Unit) {
        args.filter { arg -> TrackLoginHandler.SCHEMES.any { arg.startsWith("$it://") } }.forEach(handle)
    }
}
```

`Main.kt`:
- Antes del `listen`, un canal para las URLs:

```kotlin
    // OAuth redirects (spec 5b-2 §3 A): a second instance's, and this start's own, wait here until the graph exists
    val loginUrls = Channel<String>(Channel.UNLIMITED)
    LoginUrls.route(args.toList()) { loginUrls.trySend(it) }
```

- El `instance.listen(...) { bringToFront.tryEmit(Unit) }` pasa a:

```kotlin
    instance.listen(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { forwarded ->
        LoginUrls.route(forwarded) { loginUrls.trySend(it) }
        bringToFront.tryEmit(Unit)
    }
```

- Tras `graph.startStorage()`:

```kotlin
    ProtocolRegistrar.register()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        for (url in loginUrls) graph.trackLoginHandler.handle(url)
    }
```

  Con los imports `kotlinx.coroutines.channels.Channel`, `mihon.desktop.app.track.LoginUrls` y `mihon.desktop.app.track.ProtocolRegistrar`.

`SettingsTrackingScreen.kt`:
- Antes de Kitsu, en `preferenceItems`, vuelven los 7 `TrackerPreference` de Mihon, en su orden: MangaBaka, MyAnimeList, AniList, (Kitsu), (MangaUpdates), Shikimori, Bangumi, Hikka, Comick. Cada uno, solo si su tracker no es `null`:

```kotlin
                    // desktop: Mihon's OAuth trackers, each with its key (spec 5b-2 §1); login through PendingLogins
                    *listOfNotNull(
                        trackerManager.mangaBaka?.let { oauthTracker(it, "mangabaka", loginHandler, context) },
                        trackerManager.myAnimeList?.let { oauthTracker(it, "myanimelist", loginHandler, context) },
                        trackerManager.aniList?.let { oauthTracker(it, "anilist", loginHandler, context) },
                    ).toTypedArray(),
```

  y, tras MangaUpdates, `shikimori`, `bangumi`, `hikka` y `comick` del mismo modo. El orden exacto es el de Mihon y se copia de su `SettingsTrackingScreen`.
- La función privada:

```kotlin
    private fun oauthTracker(
        tracker: Tracker,
        service: String,
        loginHandler: TrackLoginHandler,
        context: Context,
        onLogout: () -> Unit,
    ) = Preference.PreferenceItem.TrackerPreference(
        tracker = tracker,
        login = { loginHandler.authorizationUrl(service)?.let { context.openInBrowser(it, forceDefaultBrowser = true) } },
        logout = onLogout,
    )
```

  Con `onLogout = { dialog = LogoutDialog(tracker) }` en cada llamada, y `val loginHandler = remember { context.appGraph.trackLoginHandler }`.
- Si `ProtocolRegistrar.registered == false`, se añade `Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.desktop_tracker_protocol_failed))` al final del grupo de servicios.
- `AppGraph` y `TrackerManager` ya los da la Task 2. Se quita el comentario `// desktop: the OAuth trackers arrive in 5b-2`.

- [ ] **Step 4: Comprobar que pasan**

Run: `./gradlew :desktop-app:test --tests "mihon.desktop.app.track.*" --tests "mihon.desktop.app.settings.*" --tests "mihon.desktop.app.SpanishStringsTest"`
Expected: PASS.

Run: `./gradlew :desktop-core:test --tests "mihon.desktop.core.SingleInstanceTest"`
Expected: PASS (el reenvío de argumentos de la segunda instancia, desviación 4).

- [ ] **Step 5: Commit**

```bash
git add desktop-app
git commit -m "feat(desktop-app): mangareader:// registered for this user, redirects routed to the login handler, OAuth in Settings › Tracking

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: verificación completa y prueba manual

**Files:**
- Modify: ninguno, salvo los arreglos que salgan.

- [ ] **Step 1: Suite y checks**

Run: `./gradlew test spotlessCheck`
Expected: BUILD SUCCESSFUL, con unos 490 tests.

Run: `bash scripts/check-no-android.sh` y `bash scripts/check-licenses.sh`
Expected: OK.

- [ ] **Step 2: Sin referencias pendientes**

Run: `grep -rn "5b-2\|mihon://\|app\.mihon://" desktop-app/src/main --include=*.kt`
Expected:
- ningún `mihon://` ni `app.mihon://`;
- de `5b-2`, solo las citas a la spec (`spec 5b-2 §…`), no notas de "llega en 5b-2".

- [ ] **Step 3: Arranque real**

Run: `./gradlew :desktop-app:run` con `MANGAREADER_HOME` temporal, durante 2 minutos.
Expected:
- sin errores de Metro, Apollo ni `ProtocolRegistrar` en el log;
- `reg query HKCU\Software\Classes\mangareader\shell\open\command` muestra el comando de la JVM con `"%1"`.

- [ ] **Step 4: Commit de los arreglos (si los hay)**

```bash
git add -A desktop-app
git commit -m "chore: tidy the last 5b-2 notes

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 5: Para el usuario: registrar las apps y prueba manual**

Registro, una vez:
1. Copia `tracker-keys.example.properties` a `tracker-keys.properties`.
2. Registra una app en cada servicio con el redirect `mangareader://<servicio>-auth` (Comick: `app.mangareader://comick-auth`):

   | Servicio | Dónde |
   |---|---|
   | AniList | anilist.co/settings/developer |
   | MyAnimeList | myanimelist.net/apiconfig, tipo "other" |
   | Shikimori | shikimori.io/oauth/applications |
   | Bangumi | bgm.tv/dev/app |
   | Comick | su portal de desarrolladores |
   | MangaBaka | su portal de desarrolladores |
   | Hikka | pidiendo un cliente a sus desarrolladores (reference y secret) |

3. Pon los IDs (y los secrets de Shikimori, Bangumi y Hikka) en el fichero.
4. Vuelve a compilar.

Prueba manual (spec §5):
1. Iniciar sesión en cada servicio desde Ajustes › Seguimiento, con el navegador volviendo a la app.
2. Cerrar la app a mitad de un login, volver por el redirect y ver el aviso de "vuelve a empezarlo".
3. Vincular un manga, leer un capítulo y verlo en la web del tracker.
4. Cancelar un login en el navegador y ver el aviso.
