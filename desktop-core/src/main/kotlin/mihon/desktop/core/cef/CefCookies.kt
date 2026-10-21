package mihon.desktop.core.cef

import org.cef.network.CefCookie
import java.net.HttpCookie
import java.net.URI
import java.util.Date

/** A cookie of the app's store as CEF takes it; one without a domain is a host cookie (CEF: an empty domain). */
fun HttpCookie.toCefCookie(now: Date = Date()): CefCookie {
    val expires = if (maxAge >= 0) Date(now.time + maxAge * 1000) else null
    return CefCookie(name, value, domain.orEmpty(), path ?: "/", secure, isHttpOnly, now, now, expires != null, expires)
}

/** A CEF cookie for the app's store; one without an expiry stays a session cookie. */
fun CefCookie.toHttpCookie(now: Date = Date()): HttpCookie = HttpCookie(name, value).also {
    it.domain = domain.takeIf(String::isNotEmpty)
    it.path = path
    it.secure = secure
    it.isHttpOnly = httponly
    it.maxAge = if (hasExpires && expires != null) maxOf(0L, (expires.time - now.time) / 1000) else -1L
}

/** The address CEF's setCookie wants for [cookie], which came from [uri]: its scheme, and its domain or host. */
fun cookieUrl(uri: URI?, cookie: HttpCookie): String {
    val scheme = if (cookie.secure) "https" else uri?.scheme ?: "https"
    val host = cookie.domain?.trimStart('.') ?: uri?.host.orEmpty()
    return "$scheme://$host${cookie.path ?: "/"}"
}

/** The address a CEF cookie belongs to, for the app's store (which matches cookies by domain). */
fun CefCookie.url(): URI = URI(if (secure) "https" else "http", domain.trimStart('.'), path.ifEmpty { "/" }, null)
