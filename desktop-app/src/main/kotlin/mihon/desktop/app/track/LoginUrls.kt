package mihon.desktop.app.track

/** Picks the OAuth redirects out of the app's arguments: the first start's, or a second instance's (spec 5b-2 §3 A). */
object LoginUrls {
    fun route(args: List<String>, handle: (String) -> Unit) {
        args.filter { arg -> TrackLoginHandler.SCHEMES.any { arg.startsWith("$it://") } }.forEach(handle)
    }
}
