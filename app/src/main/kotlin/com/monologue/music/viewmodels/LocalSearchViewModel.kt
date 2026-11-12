/**
 * Monologue Audiobook Player
 * Licensed under GPL-3.0
 */

package com.monologue.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monologue.music.db.MusicDatabase
import com.monologue.music.db.entities.AudiobookEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalSearchViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(LocalAudiobookFilter.ALL)

    val result =
        combine(
            query,
            filter,
        ) { q, f ->
            q to f
        }.flatMapLatest { (q, f) ->
            if (q.isEmpty()) {
                flowOf(LocalAudiobookSearchResult("", f, emptyList()))
            } else {
                database.searchAudiobooks(q).map { list ->
                    val filtered = when (f) {
                        LocalAudiobookFilter.ALL -> list
                        LocalAudiobookFilter.DOWNLOADED -> list.filter { it.downloadPath != null }
                        LocalAudiobookFilter.BOOKMARKED -> list.filter { it.bookmarkedAt != null }
                    }
                    LocalAudiobookSearchResult(
                        query = q,
                        filter = f,
                        items = filtered,
                    )
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            LocalAudiobookSearchResult("", filter.value, emptyList())
        )
}

enum class LocalAudiobookFilter {
    ALL,
    DOWNLOADED,
    BOOKMARKED,
}

data class LocalAudiobookSearchResult(
    val query: String,
    val filter: LocalAudiobookFilter,
    val items: List<AudiobookEntity>,
)
