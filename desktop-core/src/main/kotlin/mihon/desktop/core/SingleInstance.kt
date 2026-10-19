package mihon.desktop.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.SecureRandom
import java.util.HexFormat
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * One process per user. The first one holds a file lock and listens on a loopback port; later ones send it their
 * arguments (OAuth redirects, "focus the window") and exit. The port goes in a separate file because Windows
 * file locks block other processes from reading the locked file.
 *
 * Any local process can reach a loopback port, so messages must start with a random token that only readers of
 * the port file (in the user's own %APPDATA%) know.
 */
class SingleInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val server: ServerSocket,
    private val token: String,
    private val portFile: Path,
) : AutoCloseable {

    sealed interface Result {
        data class Primary(val instance: SingleInstance) : Result
        data object Forwarded : Result

        /** Another instance holds the lock but could not be reached (still starting, or its port file is gone). */
        data object Unreachable : Result
    }

    fun listen(scope: CoroutineScope, onArgs: (List<String>) -> Unit): Job = scope.launch {
        while (isActive && !server.isClosed) {
            val socket = runCatching { runInterruptible { server.accept() } }.getOrNull() ?: break
            runCatching {
                socket.use {
                    // A client that connects and never closes would otherwise stall every later forward
                    it.soTimeout = READ_TIMEOUT_MS
                    val lines = it.getInputStream().bufferedReader().readLines()
                    if (lines.firstOrNull() == token) onArgs(lines.drop(1))
                }
            }.onFailure { logcat(LogPriority.WARN, it) { "Ignoring a single-instance message" } }
        }
    }

    override fun close() {
        server.close()
        portFile.deleteIfExists()
        lock.release()
        channel.close()
    }

    companion object {
        private const val READ_TIMEOUT_MS = 2_000
        private const val FORWARD_ATTEMPTS = 20
        private const val FORWARD_RETRY_MS = 100L

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
                // The first instance writes its port file right after taking the lock: give it a moment
                repeat(FORWARD_ATTEMPTS) {
                    if (runCatching { forward(portFile, args) }.isSuccess) return Result.Forwarded
                    Thread.sleep(FORWARD_RETRY_MS)
                }
                return Result.Unreachable
            }
            val token = HexFormat.of().formatHex(ByteArray(16).also(SecureRandom()::nextBytes))
            val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
            portFile.writeText("${server.localPort}:$token")
            return Result.Primary(SingleInstance(channel, lock, server, token, portFile))
        }

        private fun forward(portFile: Path, args: List<String>) {
            val (port, token) = portFile.readText().trim().split(':', limit = 2)
            Socket(InetAddress.getLoopbackAddress(), port.toInt()).use { socket ->
                socket.getOutputStream().bufferedWriter().use { writer ->
                    writer.appendLine(token)
                    args.forEach { writer.appendLine(it) }
                }
            }
        }
    }
}
