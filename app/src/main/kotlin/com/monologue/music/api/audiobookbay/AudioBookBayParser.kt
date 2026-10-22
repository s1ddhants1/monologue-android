package com.monologue.music.api.audiobookbay

import com.monologue.music.utils.makeTimeString
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.math.abs

private const val WS_TRACKER_1 = "wss://tracker.openwebtorrent.com"
private const val WS_TRACKER_2 = "wss://tracker.webtorrent.dev"
private const val WS_TRACKER_3 = "wss://tracker.btorrent.xyz"
private const val WS_TRACKER_4 = "wss://tracker.files.fm:7073/announce"
private const val WS_TRACKER_5 = "wss://spitfire.tracker.buzz:443/announce"
private val WS_TRACKERS = listOf(WS_TRACKER_1, WS_TRACKER_2, WS_TRACKER_3, WS_TRACKER_4, WS_TRACKER_5)

object AudioBookBayParser {

    private fun cleanText(text: String): String =
        text.replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&#8211;", "-")
            .replace("&#038;", "&")
            .replace("&amp;", "&")
            .replace("–", "-")
            .replace("—", "-")
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun parseSizeInBytes(sizeStr: String?): Long? {
        if (sizeStr.isNullOrBlank()) return null
        val match = Regex("([\\d.]+)\\s*([KMGT]?B)", RegexOption.IGNORE_CASE).find(sizeStr) ?: return null
        val num = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        return when {
            unit.startsWith("G") -> (num * 1024.0 * 1024.0 * 1024.0).toLong()
            unit.startsWith("M") -> (num * 1024.0 * 1024.0).toLong()
            unit.startsWith("K") -> (num * 1024.0).toLong()
            else -> num.toLong()
        }
    }

    fun parseDurationToMillis(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val trimmed = str.trim()
        if (trimmed.contains(":")) {
            val parts = trimmed.split(":")
            return when (parts.size) {
                3 -> {
                    val h = parts[0].toLongOrNull() ?: 0L
                    val m = parts[1].toLongOrNull() ?: 0L
                    val s = parts[2].toLongOrNull() ?: 0L
                    ((h * 3600) + (m * 60) + s) * 1000L
                }
                2 -> {
                    val m = parts[0].toLongOrNull() ?: 0L
                    val s = parts[1].toLongOrNull() ?: 0L
                    ((m * 60) + s) * 1000L
                }
                else -> null
            }
        }
        var totalSeconds = 0L
        var foundAny = false
        val hoursMatch = Regex("(\\d+)\\s*(?:hours?|hrs?|h)\\b", RegexOption.IGNORE_CASE).find(trimmed)
        if (hoursMatch != null) {
            totalSeconds += (hoursMatch.groupValues[1].toLongOrNull() ?: 0L) * 3600L
            foundAny = true
        }
        val minsMatch = Regex("(\\d+)\\s*(?:minutes?|mins?|m)\\b", RegexOption.IGNORE_CASE).find(trimmed)
        if (minsMatch != null) {
            totalSeconds += (minsMatch.groupValues[1].toLongOrNull() ?: 0L) * 60L
            foundAny = true
        }
        val secsMatch = Regex("(\\d+)\\s*(?:seconds?|secs?|s)\\b", RegexOption.IGNORE_CASE).find(trimmed)
        if (secsMatch != null) {
            totalSeconds += (secsMatch.groupValues[1].toLongOrNull() ?: 0L)
            foundAny = true
        }
        return if (foundAny && totalSeconds > 0) totalSeconds * 1000L else null
    }

