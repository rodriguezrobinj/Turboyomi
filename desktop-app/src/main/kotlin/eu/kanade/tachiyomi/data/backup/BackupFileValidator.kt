package eu.kanade.tachiyomi.data.backup

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import tachiyomi.domain.source.service.SourceManager
import java.io.File

@Inject
class BackupFileValidator(
    private val sourceManager: SourceManager,
    private val backupDecoder: BackupDecoder,
) {

    /**
     * Checks for critical backup file data.
     *
     * @return List of missing sources or missing trackers.
     */
    suspend fun validate(file: File): Results {
        val backup = try {
            // desktop: the entries are still read, so a broken one fails here, before anything is restored
            backupDecoder.decodeEntries(file).collect {}
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

        // desktop: there are no trackers until subproject 5b, so none can be missing
        return Results(missingSources, missingTrackers = emptyList())
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
    )
}
