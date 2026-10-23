package mihon.desktop.app.testing

import eu.kanade.tachiyomi.data.backup.models.Backup
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.sink
import java.io.File

/** Backups written straight from Mihon's models, as Mihon Android writes them. */
object TestBackups {
    fun write(file: File, backup: Backup) {
        file.outputStream().sink().gzip().buffer().use {
            it.write(ProtoBuf.encodeToByteArray(Backup.serializer(), backup))
        }
    }
}
