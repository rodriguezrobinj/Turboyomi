package eu.kanade.tachiyomi.data.backup

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.source.service.SourceManager
import java.io.File

@Inject
class BackupFileValidator(
    private val sourceManager: SourceManager,
    private val trackerManager: TrackerManager,
    private val backupDecoder: BackupDecoder,
) {

    /**
     * Checks for critical backup file data.
     *
     * @return List of missing sources or missing trackers.
     */
    suspend fun validate(file: File): Results {
        val trackerIds = mutableSetOf<Int>()
        val backup = try {
            backupDecoder.decodeEntries(file).collect { manga ->
                manga.tracking.mapTo(trackerIds) { it.syncId }
            }
            backupDecoder.decodeMetadata(file).backup
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }

        val sources = backup.backupSources.associate { it.sourceId to it.name }
        val missingSources = sources
            .filterKeys { sourceManager.get(it) == null }
            .map { (id, name) ->
                // Some backups store the id as the name, and sources without a stub were backed up with no name
                if (name.isBlank() || name.toLongOrNull() != null) {
                    sourceManager.getOrStub(id).toString()
                } else {
                    name
                }
            }
            .distinct()
            .sorted()

        // desktop: trackers not in TrackerManager (an OAuth one without its key, spec 5b-2 §1) are left out, as Mihon
        // leaves out unknown ids
        val missingTrackers = trackerIds
            .mapNotNull { trackerManager.get(it.toLong()) }
            .filter { !it.isLoggedIn }
            .map { it.name }
            .sorted()

        return Results(missingSources, missingTrackers)
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
    )
}
