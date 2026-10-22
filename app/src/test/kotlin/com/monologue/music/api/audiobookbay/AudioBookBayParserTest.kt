package com.monologue.music.api.audiobookbay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBookBayParserTest {

    @Test
    fun parseSearchResults_extractsExpectedFields() {
        val html = """
            <div class="post">
                <div class="postTitle">
                    <h2><a href="/abss/dune-frank-herbert/">Dune &#8211; Frank Herbert</a></h2>
                </div>
                <div class="postContent">
                    <img src="//images.example.com/dune.jpg" />
                    <p>Format: MP3 | Bitrate: 64 Kbps</p>
                </div>
                <div class="postInfo">
                    Category: Sci-Fi Fantasy
                </div>
            </div>
        """.trimIndent()

        val results = AudioBookBayParser.parseSearchResults(html, "https://audiobookbay.lu")
        assertEquals(1, results.size)
        val book = results[0]
        assertEquals("dune-frank-herbert", book.id)
        assertEquals("dune-frank-herbert", book.slug)
        assertEquals("Dune - Frank Herbert", book.title)
        assertEquals("https://images.example.com/dune.jpg", book.cover)
        assertEquals("MP3", book.format)
        assertTrue(book.categories.contains("Sci-Fi"))
    }

    @Test
    fun parseAudiobookDetail_extractsMagnetAndFiles() {
        val html = """
            <div class="postTitle">
                <h2>Project Hail Mary &#8211; Andy Weir</h2>
            </div>
            <div class="postContent">
                <img src="/cover.jpg" />
                <p>Author: Andy Weir</p>
                <p>Read by: Ray Porter</p>
                <p>Format: M4B</p>
                <p>Bitrate: 128 Kbps</p>
                <p>File Size: 500 MB</p>
                <div class="desc"><p>An interstellar mission to save humanity.</p></div>
                <table>
                    <tr><td>Info Hash:</td><td>abcdef1234567890abcdef1234567890abcdef12</td></tr>
                </table>
            </div>
            <div class="torrent_files">
                <ul>
                    <li>HailMary_01.m4b (250 MB)</li>
                    <li>HailMary_02.m4b (250 MB)</li>
                </ul>
            </div>
        """.trimIndent()

        val detail = AudioBookBayParser.parseAudiobookDetail(html, "https://audiobookbay.lu/abss/project-hail-mary", "https://audiobookbay.lu")
        assertNotNull(detail)
        assertEquals("project-hail-mary", detail?.id)
        assertEquals("Project Hail Mary - Andy Weir", detail?.title)
        assertEquals("Andy Weir", detail?.author)
        assertEquals("Ray Porter", detail?.narrator)
        assertEquals("M4B", detail?.format)
        assertEquals("abcdef1234567890abcdef1234567890abcdef12", detail?.infoHash)
        assertTrue(detail?.magnetUri?.startsWith("magnet:?xt=urn:btih:abcdef1234567890abcdef1234567890abcdef12") == true)
        assertEquals(2, detail?.files?.size)
        assertEquals("HailMary_01.m4b", detail?.files?.get(0)?.name)
        assertEquals("4:33:04", detail?.files?.get(0)?.duration)
        assertEquals("4:33:04", detail?.files?.get(1)?.duration)
    }

    @Test
    fun computeTrackDuration_calculatesAccurately() {
        val dur1 = AudioBookBayParser.computeTrackDuration(
            fileName = "Track01.mp3",
            fileSizeStr = "100 MB",
            bitrateStr = "128 kbps",
            totalDurationStr = null,
            totalAudioFiles = 5,
        )
        assertEquals("1:49:13", dur1)

        val dur2 = AudioBookBayParser.computeTrackDuration(
            fileName = "Book.m4b",
            fileSizeStr = "500 MB",
            bitrateStr = null,
            totalDurationStr = "10 hrs 30 mins",
            totalAudioFiles = 1,
        )
        assertEquals("10:30:00", dur2)

        val dur3 = AudioBookBayParser.computeTrackDuration(
            fileName = "Chapter 01 (23:45).mp3",
            fileSizeStr = null,
            bitrateStr = null,
            totalDurationStr = null,
            totalAudioFiles = 10,
        )
        assertEquals("23:45", dur3)

        val dur4 = AudioBookBayParser.computeTrackDuration(
            fileName = "Part1.mp3",
            fileSizeStr = "50 MB",
            bitrateStr = null,
            totalDurationStr = "2:00:00",
            totalAudioFiles = 2,
            allFilesTotalBytes = 100L * 1024L * 1024L,
        )
        assertEquals("1:00:00", dur4)
    }

    @Test
    fun normalizeBaseUrl_cleansAndPrefixes() {
        assertEquals("https://audiobookbay.is", AudioBookBayClient.normalizeBaseUrl("audiobookbay.is"))
        assertEquals("https://audiobookbay.is", AudioBookBayClient.normalizeBaseUrl("https://audiobookbay.is/"))
        assertEquals("http://custom-proxy.internal:8080", AudioBookBayClient.normalizeBaseUrl("http://custom-proxy.internal:8080/"))
        assertEquals(com.monologue.music.constants.DEFAULT_AUDIOBOOKBAY_URL, AudioBookBayClient.normalizeBaseUrl("   "))
    }

    @Test
    fun clientBaseUrl_updatesDynamically() {
        val client = AudioBookBayClient()
        assertEquals("https://audiobookbay.lu", client.baseUrl)
        client.baseUrl = "audiobookbay.is/"
        assertEquals("https://audiobookbay.is", client.baseUrl)
    }
}
