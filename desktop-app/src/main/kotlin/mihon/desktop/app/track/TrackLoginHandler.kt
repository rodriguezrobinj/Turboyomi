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
                // Like Mihon's Uri.decode: percent-escapes only, a literal + stays a +
                val parts = it.split("=", limit = 2)
                    .map { part -> URLDecoder.decode(part.replace("+", "%2B"), Charsets.UTF_8) }
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
