/**
 * Monologue Audiobook Player
 * Licensed under GPL-3.0
 */

package com.monologue.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.monologue.music.LocalPlayerAwareWindowInsets
import com.monologue.music.R
import com.monologue.music.constants.AudiobookFilter
import com.monologue.music.constants.AudiobookFilterKey
import com.monologue.music.constants.AudiobookSortDescendingKey
import com.monologue.music.constants.AudiobookSortType
import com.monologue.music.constants.AudiobookSortTypeKey
import com.monologue.music.constants.AudiobookViewTypeKey
import com.monologue.music.constants.CONTENT_TYPE_ALBUM
import com.monologue.music.constants.CONTENT_TYPE_HEADER
import com.monologue.music.constants.GridItemSize
import com.monologue.music.constants.GridItemsSizeKey
import com.monologue.music.constants.GridThumbnailHeight
import com.monologue.music.constants.LibraryViewType
import com.monologue.music.extensions.matchesNormalizedQuery
import com.monologue.music.extensions.normalizeForSearch
import com.monologue.music.extensions.reversed
import com.monologue.music.ui.component.ChipsRow
import com.monologue.music.ui.component.EmptyPlaceholder
import com.monologue.music.ui.component.LibraryAudiobookGridItem
import com.monologue.music.ui.component.LibraryAudiobookListItem
import com.monologue.music.ui.component.LibrarySearchEmptyPlaceholder
import com.monologue.music.ui.component.LibrarySearchHeader
import com.monologue.music.ui.component.LocalMenuState
import com.monologue.music.ui.component.SortHeader
import com.monologue.music.utils.rememberEnumPreference
import com.monologue.music.utils.rememberPreference
import com.monologue.music.viewmodels.AudiobookLibraryViewModel
import java.text.Collator
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryAudiobooksScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: AudiobookLibraryViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var viewType by rememberEnumPreference(AudiobookViewTypeKey, LibraryViewType.GRID)
    var filter by rememberEnumPreference(AudiobookFilterKey, AudiobookFilter.ALL)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        AudiobookSortTypeKey,
        AudiobookSortType.CREATE_DATE,
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        AudiobookSortDescendingKey,
        true,
    )
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val allAudiobooks by viewModel.allAudiobooks.collectAsStateWithLifecycle(emptyList())
    val bookmarked by viewModel.bookmarked.collectAsStateWithLifecycle(emptyList())
    val recent by viewModel.recent.collectAsStateWithLifecycle(emptyList())
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle(emptyList())

    val rawList = remember(filter, allAudiobooks, bookmarked, recent, downloaded) {
        when (filter) {
            AudiobookFilter.ALL -> allAudiobooks
            AudiobookFilter.BOOKMARKED -> bookmarked
            AudiobookFilter.RECENT -> recent
            AudiobookFilter.DOWNLOADED -> downloaded
        }
    }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = remember(searchQuery) { searchQuery.normalizeForSearch() }

    val locale = LocalLocale.current.platformLocale
    val collator = remember(locale) {
        Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
    }

    val filteredAudiobooks = remember(rawList, sortType, sortDescending, normalizedQuery, collator) {
        val sorted = when (sortType) {
            AudiobookSortType.CREATE_DATE -> rawList.sortedBy { it.bookmarkedAt ?: it.lastPlayedAt ?: LocalDateTime.MIN }
            AudiobookSortType.NAME -> rawList.sortedWith(compareBy(collator) { it.title })
            AudiobookSortType.LAST_PLAYED -> rawList.sortedBy { it.lastPlayedAt ?: LocalDateTime.MIN }
        }.reversed(sortDescending)

        if (normalizedQuery.isBlank()) {
            sorted
        } else {
            sorted.filter {
                matchesNormalizedQuery(normalizedQuery, it.title, it.author.orEmpty())
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop = backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val filterContent = @Composable {
        Row {
            Spacer(Modifier.width(12.dp))
            FilterChip(
                label = { Text(stringResource(R.string.audiobooks)) },
                selected = true,
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
                onClick = onDeselect,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = "",
                    )
                },
            )
            ChipsRow(
                chips = listOf(
                    AudiobookFilter.ALL to stringResource(R.string.audiobook_all),
                    AudiobookFilter.BOOKMARKED to stringResource(R.string.bookmarked),
                    AudiobookFilter.RECENT to stringResource(R.string.in_progress),
                    AudiobookFilter.DOWNLOADED to stringResource(R.string.downloaded),
                ),
                currentValue = filter,
                onValueUpdate = {
                    filter = it
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val headerContent = @Composable {
        LibrarySearchHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onBack = {
                isSearchActive = false
                searchQuery = ""
            },
            keyboardController = keyboardController,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { type ->
                    when (type) {
                        AudiobookSortType.CREATE_DATE -> R.string.sort_by_create_date
                        AudiobookSortType.NAME -> R.string.sort_by_name
                        AudiobookSortType.LAST_PLAYED -> R.string.sort_by_last_played
                    }
                },
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(R.plurals.n_audiobook, filteredAudiobooks.size, filteredAudiobooks.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            IconButton(
                onClick = { isSearchActive = true },
                modifier = Modifier.padding(start = 8.dp).size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = stringResource(R.string.search),
                )
            }

            IconButton(
                onClick = {
                    viewType = viewType.toggle()
                },
                modifier = Modifier.padding(end = 8.dp).size(40.dp),
            ) {
                Icon(
                    painter = painterResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.drawable.list
                            LibraryViewType.GRID -> R.drawable.grid_view
                        },
                    ),
                    contentDescription = stringResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.string.switch_to_grid_view
                            LibraryViewType.GRID -> R.string.switch_to_list_view
                        },
                    ),
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (filteredAudiobooks.isEmpty()) {
                        item(key = "empty_placeholder") {
                            if (searchQuery.isNotBlank()) {
                                LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                            } else {
                                EmptyPlaceholder(
                                    icon = R.drawable.album,
                                    text = stringResource(R.string.no_audiobooks_found),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredAudiobooks,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ALBUM },
                        ) { book ->
                            LibraryAudiobookListItem(
                                menuState = menuState,
                                audiobook = book,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(
                        minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                    ),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (filteredAudiobooks.isEmpty()) {
                        item(
                            key = "empty_placeholder",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            if (searchQuery.isNotBlank()) {
                                LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                            } else {
                                EmptyPlaceholder(
                                    icon = R.drawable.album,
                                    text = stringResource(R.string.no_audiobooks_found),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredAudiobooks,
                            key = { it.id },
                            contentType = { CONTENT_TYPE_ALBUM },
                        ) { book ->
                            LibraryAudiobookGridItem(
                                menuState = menuState,
                                audiobook = book,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}
