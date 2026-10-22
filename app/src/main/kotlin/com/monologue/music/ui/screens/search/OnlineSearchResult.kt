/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.monologue.music.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import com.monologue.music.LocalNavController
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_EPISODE
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_PROFILE
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.monologue.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.monologue.innertube.YouTubeConstants
import com.monologue.innertube.models.AlbumItem
import com.monologue.innertube.models.ArtistItem
import com.monologue.innertube.models.EpisodeItem
import com.monologue.innertube.models.PlaylistItem
import com.monologue.innertube.models.PodcastItem
import com.monologue.innertube.models.SongItem
import com.monologue.innertube.models.WatchEndpoint
import com.monologue.innertube.models.YTItem
import com.monologue.music.LocalDatabase
import com.monologue.music.LocalPlayerConnection
import com.monologue.music.R
import com.monologue.music.constants.AutoRadioQueueKey
import com.monologue.music.constants.HideVideoSongsKey
import com.monologue.music.constants.MiniPlayerBottomSpacing
import com.monologue.music.constants.MiniPlayerHeight
import com.monologue.music.constants.NavigationBarHeight
import com.monologue.music.constants.PauseSearchHistoryKey
import com.monologue.music.db.entities.SearchHistory
import com.monologue.music.extensions.toMediaItem
import com.monologue.music.models.toMediaMetadata
import com.monologue.music.playback.queues.ListQueue
import com.monologue.music.playback.queues.YouTubeQueue
import com.monologue.music.ui.component.ChipsRow
import com.monologue.music.ui.component.EmptyPlaceholder
import com.monologue.music.ui.component.HideOnScrollFAB
import com.monologue.music.ui.component.LocalMenuState
import com.monologue.music.ui.component.NavigationTitle
import com.monologue.music.ui.component.YouTubeListItem
import com.monologue.music.ui.component.shimmer.ListItemPlaceHolder
import com.monologue.music.ui.component.shimmer.ShimmerHost
import com.monologue.music.ui.menu.YouTubeAlbumMenu
import com.monologue.music.ui.menu.YouTubeArtistMenu
import com.monologue.music.ui.menu.YouTubePlaylistMenu
import com.monologue.music.ui.menu.YouTubeSongMenu
import com.monologue.music.utils.SearchRoutes
import com.monologue.music.utils.rememberPreference
import com.monologue.music.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    pureBlack: Boolean = false,
    savedStateHandle: SavedStateHandle? = null
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scrollToTopCount by savedStateHandle
        ?.getStateFlow("scrollToTopCount", 0)
        ?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableIntStateOf(0) }

    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
            }
            // Set focused AFTER requesting focus, not before

            isSearchFocused = true
        }
    }

    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(HideVideoSongsKey, defaultValue = false)
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    BackHandler(enabled = isSearchFocused) {
        isSearchFocused = false
        focusManager.clearFocus()
    }

    // Extract query from navigation arguments
    val encodedQuery = navController.currentBackStackEntry?.arguments?.getString("query") ?: ""
    val decodedQuery = remember(encodedQuery) { SearchRoutes.decodeQuery(encodedQuery) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(decodedQuery, TextRange(decodedQuery.length)))
    }

    val onSearch: (String) -> Unit =
        remember {
            { searchQuery ->
                if (searchQuery.isNotEmpty()) {
                    isSearchFocused = false
                    focusManager.clearFocus()

                    navController.navigate(SearchRoutes.resultRoute(searchQuery)) {
                        popUpTo(SearchRoutes.ROUTE) {
                            inclusive = true
                        }

                        if (!pauseSearchHistory) {
                            coroutineScope.launch(Dispatchers.IO) {
                                database.query {
                                    insert(SearchHistory(query = searchQuery))
                                }
                            }
                        }
                    }
                }
            }
        }

    val audiobookSearchViewModel = hiltViewModel<com.monologue.music.viewmodels.AudiobookSearchViewModel>()
    val audiobookResults by audiobookSearchViewModel.results.collectAsStateWithLifecycle()
    val isAudiobookLoading by audiobookSearchViewModel.isLoading.collectAsStateWithLifecycle()
    val filterAudiobook = remember { com.monologue.innertube.YouTube.SearchFilter("audiobook") }

    // Update query when decodedQuery changes
    LaunchedEffect(decodedQuery) {
        query = TextFieldValue(decodedQuery, TextRange(decodedQuery.length))
        if (decodedQuery.isNotBlank()) {
            audiobookSearchViewModel.search(decodedQuery)
        }
    }

    // Clear video filter if hideVideoSongs setting is enabled and filter is set to FILTER_VIDEO
    LaunchedEffect(hideVideoSongs) {
        if (hideVideoSongs && viewModel.filter.value == FILTER_VIDEO) {
            viewModel.filter.value = null
        }
    }

    val searchFilter by viewModel.filter.collectAsStateWithLifecycle()
    val searchSummary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        val longClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show {
                when (item) {
                    is SongItem -> {
                        YouTubeSongMenu(
                            song = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is AlbumItem -> {
                        YouTubeAlbumMenu(
                            albumItem = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is ArtistItem -> {
                        YouTubeArtistMenu(
                            artist = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is PlaylistItem -> {
                        YouTubePlaylistMenu(
                            playlist = item,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is PodcastItem -> {
                        YouTubePlaylistMenu(
                            playlist = item.asPlaylistItem(),
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is EpisodeItem -> {
                        YouTubeSongMenu(
                            song = item.asSongItem(),
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            }
        }
        YouTubeListItem(
            item = item,
            isActive =
                when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    is EpisodeItem -> mediaMetadata?.id == item.id
                    else -> false
                },
            isPlaying = isPlaying,
            trailingContent = {
                IconButton(
                    onClick = longClick,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                    )
                }
            },
            modifier =
                Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            if (autoRadioQueue) {
                                                YouTubeQueue(
                                                    WatchEndpoint(videoId = item.id),
                                                    item.toMediaMetadata(),
                                                )
                                            } else {
                                                ListQueue(
                                                    title = item.title,
                                                    items = listOf(item.toMediaItem())
                                                )
                                            }
                                        )
                                    }
                                }

                                is AlbumItem -> {
                                    navController.navigate("album/${item.id}")
                                }

                                is ArtistItem -> {
                                    navController.navigate("artist/${item.id}")
                                }

                                is PlaylistItem -> {
                                    navController.navigate("online_playlist/${item.id}")
                                }

                                is PodcastItem -> {
                                    navController.navigate("online_podcast/${item.id}")
                                }

                                is EpisodeItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        onLongClick = longClick,
                    ).animateItem(),
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        // Google-style SearchBar with Material 3 design
        OutlinedTextField(
            value = query,
            onValueChange = { newQuery ->
                query = newQuery
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.search_yt_music),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            leadingIcon = {
                IconButton(
                    onClick = { navController.navigateUp() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingIcon = {
                if (query.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = TextFieldValue("")
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
            keyboardActions =
                KeyboardActions(
                    onSearch = {
                        onSearch(query.text)
                    },
                ),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor =
                        if (pureBlack) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    unfocusedContainerColor =
                        if (pureBlack) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            isSearchFocused = true
                        }
                    },
        )

        // Main content area below search bar
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                val visibleChips =
                    listOf(
                        null to stringResource(R.string.filter_all),
                        FILTER_SONG to stringResource(R.string.filter_songs),
                    ).let { baseChips ->
                        if (!hideVideoSongs) {
                            baseChips + (FILTER_VIDEO to stringResource(R.string.filter_videos))
                        } else {
                            baseChips
                        }
                    } +
                        listOf(
                            FILTER_ALBUM to stringResource(R.string.filter_albums),
                            FILTER_ARTIST to stringResource(R.string.filter_artists),
                            FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                            FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
                            FILTER_PODCAST to stringResource(R.string.filter_podcasts),
                            FILTER_EPISODE to stringResource(R.string.filter_episodes),
                            filterAudiobook to stringResource(R.string.audiobooks),
                            FILTER_PROFILE to stringResource(R.string.filter_profiles),
                        )

                ChipsRow(
                    chips = visibleChips,
                    currentValue = searchFilter,
                    onValueUpdate = {
                        if (viewModel.filter.value != it) {
                            viewModel.filter.value = it
                        }
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (searchFilter == null) {
                        if (audiobookResults.isNotEmpty()) {
                            item {
                                NavigationTitle(stringResource(R.string.audiobooks))
                            }
                            items(audiobookResults.take(4), key = { "search_abb_all_${it.id}" }) { book ->
                                com.monologue.music.ui.screens.audiobook.AudiobookListItem(
                                    audiobook = book,
                                    onClick = {
                                        val encoded = java.net.URLEncoder.encode(book.slug, "UTF-8")
                                        navController.navigate("audiobook/$encoded")
                                    },
                                )
                            }
                        }

                        searchSummary?.summaries?.forEach { summary ->
                            item {
                                NavigationTitle(summary.title.toLocalizedSearchSection())
                            }

                            itemsIndexed(
                                items = summary.items,
                                key = { index, item -> "${summary.title}/${item.id}/$index" },
                                itemContent = { index, item -> ytItemContent(item) },
                            )
                        }

                        if (searchSummary?.summaries?.isEmpty() == true && audiobookResults.isEmpty()) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_results_found),
                                )
                            }
                        }
                    } else if (searchFilter == filterAudiobook) {
                        if (isAudiobookLoading && audiobookResults.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                }
                            }
                        } else if (audiobookResults.isEmpty()) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_audiobooks_found),
                                )
                            }
                        } else {
                            items(audiobookResults, key = { "search_abb_${it.id}" }) { book ->
                                com.monologue.music.ui.screens.audiobook.AudiobookListItem(
                                    audiobook = book,
                                    onClick = {
                                        val encoded = java.net.URLEncoder.encode(book.slug, "UTF-8")
                                        navController.navigate("audiobook/$encoded")
                                    },
                                )
                            }
                        }
                    } else {
                        items(
                            items = itemsPage?.items.orEmpty().distinctBy { it.id },
                            key = { "filtered_${it.id}" },
                            itemContent = ytItemContent,
                        )

                        if (itemsPage?.continuation != null) {
                            item(key = "loading") {
                                ShimmerHost {
                                    repeat(3) {
                                        ListItemPlaceHolder()
                                    }
                                }
                            }
                        }

                        if (itemsPage?.items?.isEmpty() == true) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_results_found),
                                )
                            }
                        }
                    }

                    if (searchFilter == null && searchSummary == null || searchFilter != null && searchFilter != filterAudiobook && itemsPage == null) {
                        item {
                            ShimmerHost {
                                repeat(8) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(MiniPlayerHeight + MiniPlayerBottomSpacing + NavigationBarHeight))
                    }
                }
            }
            if (isSearchFocused) {
                OnlineSearchScreen(
                    query = query.text,
                    onQueryChange = { query = it },
                    onSearch = onSearch,
                    onDismiss = {
                        isSearchFocused = false
                        focusManager.clearFocus()
                    },
                    pureBlack = pureBlack,
                )
            }
            HideOnScrollFAB(
                lazyListState = lazyListState,
                icon = R.drawable.mic,
                onClick = { navController.navigate("recognition") },
            )
        }
    }
}

@Composable
private fun String.toLocalizedSearchSection(): String =
    when (this) {
        YouTubeConstants.DEFAULT_TOP_RESULT -> stringResource(R.string.top_result)
        YouTubeConstants.DEFAULT_OTHER_RESULTS -> stringResource(R.string.other)
        "Songs" -> stringResource(R.string.songs)
        "Videos" -> stringResource(R.string.filter_videos)
        "Albums" -> stringResource(R.string.albums)
        "Artists" -> stringResource(R.string.artists)
        "Playlists" -> stringResource(R.string.playlists)
        "Podcasts" -> stringResource(R.string.filter_podcasts)
        "Episodes" -> stringResource(R.string.filter_episodes)
        "Profiles" -> stringResource(R.string.filter_profiles)
        else -> this
    }