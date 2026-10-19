package mihon.desktop.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * One process per user. The first one holds a file lock and listens on a loopback port; later ones send it their
 * arguments (OAuth redirects, "focus the window") and exit. The port goes in a separate file because Windows
 * file locks block other processes from reading the locked file.
 */
class SingleInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val server: ServerSocket,
) : AutoCloseable {

    sealed interface Result {
        data class Primary(val instance: SingleInstance) : Result
        data object Forwarded : Result
    }

    fun listen(scope: CoroutineScope, onArgs: (List<String>) -> Unit): Job = scope.launch {
        while (isActive && !server.isClosed) {
            val socket = runCatching { runInterruptible { server.accept() } }.getOrNull() ?: break
            socket.use { onArgs(it.getInputStream().bufferedReader().readLines()) }
        }
    }

    override fun close() {
        server.close()
        lock.release()
        channel.close()
    }

    companion object {
        fun acquire(lockFile: Path, args: List<String>): Result {
            val portFile = lockFile.resolveSibling("${lockFile.fileName}.port")
            val channel = FileChannel.open(lockFile, CREATE, WRITE)
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                forward(portFile.readText().trim().toInt(), args)
                return Result.Forwarded
            }
            val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            portFile.writeText(server.localPort.toString())
            return Result.Primary(SingleInstance(channel, lock, server))
        }

        private fun forward(port: Int, args: List<String>) {
            Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                socket.getOutputStream().bufferedWriter().use { writer -> args.forEach { writer.appendLine(it) } }
            }
        }
    }
}
