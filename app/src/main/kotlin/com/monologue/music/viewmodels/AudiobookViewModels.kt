package com.monologue.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monologue.music.api.audiobookbay.AudioBookBayClient
import com.monologue.music.api.audiobookbay.AudiobookDetail
import com.monologue.music.api.audiobookbay.AudiobookSummary
import com.monologue.music.api.audiobookbay.CategoryInfo
import com.monologue.music.api.audiobookbay.POPULAR_CATEGORIES
import com.monologue.music.db.MusicDatabase
import com.monologue.music.db.entities.AudiobookChapterEntity
import com.monologue.music.db.entities.AudiobookEntity
import com.monologue.music.torrent.TorrentStreamEngine
import com.monologue.music.torrent.TorrentStreamState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class AudiobookSearchViewModel @Inject constructor(
    private val client: AudioBookBayClient,
) : ViewModel() {
    private val _results = MutableStateFlow<List<AudiobookSummary>>(emptyList())
    val results: StateFlow<List<AudiobookSummary>> = _results

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _currentPage = MutableStateFlow(1)
    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages

    fun search(query: String, page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            runCatching {
                val result = client.searchAudiobooks(query, page)
                _results.value = result.results
                _currentPage.value = result.page
                _totalPages.value = result.totalPages
            }
            _isLoading.value = false
        }
    }
}

@HiltViewModel
class AudiobookHomeViewModel @Inject constructor(
    private val client: AudioBookBayClient,
    private val database: MusicDatabase,
) : ViewModel() {
    private val _latest = MutableStateFlow<List<AudiobookSummary>>(emptyList())
    val latest: StateFlow<List<AudiobookSummary>> = _latest

    private val _categoryResults = MutableStateFlow<Map<String, List<AudiobookSummary>>>(emptyMap())
    val categoryResults: StateFlow<Map<String, List<AudiobookSummary>>> = _categoryResults

    private val _selectedCategory = MutableStateFlow<CategoryInfo?>(null)
    val selectedCategory: StateFlow<CategoryInfo?> = _selectedCategory

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val categories = POPULAR_CATEGORIES

    val recentAudiobooks = database.recentAudiobooks()
    val bookmarkedAudiobooks = database.bookmarkedAudiobooks()

    init {
        loadLatest()
        preloadFeaturedCategories()
    }

    fun selectCategory(category: CategoryInfo?) {
        _selectedCategory.value = category
        if (category != null && !_categoryResults.value.containsKey(category.slug)) {
            loadCategory(category.slug)
        }
    }

    fun refresh() {
        loadLatest()
        _selectedCategory.value?.let { loadCategory(it.slug) }
        preloadFeaturedCategories()
    }

    private fun preloadFeaturedCategories() {
        listOf("sci-fi", "thriller", "history").forEach { slug ->
            if (!_categoryResults.value.containsKey(slug)) {
                loadCategory(slug)
            }
        }
    }

    fun loadLatest(page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            runCatching {
                val result = client.getLatestAudiobooks(page)
                _latest.value = result.results
            }
            _isLoading.value = false
        }
    }

    fun loadCategory(slug: String, page: Int = 1) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            runCatching {
                val result = client.getCategoryAudiobooks(slug, page)
                _categoryResults.value = _categoryResults.value + (slug to result.results)
            }
            _isLoading.value = false
        }
    }
}

@HiltViewModel
class AudiobookDetailViewModel @Inject constructor(
    private val client: AudioBookBayClient,
    private val database: MusicDatabase,
    private val torrentEngine: TorrentStreamEngine,
) : ViewModel() {
    private val _detail = MutableStateFlow<AudiobookDetail?>(null)
    val detail: StateFlow<AudiobookDetail?> = _detail

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val streamState: StateFlow<TorrentStreamState> = torrentEngine.streamState
    val torrentStatus = torrentEngine.status

    @OptIn(ExperimentalCoroutinesApi::class)
    val audiobookEntity: StateFlow<AudiobookEntity?> =
        _detail.flatMapLatest { detail ->
            if (detail == null) flowOf(null)
            else database.audiobookEntityFlow(detail.id)
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun loadDetail(slug: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            runCatching {
                val detail = client.getAudiobookDetail(slug)
                _detail.value = detail

                if (detail != null) {
                    database.upsert(
                        AudiobookEntity(
                            id = detail.id,
                            title = detail.title,
                            author = detail.author,
                            narrator = detail.narrator,
                            cover = detail.cover,
                            format = detail.format,
                            fileSize = detail.fileSize,
                            description = detail.description,
                            duration = detail.duration,
                            infoHash = detail.infoHash,
                            magnetUri = detail.magnetUri,
                        )
                    )

                    database.deleteChapters(detail.id)
                    database.insertChapters(
                        detail.files.mapIndexed { index, file ->
                            AudiobookChapterEntity(
                                audiobookId = detail.id,
                                index = index,
                                fileName = file.name,
                            )
                        }
                    )
                }
            }
            _isLoading.value = false
        }
    }

    fun streamChapter(fileIndex: Int = 0) {
        val detail = _detail.value ?: return
        if (detail.magnetUri.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            torrentEngine.streamFromMagnet(detail.magnetUri, fileIndex)
            database.upsert(
                (database.audiobookEntity(detail.id) ?: return@launch).copy(
                    lastPlayedAt = LocalDateTime.now(),
                    lastPlayChapterIndex = fileIndex,
                )
            )
        }
    }

    fun toggleBookmark() {
        val detail = _detail.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val entity = database.audiobookEntity(detail.id) ?: return@launch
            database.update(entity.toggleBookmark())
        }
    }
}

@HiltViewModel
class AudiobookLibraryViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {
    val allAudiobooks = database.allAudiobooks()
    val bookmarked = database.bookmarkedAudiobooks()
    val recent = database.recentAudiobooks()
    val downloaded = database.downloadedAudiobooks()
}

