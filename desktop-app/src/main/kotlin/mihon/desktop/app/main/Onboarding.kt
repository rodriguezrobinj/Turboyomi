package mihon.desktop.app.main

import mihon.app.di.AppGraph

/**
 * The onboarding is new in 11e: who already has a library or categories knows the app and doesn't get it. Only
 * decides once, while the preference was never saved.
 */
suspend fun skipOnboardingForExistingUsers(graph: AppGraph) {
    val shown = graph.basePreferences.shownOnboardingFlow
    if (shown.isSet()) return
    if (graph.getFavorites.await().isNotEmpty() || graph.getCategories.await().any { !it.isSystemCategory }) {
        shown.set(true)
    }
}
