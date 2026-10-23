package com.monologue.music.torrent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.TorrentAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.alert_category_t
import org.libtorrent4j.swig.settings_pack
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class TorrentStreamState {
    data object Idle : TorrentStreamState()
    data object Connecting : TorrentStreamState()
    data object FetchingMetadata : TorrentStreamState()
    data class Buffering(val progress: Float, val peers: Int, val downloadSpeed: Long) : TorrentStreamState()
    data class Ready(
        val filePath: String,
        val streamUrl: String = "",
        val fileIndex: Int = 0,
        val peers: Int = 0,
        val downloadSpeed: Long = 0,
        val uploadSpeed: Long = 0,
    ) : TorrentStreamState()
    data class Seeding(val filePath: String, val peers: Int, val uploadSpeed: Long, val ratio: Float) : TorrentStreamState()
    data class Error(val message: String) : TorrentStreamState()
}

data class TorrentStatus(
    val peers: Int = 0,
    val downloadSpeed: Long = 0,
    val uploadSpeed: Long = 0,
    val progress: Float = 0f,
    val ratio: Float = 0f,
    val state: TorrentStreamState = TorrentStreamState.Idle,
)

@Singleton
class TorrentStreamEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val BUFFER_PIECES_AHEAD = 5
        const val STREAM_PORT = 19527

        private val AUDIO_EXTENSIONS = listOf(
            ".m4b", ".mp3", ".m4a", ".aac", ".flac", ".ogg", ".oga", ".opus", ".wav", ".wma",
        )
    }

    data class StreamFileInfo(
        val fileIndex: Int,
        val chapterIndex: Int,
        val fileName: String,
        val fileSize: Long,
        val fileOffset: Long,
        val pieceLength: Int,
        val firstPiece: Int,
        val lastPiece: Int,
        val numPieces: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var sessionManager: SessionManager? = null
    @Volatile
    private var currentHandle: TorrentHandle? = null
    @Volatile
    private var targetChapterIndex: Int = 0
    @Volatile
    private var targetFileName: String? = null
    @Volatile
    private var activeInfoHash: Sha1Hash? = null
    @Volatile
    private var currentFileInfo: StreamFileInfo? = null

    private var serverSocket: ServerSocket? = null
    var serverPort: Int = 0
        private set

    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val activeWindowLock = Any()
    private val activeWindowPieces = mutableSetOf<Int>()

    private val _streamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    val streamState: StateFlow<TorrentStreamState> = _streamState

    private val _status = MutableStateFlow(TorrentStatus())
    val status: StateFlow<TorrentStatus> = _status

    private val saveDir: File
        get() = File(context.getExternalFilesDir(null), "audiobooks").also { it.mkdirs() }

    @Volatile
    private var isStarted = false

    private val startMutex = kotlinx.coroutines.sync.Mutex()
    private val streamMutex = Mutex()

    private suspend fun ensureStarted() = withContext(Dispatchers.IO) {
        if (isStarted && sessionManager != null) return@withContext
        startMutex.withLock {
            if (isStarted && sessionManager != null) return@withLock
            try {
                val sp = SettingsPack()
                sp.setString(settings_pack.string_types.user_agent.swigValue(), "Monologue/1.0 libtorrent")
                sp.setInteger(settings_pack.int_types.alert_mask.swigValue(), alert_category_t.all().to_int())
                sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)

                val sm = SessionManager(false)
                sm.addListener(alertListener)
                sm.start(SessionParams(sp))
                sessionManager = sm
                isStarted = true

                startStreamServer()
                Timber.d("TorrentStreamEngine started on port $serverPort")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start torrent engine")
                _streamState.value = TorrentStreamState.Error("Failed to start torrent engine: ${e.message}")
            }
        }
    }

    fun start() {
        if (isStarted && sessionManager != null) return
        scope.launch {
            ensureStarted()
        }
    }

    fun stop() {
        scope.launch {
            try {
                stopStreamServer()
                closeActiveStreamClients()
                currentHandle?.let { saveResumeData(it) }
                sessionManager?.stop()
                sessionManager = null
                currentHandle = null
                currentFileInfo = null
                activeInfoHash = null
                isStarted = false
                synchronized(activeWindowLock) { activeWindowPieces.clear() }
                _streamState.value = TorrentStreamState.Idle
                _status.value = TorrentStatus()
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop torrent engine")
            }
        }
    }

    suspend fun streamFromMagnet(
        magnetUri: String,
        chapterIndex: Int = 0,
        fileName: String? = null,
    ): String? {
        ensureStarted()
        val sm = sessionManager ?: run {
            _streamState.value = TorrentStreamState.Error("Engine not started")
            return null
        }
        val infoHash = runCatching {
            AddTorrentParams.parseMagnetUri(magnetUri).infoHashes.best
        }.getOrNull()

        return withContext(Dispatchers.IO) {
            streamMutex.withLock {
                try {
                    val previousHash = activeInfoHash
                    stopCurrentStream()
                    // Filter alerts from the previous torrent as early as possible.
                    activeInfoHash = infoHash

                    if (infoHash != null && previousHash != null && previousHash != infoHash) {
                        runCatching {
                            sm.find(previousHash)?.let { sm.remove(it) }
                        }
                    }

                    targetChapterIndex = chapterIndex
                    targetFileName = fileName
                    _streamState.value = TorrentStreamState.Connecting

                    sm.download(magnetUri, saveDir, TorrentFlags.DEFAULT_DONT_DOWNLOAD)
                    _streamState.value = TorrentStreamState.FetchingMetadata

                    // Reusing a torrent that is already in the session doesn't emit add/metadata
                    // alerts again, so initialise the new chapter explicitly.
                    val handle = infoHash?.let { hash ->
                        runCatching { sm.find(hash) }.getOrNull()
                    }
                    if (handle != null && handle.isValid) {
                        currentHandle = handle
                        if (getTorrentInfoSafely(handle) != null) {
                            prioritizeFile(handle, targetChapterIndex, targetFileName)
                            checkStreamReadiness(handle)
                            updateBufferingIfNeeded(handle)
                        }
                    }
                    null
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start stream")
                    _streamState.value = TorrentStreamState.Error("Failed to start stream: ${e.message}")
                    null
                }
            }
        }
    }

    fun stopCurrentStream() {
        val handle = currentHandle
        activeInfoHash = null
        currentFileInfo = null
        currentHandle = null
        closeActiveStreamClients()
        handle?.let { saveResumeData(it) }
        synchronized(activeWindowLock) { activeWindowPieces.clear() }
        _streamState.value = TorrentStreamState.Idle
    }

    fun setTargetFile(chapterIndex: Int, fileName: String? = null) {
        targetChapterIndex = chapterIndex
        targetFileName = fileName
        val handle = currentHandle ?: activeInfoHash?.let { sessionManager?.find(it) }
        handle?.let {
            prioritizeFile(it, chapterIndex, fileName)
            checkStreamReadiness(it)
        }
    }

    private fun getTorrentInfoSafely(handle: TorrentHandle?): TorrentInfo? {
        if (handle == null || !handle.isValid) return null
        val status = try { handle.status() } catch (_: Exception) { return null }
        if (!status.hasMetadata()) return null
        return try {
            handle.torrentFile()
        } catch (_: Exception) {
            null
        }
    }

    private fun isAudioFile(path: String): Boolean {
        val lower = path.lowercase()
        return AUDIO_EXTENSIONS.any { lower.endsWith(it) }
    }

    /**
     * The UI only knows the audio files listed on the website, sorted by name. The torrent
     * itself may contain artwork or other files in a different order, so the chapter index
     * cannot be used as a torrent file index directly.
     */
    private fun resolveFileIndex(ti: TorrentInfo, chapterIndex: Int, fileName: String?): Int? {
        val fs = ti.files()
        val numFiles = ti.numFiles()
        if (numFiles <= 0) return null

        val targetName = fileName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        if (targetName != null) {
            for (i in 0 until numFiles) {
                val path = fs.filePath(i)
                val baseName = path.substringAfterLast('/').substringAfterLast('\\')
                if (baseName.equals(targetName, ignoreCase = true)) return i
            }
            for (i in 0 until numFiles) {
                if (fs.filePath(i).endsWith(targetName, ignoreCase = true)) return i
            }
        }

        val audioIndices = (0 until numFiles).filter { isAudioFile(fs.filePath(it)) }
        val candidates = audioIndices.ifEmpty { (0 until numFiles).toList() }
        return candidates.getOrNull(chapterIndex) ?: candidates.firstOrNull()
    }

    /**
     * Ignores all non-target files and unplayed pieces; pins container metadata (head + tail)
     * and initializes the sliding download window at the start of the audio file.
     */
    private fun prioritizeFile(handle: TorrentHandle, chapterIndex: Int, fileName: String?) {
        if (!handle.isValid) return
        val ti = getTorrentInfoSafely(handle) ?: return
        val fileIndex = resolveFileIndex(ti, chapterIndex, fileName) ?: return
        val fs = ti.files()
        val numFiles = ti.numFiles()
        if (fileIndex !in 0 until numFiles) return

        val firstPiece = fs.pieceIndexAtFile(fileIndex)
        val lastPiece = fs.lastPieceIndexAtFile(fileIndex)
        val pieceLength = fs.pieceLength()
        val fileSize = fs.fileSize(fileIndex)
        val fileOffset = fs.fileOffset(fileIndex)
        val torrentFileName = fs.filePath(fileIndex)
        val numPieces = ti.numPieces()

        currentFileInfo = StreamFileInfo(
            fileIndex = fileIndex,
            chapterIndex = chapterIndex,
            fileName = torrentFileName,
            fileSize = fileSize,
            fileOffset = fileOffset,
            pieceLength = pieceLength,
            firstPiece = firstPiece,
            lastPiece = lastPiece,
            numPieces = numPieces,
        )

        // Ignore all other files in multi-file torrent to save swarm bandwidth and disk
        val filePriorities = Array(numFiles) { i ->
            if (i == fileIndex) Priority.TOP_PRIORITY else Priority.IGNORE
        }
        handle.prioritizeFiles(filePriorities)

        // Ignore all pieces in the torrent by default
        val allIgnore = Array(numPieces) { Priority.IGNORE }
        handle.prioritizePieces(allIgnore)

        // Pin container metadata: head pieces & tail piece for MP3/ID3 and MP4/moov atoms
        handle.piecePriority(firstPiece, Priority.TOP_PRIORITY)
        handle.setPieceDeadline(firstPiece, 1000)
        if (firstPiece + 1 <= lastPiece) {
            handle.piecePriority(firstPiece + 1, Priority.TOP_PRIORITY)
            handle.setPieceDeadline(firstPiece + 1, 2000)
        }
        handle.piecePriority(lastPiece, Priority.TOP_PRIORITY)
        handle.setPieceDeadline(lastPiece, 3000)

        // Open active sliding window at start of file
        synchronized(activeWindowLock) { activeWindowPieces.clear() }
        updateSlidingWindow(handle, fileIndex, startByte = 0L)
    }

    /**
     * Torrent pieces span file boundaries, so a file offset must be translated through the
     * file's offset in the torrent rather than divided by the piece length directly.
     */
    private fun pieceIndexAtFileOffset(info: StreamFileInfo, filePos: Long): Int {
        val absoluteOffset = info.fileOffset + filePos.coerceAtLeast(0L)
        return (absoluteOffset / info.pieceLength).toInt().coerceIn(info.firstPiece, info.lastPiece)
    }

    /**
     * Shifts the active sliding window to the requested byte position, demoting pieces that fell behind.
     */
    fun updateSlidingWindow(handle: TorrentHandle, fileIndex: Int, startByte: Long) {
        if (!handle.isValid) return
        val info = currentFileInfo ?: return
        if (fileIndex != info.fileIndex) return

        val firstPiece = info.firstPiece
        val lastPiece = info.lastPiece

        val targetPiece = pieceIndexAtFileOffset(info, startByte)
        val windowEnd = minOf(targetPiece + BUFFER_PIECES_AHEAD, lastPiece)

        val newWindow = (targetPiece..windowEnd).toSet()
        val pinned = setOf(firstPiece, minOf(firstPiece + 1, lastPiece), lastPiece)

        synchronized(activeWindowLock) {
            // Reset pieces that left the active window and are not container metadata
            for (oldPiece in activeWindowPieces) {
                if (oldPiece !in newWindow && oldPiece !in pinned) {
                    handle.piecePriority(oldPiece, Priority.IGNORE)
                    handle.resetPieceDeadline(oldPiece)
                }
            }

            // Prioritize pieces in the active sliding window with short deadlines
            for (piece in newWindow) {
                handle.piecePriority(piece, Priority.TOP_PRIORITY)
                val deadlineMs = 1000 + (piece - targetPiece) * 1000
                handle.setPieceDeadline(piece, deadlineMs)
            }

            activeWindowPieces.clear()
            activeWindowPieces.addAll(newWindow)
        }
    }

    private fun hasPiece(handle: TorrentHandle, piece: Int): Boolean {
        if (!handle.isValid) return false
        return try {
            handle.havePiece(piece)
        } catch (_: Exception) {
            false
        }
    }

    private fun isActiveHandle(handle: TorrentHandle): Boolean {
        if (!handle.isValid) return false
        val expected = activeInfoHash ?: return true
        return try {
            handle.infoHash() == expected
        } catch (_: Exception) {
            false
        }
    }

    private fun updateBufferingIfNeeded(handle: TorrentHandle) {
        val current = _streamState.value
        if (current is TorrentStreamState.Ready || current is TorrentStreamState.Error) return
        val status = try { handle.status() } catch (_: Exception) { return }
        _streamState.value = TorrentStreamState.Buffering(
            progress = status.progress(),
            peers = status.numPeers(),
            downloadSpeed = status.downloadPayloadRate().toLong(),
        )
    }

    private fun checkStreamReadiness(handle: TorrentHandle) {
        if (!handle.isValid || !isActiveHandle(handle)) return
        val info = currentFileInfo ?: return

        val firstPiece = info.firstPiece
        val lastPiece = info.lastPiece
        val filePath = File(saveDir, info.fileName).absolutePath

        // Ready when initial playback pieces are downloaded
        val hasHead = hasPiece(handle, firstPiece)
        val hasBuffer = (firstPiece + 1 > lastPiece) || hasPiece(handle, firstPiece + 1)

        if (hasHead && hasBuffer) {
            val streamUrl = "http://127.0.0.1:$serverPort/stream?file=${info.chapterIndex}"
            val status = try { handle.status() } catch (_: Exception) { return }
            if (_streamState.value !is TorrentStreamState.Ready) {
                _streamState.value = TorrentStreamState.Ready(
                    filePath = filePath,
                    streamUrl = streamUrl,
                    fileIndex = info.chapterIndex,
                    peers = status.numPeers(),
                    downloadSpeed = status.downloadPayloadRate().toLong(),
                    uploadSpeed = status.uploadPayloadRate().toLong(),
                )
            }
        }
    }

    private fun startStreamServer() {
        try {
            val server = try {
                ServerSocket(STREAM_PORT, 50, InetAddress.getByName("127.0.0.1"))
            } catch (_: Exception) {
                ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            }
            serverSocket = server
            serverPort = server.localPort
            Timber.d("TorrentStreamEngine server listening on 127.0.0.1:$serverPort")
            scope.launch(Dispatchers.IO) {
                while (isStarted && !server.isClosed) {
                    try {
                        val clientSocket = server.accept()
                        scope.launch(Dispatchers.IO) {
                            handleStreamClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!server.isClosed) Timber.w(e, "Error accepting stream client")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start local stream server")
        }
    }

    private fun stopStreamServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing stream server")
        }
        serverSocket = null
        serverPort = 0
    }

    private fun closeActiveStreamClients() {
        activeSockets.toList().forEach { client ->
            try { client.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
    }

    private fun handleStreamClient(socket: Socket) {
        activeSockets.add(socket)
        try {
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return
            if (!requestLine.startsWith("GET") && !requestLine.startsWith("HEAD")) return

            var rangeHeader: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substring(6).trim()
                }
            }

            // Wait up to 10s for metadata & file info if client connects early
            var metadataWait = 0
            while (currentFileInfo == null && metadataWait < 100 && isStarted && !socket.isClosed) {
                Thread.sleep(100)
                metadataWait++
            }

            val info = currentFileInfo
            val handle = currentHandle
            if (info == null || handle == null || !handle.isValid || !isActiveHandle(handle)) {
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 503 Service Unavailable\r\nRetry-After: 2\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
                return
            }

            val fileSize = info.fileSize
            val fileName = info.fileName
            val file = File(saveDir, fileName)

            var startByte = 0L
            var endByte = fileSize - 1
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val rangeVal = rangeHeader.removePrefix("bytes=").trim()
                val parts = rangeVal.split("-", limit = 2)
                val startPart = parts.getOrNull(0)?.trim().orEmpty()
                val endPart = parts.getOrNull(1)?.trim().orEmpty()
                if (startPart.isEmpty()) {
                    // Suffix range: bytes=-N means the last N bytes.
                    val suffixLength = endPart.toLongOrNull() ?: 0L
                    startByte = (fileSize - suffixLength).coerceAtLeast(0L)
                } else {
                    startByte = startPart.toLongOrNull() ?: 0L
                    if (endPart.isNotEmpty()) {
                        endByte = endPart.toLongOrNull() ?: endByte
                    }
                }
            }

            if (fileSize <= 0 || startByte > fileSize - 1) {
                val out = socket.getOutputStream()
                val response = "HTTP/1.1 416 Range Not Satisfiable\r\n" +
                    "Content-Range: bytes */$fileSize\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(response.toByteArray(Charsets.UTF_8))
                out.flush()
                return
            }
            startByte = startByte.coerceIn(0L, fileSize - 1)
            endByte = endByte.coerceIn(startByte, fileSize - 1)

            val contentLength = endByte - startByte + 1
            val mimeType = when {
                fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
                fileName.endsWith(".m4b", ignoreCase = true) || fileName.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                fileName.endsWith(".flac", ignoreCase = true) -> "audio/flac"
                fileName.endsWith(".ogg", ignoreCase = true) || fileName.endsWith(".oga", ignoreCase = true) -> "audio/ogg"
                else -> "audio/mpeg"
            }

            val out = socket.getOutputStream()
            val header = buildString {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Type: $mimeType\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Range: bytes $startByte-$endByte/$fileSize\r\n")
                append("Content-Length: $contentLength\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            out.write(header.toByteArray(Charsets.UTF_8))
            out.flush()

            if (requestLine.startsWith("HEAD")) return

            // Ensure first requested piece is downloaded before attempting to read the file
            val firstRequestedPiece = pieceIndexAtFileOffset(info, startByte)
            var pieceWait = 0
            while (!hasPiece(handle, firstRequestedPiece) && !socket.isClosed && isStarted && pieceWait < 600) {
                Thread.sleep(50)
                pieceWait++
            }
            if (!hasPiece(handle, firstRequestedPiece) || socket.isClosed || !isStarted || !file.exists()) return

            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(64 * 1024)
                var currentPos = startByte
                val pieceLength = info.pieceLength

                while (currentPos <= endByte && !socket.isClosed && isStarted && handle.isValid) {
                    val pieceIndex = pieceIndexAtFileOffset(info, currentPos)

                    updateSlidingWindow(handle, info.fileIndex, currentPos)

                    var pieceWait = 0
                    while (!hasPiece(handle, pieceIndex) && !socket.isClosed && isStarted && pieceWait < 600) {
                        Thread.sleep(50)
                        pieceWait++
                    }
                    if (!hasPiece(handle, pieceIndex) || socket.isClosed || !isStarted) break

                    val absolutePos = info.fileOffset + currentPos
                    val pieceStartAbsolute = (absolutePos / pieceLength) * pieceLength
                    val pieceEndAbsolute = pieceStartAbsolute + pieceLength
                    val chunkEnd = minOf(endByte + 1, pieceEndAbsolute - info.fileOffset)
                    var bytesRemaining = (chunkEnd - currentPos).toInt()

                    raf.seek(currentPos)
                    while (bytesRemaining > 0 && !socket.isClosed) {
                        val toRead = minOf(bytesRemaining, buffer.size)
                        val read = raf.read(buffer, 0, toRead)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        bytesRemaining -= read
                        currentPos += read
                    }
                    out.flush()
                }
            }
        } catch (_: SocketException) {
            // Player cancelled / seeked to another offset
        } catch (e: Exception) {
            Timber.d("Stream client exception: ${e.message}")
        } finally {
            activeSockets.remove(socket)
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun saveResumeData(handle: TorrentHandle) {
        try {
            handle.saveResumeData()
        } catch (e: Exception) {
            Timber.w(e, "Failed to save resume data")
        }
    }

    /**
     * Alert-owned handles point into native alert memory and become dangling as soon as the
     * alert is destroyed, so always resolve a session-owned handle for the active info hash.
     */
    private fun handleForHash(hash: Sha1Hash?): TorrentHandle? {
        val expected = activeInfoHash ?: return null
        if (hash == null || hash != expected) return null
        return try {
            sessionManager?.find(hash)?.takeIf { it.isValid }
        } catch (_: Exception) {
            null
        }
    }

    private fun handleForAlert(alert: TorrentAlert<*>): TorrentHandle? {
        val hash = try { alert.handle().infoHash() } catch (_: Exception) { null }
        return handleForHash(hash)
    }

    private val alertListener = object : AlertListener {
        override fun types(): IntArray? = null

        override fun alert(alert: Alert<*>?) {
            when (alert) {
                is AddTorrentAlert -> {
                    val hash = runCatching { alert.params().infoHashes.best }.getOrNull()
                        ?: runCatching { alert.handle().infoHash() }.getOrNull()

                    if (hash != null && hash != activeInfoHash) return

                    if (alert.error().isError) {
                        _streamState.value = TorrentStreamState.Error("Failed to add torrent: ${alert.error().message}")
                        return
                    }

                    val handle = handleForHash(hash) ?: return
                    currentHandle = handle
                    val ti = getTorrentInfoSafely(handle)
                    if (ti != null) {
                        prioritizeFile(handle, targetChapterIndex, targetFileName)
                        checkStreamReadiness(handle)
                        updateBufferingIfNeeded(handle)
                    }
                }

                is MetadataReceivedAlert -> {
                    val handle = handleForAlert(alert) ?: return
                    currentHandle = handle
                    prioritizeFile(handle, targetChapterIndex, targetFileName)
                    checkStreamReadiness(handle)
                    updateBufferingIfNeeded(handle)
                }

                is PieceFinishedAlert -> {
                    val handle = handleForAlert(alert) ?: return
                    val status = try { handle.status() } catch (_: Exception) { return }
                    val progress = status.progress()
                    val peers = status.numPeers()
                    val dlSpeed = status.downloadPayloadRate().toLong()
                    val ulSpeed = status.uploadPayloadRate().toLong()

                    _status.value = TorrentStatus(
                        peers = peers,
                        downloadSpeed = dlSpeed,
                        uploadSpeed = ulSpeed,
                        progress = progress,
                        ratio = if (status.allTimeDownload() > 0) status.allTimeUpload().toFloat() / status.allTimeDownload() else 0f,
                        state = _streamState.value,
                    )

                    checkStreamReadiness(handle)
                    when (val current = _streamState.value) {
                        is TorrentStreamState.Ready -> {
                            _streamState.value = current.copy(
                                peers = peers,
                                downloadSpeed = dlSpeed,
                                uploadSpeed = ulSpeed,
                            )
                        }
                        is TorrentStreamState.Error, is TorrentStreamState.Seeding -> {}
                        else -> {
                            _streamState.value = TorrentStreamState.Buffering(
                                progress = progress,
                                peers = peers,
                                downloadSpeed = dlSpeed,
                            )
                        }
                    }
                }

                is TorrentFinishedAlert -> {
                    val handle = handleForAlert(alert) ?: return
                    val info = currentFileInfo ?: return
                    // Only transition to Seeding when all pieces of target file are actually completed
                    val isTargetFileComplete = (info.firstPiece..info.lastPiece).all { hasPiece(handle, it) }
                    if (isTargetFileComplete) {
                        val status = try { handle.status() } catch (_: Exception) { return }
                        _streamState.value = TorrentStreamState.Seeding(
                            filePath = File(saveDir, info.fileName).absolutePath,
                            peers = status.numPeers(),
                            uploadSpeed = status.uploadPayloadRate().toLong(),
                            ratio = if (status.allTimeDownload() > 0) status.allTimeUpload().toFloat() / status.allTimeDownload() else 0f,
                        )
                    }
                }

                else -> {}
            }
        }
    }
}
