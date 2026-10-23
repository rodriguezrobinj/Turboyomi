package mihon.desktop.app.local

import mihon.app.di.AppGraph

/**
 * Starts the storage as Android does with the app: StorageManager listens for location changes from now on (created on
 * first use, a change made before then wouldn't create the new folder's local/, downloads/ and autobackup/), and the
 * default folder exists while it's the chosen one.
 */
fun AppGraph.startStorage() {
    appDirs.createStorageIfChosen(storagePreferences.baseStorageDirectory.get())
    storageManager
}
