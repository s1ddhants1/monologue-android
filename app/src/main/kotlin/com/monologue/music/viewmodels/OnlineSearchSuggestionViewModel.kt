/**
 * Monologue Audiobook Player
 * Licensed under GPL-3.0
 */

package com.monologue.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monologue.music.db.MusicDatabase
import com.monologue.music.db.entities.SearchHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        database: MusicDatabase,
    ) : ViewModel() {
        val query = MutableStateFlow("")
        private val _viewState = MutableStateFlow(SearchSuggestionViewState())
        val viewState = _viewState.asStateFlow()

        init {
            viewModelScope.launch {
                query
                    .flatMapLatest { query ->
                        if (query.isEmpty()) {
                            database.searchHistory().map { history ->
                                SearchSuggestionViewState(
                                    history = history,
                                )
                            }
                        } else {
                            database
                                .searchHistory(query)
                                .map { history ->
                                    SearchSuggestionViewState(
                                        history = history.take(10),
                                    )
                                }
                        }
                    }.collect {
                        _viewState.value = it
                    }
            }
        }
    }

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
)
