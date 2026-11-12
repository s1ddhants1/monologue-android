/**
 * Monologue Audiobook Player
 * Licensed under GPL-3.0
 */

package com.monologue.music.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monologue.music.LocalNavController
import com.monologue.music.R
import com.monologue.music.ui.component.AudiobookListItem
import com.monologue.music.ui.component.ChipsRow
import com.monologue.music.ui.component.EmptyPlaceholder
import com.monologue.music.ui.component.LocalMenuState
import com.monologue.music.ui.menu.AudiobookMenu
import com.monologue.music.viewmodels.LocalAudiobookFilter
import com.monologue.music.viewmodels.LocalSearchViewModel
import kotlinx.coroutines.flow.drop
import java.net.URLEncoder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalSearchScreen(
    query: String,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
    pureBlack: Boolean,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current

    val searchFilter by viewModel.filter.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    val configuration = LocalWindowInfo.current
    val isLandscape = configuration.containerSize.width > configuration.containerSize.height

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.background)
                .let { base ->
                    if (isLandscape) {
                        base.windowInsetsPadding(
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                        )
                    } else {
                        base
                    }
                },
    ) {
        ChipsRow(
            chips =
                listOf(
                    LocalAudiobookFilter.ALL to stringResource(R.string.audiobook_all),
                    LocalAudiobookFilter.DOWNLOADED to stringResource(R.string.downloaded),
                    LocalAudiobookFilter.BOOKMARKED to stringResource(R.string.bookmarked),
                ),
            currentValue = searchFilter,
            onValueUpdate = { viewModel.filter.value = it },
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding =
                WindowInsets.systemBars
                    .only(WindowInsetsSides.Bottom)
                    .asPaddingValues(),
        ) {
            items(
                items = result.items,
                key = { "local_audiobook_${it.id}" },
            ) { item ->
                AudiobookListItem(
                    audiobook = item,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    AudiobookMenu(
                                        audiobook = item,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
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
                                    onDismiss()
                                    val encoded = URLEncoder.encode(item.id, "UTF-8")
                                    navController.navigate("audiobook/$encoded")
                                },
                                onLongClick = {
                                    menuState.show {
                                        AudiobookMenu(
                                            audiobook = item,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ).animateItem(),
                )
            }

            if (result.query.isNotEmpty() && result.items.isEmpty()) {
                item(key = "no_result") {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_audiobooks_found),
                    )
                }
            }
        }
    }
}
