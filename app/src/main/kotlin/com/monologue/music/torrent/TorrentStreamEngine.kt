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
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
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
    }

    data class StreamFileInfo(
        val fileIndex: Int,
        val fileName: String,
        val fileSize: Long,
        val pieceLength: Int,
        val firstPiece: Int,
        val lastPiece: Int,
        val numPieces: Int,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionManager: SessionManager? = null
    private var currentHandle: TorrentHandle? = null
    private var targetFileIndex: Int = 0
    @Volatile
    private var currentFileInfo: StreamFileInfo? = null

    private var serverSocket: ServerSocket? = null
    var serverPort: Int = 0
        private set

    private val activeWindowPieces = mutableSetOf<Int>()

    private val _streamState = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    val streamState: StateFlow<TorrentStreamState> = _streamState

    private val _status = MutableStateFlow(TorrentStatus())
    val status: StateFlow<TorrentStatus> = _status

    private val saveDir: File
        get() = File(context.getExternalFilesDir(null), "audiobooks").also { it.mkdirs() }

    private var isStarted = false

    private val startMutex = kotlinx.coroutines.sync.Mutex()

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
                currentHandle?.let { saveResumeData(it) }
                sessionManager?.stop()
                sessionManager = null
                currentHandle = null
                currentFileInfo = null
                isStarted = false
                activeWindowPieces.clear()
                _streamState.value = TorrentStreamState.Idle
                _status.value = TorrentStatus()
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop torrent engine")
            }
        }
    }

    suspend fun streamFromMagnet(magnetUri: String, fileIndex: Int = 0): String? {
        ensureStarted()
        val sm = sessionManager ?: run {
            _streamState.value = TorrentStreamState.Error("Engine not started")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                stopCurrentStream()
                targetFileIndex = fileIndex
                _streamState.value = TorrentStreamState.Connecting

                sm.download(magnetUri, saveDir, TorrentFlags.DEFAULT_DONT_DOWNLOAD)
                _streamState.value = TorrentStreamState.FetchingMetadata
                null
            } catch (e: Exception) {
                Timber.e(e, "Failed to start stream")
                _streamState.value = TorrentStreamState.Error("Failed to start stream: ${e.message}")
                null
            }
        }
    }

    fun stopCurrentStream() {
        currentHandle?.let { handle ->
            saveResumeData(handle)
        }
        currentHandle = null
        currentFileInfo = null
        activeWindowPieces.clear()
        _streamState.value = TorrentStreamState.Idle
    }

    fun setTargetFile(index: Int) {
        targetFileIndex = index
        currentHandle?.let { handle ->
            prioritizeFile(handle, index)
            checkStreamReadiness(handle, index)
        }
    }

    private fun getTorrentInfoSafely(handle: TorrentHandle?): org.libtorrent4j.TorrentInfo? {
        if (handle == null || !handle.isValid) return null
        val status = try { handle.status() } catch (_: Exception) { return null }
        if (!status.hasMetadata()) return null
        return try {
            handle.torrentFile()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Ignores all non-target files and unplayed pieces; pins container metadata (head + tail)
     * and initializes the sliding download window at the start of the audio file.
     */
    private fun prioritizeFile(handle: TorrentHandle, fileIndex: Int) {
        val ti = getTorrentInfoSafely(handle) ?: return
        val fs = ti.files()
        val numFiles = ti.numFiles()
        if (fileIndex !in 0 until numFiles) return

        val firstPiece = fs.pieceIndexAtFile(fileIndex)
        val lastPiece = fs.lastPieceIndexAtFile(fileIndex)
        val pieceLength = fs.pieceLength()
        val fileSize = fs.fileSize(fileIndex)
        val fileName = fs.filePath(fileIndex)
        val numPieces = ti.numPieces()

        currentFileInfo = StreamFileInfo(
            fileIndex = fileIndex,
            fileName = fileName,
            fileSize = fileSize,
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
        activeWindowPieces.clear()
        updateSlidingWindow(handle, fileIndex, startByte = 0L)
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
        val pieceLength = info.pieceLength

        val pieceOffset = (startByte / pieceLength).toInt()
        val targetPiece = (firstPiece + pieceOffset).coerceIn(firstPiece, lastPiece)
        val windowEnd = minOf(targetPiece + BUFFER_PIECES_AHEAD, lastPiece)

        val newWindow = (targetPiece..windowEnd).toSet()
        val pinned = setOf(firstPiece, minOf(firstPiece + 1, lastPiece), lastPiece)

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

    private fun checkStreamReadiness(handle: TorrentHandle, fileIndex: Int) {
        if (!handle.isValid) return
        val info = currentFileInfo ?: return
        if (fileIndex != info.fileIndex) return

        val firstPiece = info.firstPiece
        val lastPiece = info.lastPiece
        val filePath = getFilePath(handle, fileIndex)

        // Ready when initial playback pieces are downloaded
        val hasHead = handle.havePiece(firstPiece)
        val hasBuffer = (firstPiece + 1 > lastPiece) || handle.havePiece(firstPiece + 1)

        if (hasHead && hasBuffer && filePath != null) {
            val streamUrl = "http://127.0.0.1:$serverPort/stream?file=$fileIndex"
            val status = handle.status()
            if (_streamState.value !is TorrentStreamState.Ready) {
                _streamState.value = TorrentStreamState.Ready(
                    filePath = filePath,
                    streamUrl = streamUrl,
                    fileIndex = fileIndex,
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

    private fun handleStreamClient(socket: Socket) {
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
            if (info == null || handle == null || !handle.isValid) {
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
                val parts = rangeVal.split("-")
                if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                    startByte = parts[0].toLongOrNull() ?: 0L
                }
                if (parts.size > 1 && parts[1].isNotEmpty()) {
                    endByte = parts[1].toLongOrNull() ?: (fileSize - 1)
                }
            }

            val contentLength = (endByte - startByte + 1).coerceAtLeast(0L)
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
            val firstRequestedPiece = (info.firstPiece + (startByte / info.pieceLength).toInt()).coerceIn(info.firstPiece, info.lastPiece)
            var pieceWait = 0
            while (!handle.havePiece(firstRequestedPiece) && !socket.isClosed && isStarted && pieceWait < 600) {
                Thread.sleep(50)
                pieceWait++
            }
            if (!handle.havePiece(firstRequestedPiece) || socket.isClosed || !isStarted || !file.exists()) return

            RandomAccessFile(file, "r").use { raf ->
                val buffer = ByteArray(64 * 1024)
                var currentPos = startByte
                val pieceLength = info.pieceLength
                val firstPiece = info.firstPiece
                val lastPiece = info.lastPiece

                while (currentPos <= endByte && !socket.isClosed && isStarted) {
                    val pieceOffset = (currentPos / pieceLength).toInt()
                    val pieceIndex = (firstPiece + pieceOffset).coerceIn(firstPiece, lastPiece)

                    updateSlidingWindow(handle, info.fileIndex, currentPos)

                    var pieceWait = 0
                    while (!handle.havePiece(pieceIndex) && !socket.isClosed && isStarted && pieceWait < 600) {
                        Thread.sleep(50)
                        pieceWait++
                    }
                    if (!handle.havePiece(pieceIndex) || socket.isClosed || !isStarted) break

                    val pieceFileStart = pieceOffset.toLong() * pieceLength
                    val pieceFileEnd = minOf(pieceFileStart + pieceLength, fileSize)
                    val chunkEnd = minOf(endByte + 1, pieceFileEnd)
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

    private fun getFilePath(handle: TorrentHandle, fileIndex: Int): String? {
        val info = currentFileInfo
        if (info != null && info.fileIndex == fileIndex) {
            return File(saveDir, info.fileName).absolutePath
        }
        val ti = getTorrentInfoSafely(handle) ?: return null
        if (fileIndex !in 0 until ti.numFiles()) return null
        val fileName = ti.files().filePath(fileIndex)
        return File(saveDir, fileName).absolutePath
    }

    private val alertListener = object : AlertListener {
        override fun types(): IntArray? = null

        override fun alert(alert: Alert<*>?) {
            when (alert) {
                is AddTorrentAlert -> {
                    if (alert.error().isError) {
                        _streamState.value = TorrentStreamState.Error("Failed to add torrent: ${alert.error().message}")
                        return
                    }
                    val handle = alert.handle()
                    currentHandle = handle
                    val ti = getTorrentInfoSafely(handle)
                    if (ti != null) {
                        prioritizeFile(handle, targetFileIndex)
                        checkStreamReadiness(handle, targetFileIndex)
                    }
                }

                is MetadataReceivedAlert -> {
                    val handle = alert.handle()
                    currentHandle = handle
                    prioritizeFile(handle, targetFileIndex)
                    checkStreamReadiness(handle, targetFileIndex)
                    if (_streamState.value !is TorrentStreamState.Ready) {
                        _streamState.value = TorrentStreamState.Buffering(progress = 0f, peers = 0, downloadSpeed = 0)
                    }
                }

                is PieceFinishedAlert -> {
                    val handle = alert.handle()
                    val status = handle.status()
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

                    checkStreamReadiness(handle, targetFileIndex)
                    when (val current = _streamState.value) {
                        is TorrentStreamState.Ready -> {
                            _streamState.value = current.copy(
                                peers = peers,
                                downloadSpeed = dlSpeed,
                                uploadSpeed = ulSpeed,
                            )
                        }
                        !is TorrentStreamState.Seeding -> {
                            _streamState.value = TorrentStreamState.Buffering(
                                progress = progress,
                                peers = peers,
                                downloadSpeed = dlSpeed,
                            )
                        }
                        else -> {}
                    }
                }

                is TorrentFinishedAlert -> {
                    val handle = alert.handle()
                    val info = currentFileInfo
                    // Only transition to Seeding when all pieces of target file are actually completed
                    val isTargetFileComplete = info != null && (info.firstPiece..info.lastPiece).all { handle.havePiece(it) }
                    if (isTargetFileComplete) {
                        val status = handle.status()
                        val filePath = getFilePath(handle, targetFileIndex) ?: ""
                        _streamState.value = TorrentStreamState.Seeding(
                            filePath = filePath,
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
