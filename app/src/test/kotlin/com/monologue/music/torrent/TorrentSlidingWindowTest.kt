package com.monologue.music.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentSlidingWindowTest {

    private fun computeSlidingWindow(
        firstPiece: Int,
        lastPiece: Int,
        pieceLength: Long,
        startByte: Long,
        bufferPiecesAhead: Int = 5,
    ): Set<Int> {
        val pieceOffset = (startByte / pieceLength).toInt()
        val targetPiece = (firstPiece + pieceOffset).coerceIn(firstPiece, lastPiece)
        val windowEnd = minOf(targetPiece + bufferPiecesAhead, lastPiece)
        return (targetPiece..windowEnd).toSet()
    }

    private fun computeDemotions(
        oldWindow: Set<Int>,
        newWindow: Set<Int>,
        pinnedPieces: Set<Int>,
    ): Set<Int> {
        return oldWindow.filter { it !in newWindow && it !in pinnedPieces }.toSet()
    }

    @Test
    fun `initial sliding window at byte 0 starts at firstPiece`() {
        val firstPiece = 10
        val lastPiece = 100
        val pieceLength = 1024L * 1024L // 1MB
        val window = computeSlidingWindow(firstPiece, lastPiece, pieceLength, startByte = 0L)

        assertEquals(6, window.size) // 10..15 inclusive
        assertEquals(10, window.minOrNull())
        assertEquals(15, window.maxOrNull())
    }

    @Test
    fun `seek to middle shifts window and identifies demotions`() {
        val firstPiece = 0
        val lastPiece = 500
        val pieceLength = 512L * 1024L // 512KB
        val pinned = setOf(0, 1, 500)

        val initialWindow = computeSlidingWindow(firstPiece, lastPiece, pieceLength, startByte = 0L)
        // initialWindow = 0..5

        // Seek to 50MB (around piece 100)
        val seekByte = 50L * 1024L * 1024L
        val seekWindow = computeSlidingWindow(firstPiece, lastPiece, pieceLength, startByte = seekByte)
        // seekWindow = 100..105

        val demotions = computeDemotions(initialWindow, seekWindow, pinned)

        // Pieces 2, 3, 4, 5 were in initialWindow and are not pinned, so they should be demoted to IGNORE
        assertEquals(setOf(2, 3, 4, 5), demotions)
        // Pinned pieces 0 and 1 must NOT be demoted
        assertTrue(0 !in demotions)
        assertTrue(1 !in demotions)
    }

    @Test
    fun `window near end of file does not exceed lastPiece`() {
        val firstPiece = 20
        val lastPiece = 23
        val pieceLength = 1024L * 1024L
        val window = computeSlidingWindow(firstPiece, lastPiece, pieceLength, startByte = 0L, bufferPiecesAhead = 5)

        assertEquals(setOf(20, 21, 22, 23), window)
        assertEquals(23, window.maxOrNull())
    }

    @Test
    fun `single piece file produces single piece window`() {
        val firstPiece = 5
        val lastPiece = 5
        val pieceLength = 1024L * 1024L
        val window = computeSlidingWindow(firstPiece, lastPiece, pieceLength, startByte = 0L)

        assertEquals(setOf(5), window)
    }

    @Test
    fun `seek beyond file clamps to lastPiece`() {
        val firstPiece = 10
        val lastPiece = 20
        val pieceLength = 1024L * 1024L
        val window = computeSlidingWindow(firstPiece, lastPiece, pieceLength, startByte = 100L * 1024L * 1024L)

        assertEquals(setOf(20), window)
    }
}