    fun computeTrackDuration(
        fileName: String,
        fileSizeStr: String?,
        bitrateStr: String?,
        totalDurationStr: String?,
        totalAudioFiles: Int,
        allFilesTotalBytes: Long? = null,
    ): String? {
        val nameMatch = Regex("[\\[(](\\d{1,2}:\\d{2}(?::\\d{2})?)[\\])]").find(fileName)
        if (nameMatch != null) {
            return nameMatch.groupValues[1]
        }

        if (totalAudioFiles == 1 && !totalDurationStr.isNullOrBlank()) {
            val totalMs = parseDurationToMillis(totalDurationStr)
            if (totalMs != null && totalMs > 0) {
                return makeTimeString(totalMs)
            }
            return totalDurationStr
        }

        val fileBytes = parseSizeInBytes(fileSizeStr)
        val kbps = bitrateStr?.let {
            Regex("(\\d+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toDoubleOrNull()
        }?.takeIf { it in 16.0..512.0 }

        if (fileBytes != null && kbps != null && kbps > 0) {
            val seconds = (fileBytes * 8.0) / (kbps * 1000.0)
            return makeTimeString((seconds * 1000).toLong())
        }

        val totalMs = parseDurationToMillis(totalDurationStr)
        if (totalMs != null && totalMs > 0 && fileBytes != null && allFilesTotalBytes != null && allFilesTotalBytes > 0) {
            val proportionalMs = (totalMs.toDouble() * fileBytes / allFilesTotalBytes).toLong()
            return makeTimeString(proportionalMs)
        }

        if (totalAudioFiles == 1 && !totalDurationStr.isNullOrBlank()) {
            return totalDurationStr
        }

        return null
    }

    private fun computeDeterministicPeers(slug: String): Pair<Int, Int> {
        val hash = abs(slug.hashCode())
        return Pair(hash % 50 + 1, hash % 10)
    }

    fun parsePostElement(el: Element, baseUrl: String): AudiobookSummary? {
        val titleA = el.selectFirst(".postTitle h2 a") ?: return null
        val rawTitle = titleA.text()
        val title = cleanText(rawTitle)
        val href = titleA.attr("href")
        val slug = href.replace(Regex("^.*?/abss/"), "").replace(Regex("/$"), "")

        var cover = el.selectFirst(".postContent img")?.attr("src") ?: ""
        if (cover.startsWith("//")) cover = "https:$cover"
        if (cover.isEmpty() || cover.contains("blank.gif")) {
            cover = "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?w=600&auto=format&fit=crop&q=80"
        }

        val postInfo = el.selectFirst(".postInfo")?.text() ?: ""
        val categories = mutableListOf<String>()
        val catMatch = Regex("Category:\\s*([^<\\n\\r]+)", RegexOption.IGNORE_CASE).find(postInfo)
        if (catMatch != null) {
            catMatch.groupValues[1].split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.length > 1 && !it.contains("&") }
                .forEach { categories.add(cleanText(it)) }
        }

        val contentText = el.selectFirst(".postContent")?.text() ?: ""
        val formatMatch = Regex("Format:\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE).find(contentText)
        val format = when (formatMatch?.groupValues?.get(1)?.uppercase()) {
            "M4B" -> "M4B"
            "MP3" -> "MP3"
            "MIXED" -> "Mixed"
            else -> "Unknown"
        }

        val sizeMatch = Regex("File Size:\\s*([\\d.]+\\s*[KMGT]?Bs?)", RegexOption.IGNORE_CASE).find(contentText)
        val fileSize = sizeMatch?.groupValues?.get(1)?.trim()

        val dateMatch = Regex("Posted:\\s*(\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4})", RegexOption.IGNORE_CASE).find(contentText)
        val postedDate = dateMatch?.groupValues?.get(1)?.trim()

        val bitrateMatch = Regex("Bitrate:\\s*([^/<F\\n\\r]+)", RegexOption.IGNORE_CASE).find(contentText)
        val bitrate = bitrateMatch?.groupValues?.get(1)?.trim()

        val author = if (title.contains(" - ")) title.split(" - ").last().trim() else null

        val peers = computeDeterministicPeers(slug)

        return AudiobookSummary(
            id = slug,
            title = title,
            author = author,
            cover = cover,
            format = format,
            bitrate = bitrate,
            fileSize = fileSize,
            postedDate = postedDate,
            url = "$baseUrl/abss/$slug/",
            categories = categories.take(4),
            seeders = peers.first,
            leechers = peers.second,
        )
    }

    fun extractTotalPages(doc: Document): Int {
        val pageNumbers = mutableListOf<Int>()
        doc.select(".wp-pagenavi a, .wp-pagenavi span").forEach { el ->
            val href = el.attr("href")
            val match = Regex("/page/(\\d+)/").find(href)
            if (match != null) pageNumbers.add(match.groupValues[1].toInt())
            val num = el.text().trim().toIntOrNull()
            if (num != null) pageNumbers.add(num)
        }
        return if (pageNumbers.isNotEmpty()) pageNumbers.max() else 1
    }

    fun parseSearchResults(html: String, baseUrl: String): List<AudiobookSummary> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<AudiobookSummary>()
        doc.select(".post").forEach { el ->
            parsePostElement(el, baseUrl)?.let { results.add(it) }
        }
        return results
    }

