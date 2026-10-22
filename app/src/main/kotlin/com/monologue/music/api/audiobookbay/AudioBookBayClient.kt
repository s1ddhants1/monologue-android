package com.monologue.music.api.audiobookbay

import android.content.Context
import com.monologue.music.constants.AudioBookBayBaseUrlKey
import com.monologue.music.constants.DEFAULT_AUDIOBOOKBAY_URL
import com.monologue.music.utils.dataStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

class AudioBookBayClient(
    private val httpClient: HttpClient = HttpClient(),
    context: Context? = null,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    var baseUrl: String = DEFAULT_AUDIOBOOKBAY_URL
        get() = field.removeSuffix("/")
        set(value) {
            field = normalizeBaseUrl(value)
        }

    init {
        context?.let { ctx ->
            coroutineScope.launch {
                ctx.dataStore.data.collect { prefs ->
                    val url = prefs[AudioBookBayBaseUrlKey]
                    if (!url.isNullOrBlank()) {
                        baseUrl = url
                    }
                }
            }
        }
    }

    companion object {
        fun normalizeBaseUrl(url: String): String {
            val trimmed = url.trim().removeSuffix("/")
            if (trimmed.isBlank()) return DEFAULT_AUDIOBOOKBAY_URL
            return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                "https://$trimmed"
            } else {
                trimmed
            }
        }
    }

    private suspend fun fetchHtml(path: String): String {
        val url = if (path.startsWith("/")) "$baseUrl$path" else path
        val response = httpClient.get(url) {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            header("Accept-Language", "en-US,en;q=0.5")
        }
        return response.bodyAsText()
    }

    suspend fun searchAudiobooks(
        query: String,
        page: Int = 1,
    ): SearchResult {
        val abbPage1 = (page - 1) * 2 + 1
        val abbPage2 = (page - 1) * 2 + 2
        val encoded = URLEncoder.encode(query, "UTF-8")

        val path1 = if (abbPage1 > 1) "/page/$abbPage1/?s=$encoded" else "/?s=$encoded"
        val path2 = "/page/$abbPage2/?s=$encoded"

        val html1 = fetchHtml(path1)
        val abbTotalPages = AudioBookBayParser.parseTotalPages(html1)
        val results = AudioBookBayParser.parseSearchResults(html1, baseUrl).toMutableList()

        if (abbTotalPages >= abbPage2) {
            runCatching { fetchHtml(path2) }.getOrNull()?.let { html2 ->
                results.addAll(AudioBookBayParser.parseSearchResults(html2, baseUrl))
            }
        }

        val unique = results.distinctBy { it.id }
        val totalPages = maxOf(1, (abbTotalPages + 1) / 2)
        return SearchResult(results = unique, page = page, totalPages = totalPages)
    }

    suspend fun getCategoryAudiobooks(
        categorySlug: String,
        page: Int = 1,
    ): SearchResult {
        val aliases = mapOf(
            "sci-fi" to "doctor-who-sci-fi",
            "scifi" to "doctor-who-sci-fi",
            "science-fiction" to "doctor-who-sci-fi",
            "biographies" to "autobiography-biographies",
            "biography" to "autobiography-biographies",
            "thriller" to "crime",
            "fiction" to "general-fiction",
            "non-fiction" to "general-non-fiction",
        )
        val actualSlug = aliases[categorySlug.lowercase()] ?: categorySlug

        val abbPage1 = (page - 1) * 2 + 1
        val abbPage2 = (page - 1) * 2 + 2

        val path1 = if (abbPage1 > 1) "/audio-books/type/$actualSlug/page/$abbPage1/" else "/audio-books/type/$actualSlug/"
        val path2 = "/audio-books/type/$actualSlug/page/$abbPage2/"

        val html1 = fetchHtml(path1)
        val abbTotalPages = AudioBookBayParser.parseTotalPages(html1)
        val results = AudioBookBayParser.parseSearchResults(html1, baseUrl).toMutableList()

        if (abbTotalPages >= abbPage2) {
            runCatching { fetchHtml(path2) }.getOrNull()?.let { html2 ->
                results.addAll(AudioBookBayParser.parseSearchResults(html2, baseUrl))
            }
        }

        val unique = results.distinctBy { it.id }
        val totalPages = maxOf(1, (abbTotalPages + 1) / 2)
        return SearchResult(results = unique, page = page, totalPages = totalPages)
    }

    suspend fun getLatestAudiobooks(page: Int = 1): SearchResult {
        val abbPage1 = (page - 1) * 2 + 1
        val abbPage2 = (page - 1) * 2 + 2

        val path1 = if (abbPage1 > 1) "/page/$abbPage1/" else "/"
        val path2 = "/page/$abbPage2/"

        val html1 = fetchHtml(path1)
        val abbTotalPages = AudioBookBayParser.parseTotalPages(html1)
        val results = AudioBookBayParser.parseSearchResults(html1, baseUrl).toMutableList()

        if (abbTotalPages >= abbPage2) {
            runCatching { fetchHtml(path2) }.getOrNull()?.let { html2 ->
                results.addAll(AudioBookBayParser.parseSearchResults(html2, baseUrl))
            }
        }

        val unique = results.distinctBy { it.id }
        val totalPages = maxOf(1, (abbTotalPages + 1) / 2)
        return SearchResult(results = unique, page = page, totalPages = totalPages)
    }

    suspend fun getAudiobookDetail(slug: String): AudiobookDetail? =
        runCatching {
            val html = fetchHtml("/abss/$slug/")
            AudioBookBayParser.parseAudiobookDetail(html, slug, baseUrl)
        }.getOrNull()
}

data class SearchResult(
    val results: List<AudiobookSummary>,
    val page: Int,
    val totalPages: Int,
)