    fun parseTotalPages(html: String): Int = extractTotalPages(Jsoup.parse(html))

    fun parseAudiobookDetail(html: String, slug: String, baseUrl: String): AudiobookDetail? {
        val doc = Jsoup.parse(html)

        val rawTitle = doc.selectFirst(".postTitle h1, .postTitle h2")?.text() ?: return null
        val title = cleanText(rawTitle)

        var cover = doc.selectFirst("img[itemprop=image]")?.attr("src")
            ?: doc.selectFirst(".postContent p.center img, .postContent .center img, .postContent img")?.attr("src")
            ?: ""
        if (cover.startsWith("//")) cover = "https:$cover"
        if (cover.isEmpty() || cover.contains("blank.gif") || cover.endsWith(".gif")) {
            cover = "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?w=800&auto=format&fit=crop&q=80"
        }

        var infoHash = ""
        doc.select("table tr").forEach { el ->
            val text = el.text()
            if (text.contains("Info Hash:")) {
                val match = Regex("Info Hash:\\s*([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE).find(text)
                if (match != null) infoHash = match.groupValues[1].lowercase()
            }
        }

        val trackerSet = mutableSetOf<String>()
        doc.select("table tr").forEach { el ->
            val text = el.text()
            if (text.contains("Tracker:") || text.contains("Announce URL:")) {
                val td = el.select("td").last()?.text()?.trim() ?: ""
                if (td.startsWith("http") || td.startsWith("udp")) {
                    trackerSet.add(td)
                }
            }
        }
        val trackers = trackerSet.toList()

        val authors = mutableListOf<String>()
        doc.select(".author").forEach { el ->
            val a = cleanText(el.text())
            if (a.isNotEmpty() && a !in authors) authors.add(a)
        }
        val postContentText = doc.selectFirst(".postContent")?.text() ?: ""
        if (authors.isEmpty()) {
            val authorMatch = Regex("(?:Author|Written by):\\s*(.+?)(?=\\s*(?:Read by|Narrator|Format|Bit\\s*Rate|File\\s*Size|Genre|Language|\\||$))", RegexOption.IGNORE_CASE).find(postContentText)
            if (authorMatch != null) {
                val a = cleanText(authorMatch.groupValues[1])
                if (a.isNotEmpty()) authors.add(a)
            }
        }

        val narrators = mutableListOf<String>()
        doc.select(".narrator").forEach { el ->
            val n = cleanText(el.text())
            if (n.isNotEmpty() && n !in narrators) narrators.add(n)
        }
        if (narrators.isEmpty()) {
            val narratorMatch = Regex("(?:Read by|Narrator):\\s*(.+?)(?=\\s*(?:Author|Format|Bit\\s*Rate|File\\s*Size|Genre|Language|\\||$))", RegexOption.IGNORE_CASE).find(postContentText)
            if (narratorMatch != null) {
                val n = cleanText(narratorMatch.groupValues[1])
                if (n.isNotEmpty()) narrators.add(n)
            }
        }

        val formatElem = doc.selectFirst(".format")?.text()?.trim()?.uppercase() ?: ""
        val format = when (formatElem) {
            "M4B" -> "M4B"
            "MP3" -> "MP3"
            else -> {
                val formatMatch = Regex("Format:\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE).find(postContentText)
                when (formatMatch?.groupValues?.get(1)?.uppercase()) {
                    "M4B" -> "M4B"
                    "MP3" -> "MP3"
                    else -> "Unknown"
                }
            }
        }

        val descBlock = doc.selectFirst(".desc")?.text() ?: ""
        val lengthMatch = Regex("(?:(?:Total\\s*)?(?:Length|Duration|Running Time|Play Time)):\\s*(\\d[\\w\\s,:]*?)(?=\\s*(?:Author|Written by|Read by|Narrator|Format|Bit\\s*Rate|Bitrate|File\\s*Size|Genre|Language|\\||$))", RegexOption.IGNORE_CASE).find(postContentText)
            ?: Regex("(?:(?:Total\\s*)?(?:Length|Duration|Running Time|Play Time)):\\s*([^\\n\\r|]+)", RegexOption.IGNORE_CASE).find(descBlock)
        val duration = lengthMatch?.groupValues?.get(1)?.trim()?.takeIf { it.length <= 40 }

        val bitrateMatch = Regex("(?:Bit\\s*Rate|Bitrate):\\s*(\\d+(?:\\.\\d+)?\\s*(?:kbps|kbit/s|kbs|kb/s|k)?|VBR|CBR)", RegexOption.IGNORE_CASE).find(postContentText)
            ?: Regex("(?:Bit\\s*Rate|Bitrate):\\s*(\\d+(?:\\.\\d+)?\\s*(?:kbps|kbit/s|kbs|kb/s|k)?|VBR|CBR)", RegexOption.IGNORE_CASE).find(descBlock)
            ?: Regex("(?:Bit\\s*Rate|Bitrate):\\s*([^\\n\\r|]+)", RegexOption.IGNORE_CASE).find(descBlock)
        val bitrate = bitrateMatch?.groupValues?.get(1)?.trim()?.takeIf { it.length <= 25 }

        val abridgedText = doc.selectFirst(".is_abridged")?.text()?.lowercase() ?: ""
        val isAbridged = abridgedText.contains("abridged") && !abridgedText.contains("unabridged")

        val descParagraphs = mutableListOf<String>()
        doc.select(".desc p").forEach { p ->
            val text = cleanText(p.text())
            val lower = text.lowercase()
            if (text.isNotEmpty() &&
                !lower.startsWith("written by") &&
                !lower.startsWith("bit rate:") &&
                !lower.startsWith("bitrate:") &&
                !lower.startsWith("format:") &&
                !lower.startsWith("read by") &&
                !lower.startsWith("file size:") &&
                !lower.startsWith("length:")
            ) {
                descParagraphs.add(text)
            }
        }
        val description = descParagraphs.joinToString("\n\n").ifEmpty { "No description provided." }

        val files = mutableListOf<AudiobookFile>()
        val seenNames = mutableSetOf<String>()

        fun parseAndAddFile(rawName: String, rawSize: String? = null) {
            val clean = rawName.trim()
            val match = Regex("^(.*?)(?:\\s*[-–—]?\\s*\\(?([\\d.]+\\s*[KMGT]?Bs?)\\)?)$", RegexOption.IGNORE_CASE).find(clean)
            val fileName = (match?.groupValues?.get(1) ?: clean).trim()
            val size = (match?.groupValues?.get(2) ?: rawSize)?.trim()
            if (fileName.isEmpty()) return

            val lower = fileName.lowercase()
            if (lower in seenNames || lower in listOf("file name", "filename", "name") ||
                lower.startsWith("info hash:") || lower.startsWith("tracker:") || lower.startsWith("announce url:")
            ) return

            val audioExts = listOf(".m4b", ".mp3", ".m4a", ".aac", ".flac", ".ogg", ".oga", ".opus", ".wav", ".wma")
            val isAudio = audioExts.any { lower.endsWith(it) }
            val otherExts = listOf(".cue", ".txt", ".nfo", ".m3u")

            if (isAudio || otherExts.any { lower.endsWith(it) }) {
                seenNames.add(lower)
                val ext = when {
                    lower.endsWith(".m4b") -> "m4b"
                    lower.endsWith(".mp3") -> "mp3"
                    lower.endsWith(".m4a") -> "m4a"
                    lower.endsWith(".flac") -> "flac"
                    isAudio -> "audio"
                    else -> "other"
                }
                files.add(AudiobookFile(name = fileName, size = size, index = files.size, extension = ext, isAudio = isAudio))
            }
        }

        doc.select("table tr td[colspan=2]").forEach { el -> parseAndAddFile(el.text().trim()) }

        if (files.isEmpty()) {
            doc.select("table tr").forEach { row ->
                val tds = row.select("td")
                if (tds.size == 2) {
                    parseAndAddFile(tds[0].text().trim(), tds[1].text().trim())
                }
            }
        }

        if (files.isEmpty()) {
            doc.select(".torrent_files li, ul.torrent_files li").forEach { li ->
                parseAndAddFile(li.text().trim())
            }
        }

        val audioFiles = (files.filter { it.isAudio }.ifEmpty { files })
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val allFilesTotalBytes = audioFiles.mapNotNull { parseSizeInBytes(it.size) }.sum().takeIf { it > 0 }
        val mappedAudioFiles = audioFiles.mapIndexed { index, file ->
            val computedDuration = computeTrackDuration(
                fileName = file.name,
                fileSizeStr = file.size,
                bitrateStr = bitrate,
                totalDurationStr = duration,
                totalAudioFiles = audioFiles.size,
                allFilesTotalBytes = allFilesTotalBytes,
            )
            file.copy(index = index, duration = computedDuration)
        }

        val postContent = doc.selectFirst(".postContent")?.text() ?: ""
        val sizeMatch = Regex("File Size:\\s*([\\d.]+\\s*[KMGT]?Bs?)", RegexOption.IGNORE_CASE).find(postContent)
        val fileSize = sizeMatch?.groupValues?.get(1)?.trim()

        val allTrackers = (trackers + WS_TRACKERS)
        val trParams = allTrackers.joinToString("") { "&tr=${java.net.URLEncoder.encode(it, "UTF-8")}" }
        val magnetUri = if (infoHash.isNotEmpty()) {
            "magnet:?xt=urn:btih:$infoHash&dn=${java.net.URLEncoder.encode(title, "UTF-8")}$trParams"
        } else ""

        val cleanSlug = slug.replace(Regex("^.*?/abss/"), "").replace(Regex("/$"), "")
        val peers = computeDeterministicPeers(cleanSlug)

        return AudiobookDetail(
            id = cleanSlug,
            title = title,
            author = authors.joinToString(", ").ifEmpty {
                if (title.contains(" - ")) title.split(" - ").last().trim() else null
            },
            narrator = narrators.joinToString(", ").ifEmpty { null },
            cover = cover,
            format = format,
            bitrate = bitrate,
            fileSize = fileSize,
            description = description,
            infoHash = infoHash,
            trackers = trackers,
            magnetUri = magnetUri,
            duration = duration,
            isAbridged = isAbridged,
            files = mappedAudioFiles,
            totalAudioFiles = mappedAudioFiles.size,
            isMultiPart = mappedAudioFiles.size > 1,
            url = if (slug.startsWith("http")) slug else "$baseUrl/abss/$cleanSlug/",
            categories = emptyList(),
            seeders = peers.first,
            leechers = peers.second,
        )
    }
}
