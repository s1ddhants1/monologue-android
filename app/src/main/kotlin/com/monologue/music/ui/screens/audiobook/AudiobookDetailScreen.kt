/**
 * Monologue Audiobook Player
 * Licensed under GPL-3.0
 */

package com.monologue.music.ui.screens.audiobook

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.monologue.music.LocalPlayerAwareWindowInsets
import com.monologue.music.LocalPlayerConnection
import com.monologue.music.R
import com.monologue.music.api.audiobookbay.AudioBookBayParser
import com.monologue.music.extensions.toMediaItem
import com.monologue.music.models.MediaMetadata
import com.monologue.music.playback.queues.ListQueue
import com.monologue.music.torrent.TorrentStreamState
import com.monologue.music.ui.component.ExpandableText
import com.monologue.music.utils.makeTimeString
import com.monologue.music.viewmodels.AudiobookDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookDetailScreen(
    slug: String,
    navController: NavController,
    viewModel: AudiobookDetailViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val streamState by viewModel.streamState.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val audiobookEntity by viewModel.audiobookEntity.collectAsStateWithLifecycle()
    val isBookmarked = audiobookEntity?.bookmarkedAt != null
    val isCurrentBook = detail?.let { book ->
        mediaMetadata?.album?.id == book.id || mediaMetadata?.title == book.title || book.files.any { it.name == mediaMetadata?.title }
    } ?: false

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

    LaunchedEffect(slug) {
        viewModel.loadDetail(slug)
    }

    // Connect ready stream to ExoPlayer playback. Seeding must not restart playback from the
    // local file: it uses the same data the stream already served and would reset the position.
    val playUri = (streamState as? TorrentStreamState.Ready)?.let { state ->
        state.streamUrl.ifEmpty { state.filePath }
    }
    val playFileIndex = (streamState as? TorrentStreamState.Ready)?.fileIndex ?: 0
    LaunchedEffect(playUri, playFileIndex) {
        val uri = playUri ?: return@LaunchedEffect
        val currentDetail = detail ?: return@LaunchedEffect
        if (uri.isEmpty()) return@LaunchedEffect
        val chapterTitle = currentDetail.files.getOrNull(playFileIndex)?.name ?: currentDetail.title

        val metadata = MediaMetadata(
            id = uri,
            title = chapterTitle,
            artists = listOfNotNull(currentDetail.author).map {
                MediaMetadata.Artist(id = it, name = it)
            },
            duration = 0,
            thumbnailUrl = currentDetail.cover,
            album = MediaMetadata.Album(id = currentDetail.id, title = currentDetail.title),
        )

        val mediaItem = metadata.toMediaItem()
        playerConnection.playQueue(
            ListQueue(
                title = currentDetail.title,
                items = listOf(mediaItem),
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    } else {
                        Text(
                            text = detail?.title ?: stringResource(R.string.audiobook_detail),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                query = TextFieldValue()
                            } else {
                                navController.navigateUp()
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.dismiss),
                        )
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime),
    ) { paddingValues ->
        if (isLoading && detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            val book = detail
            if (book == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.no_audiobooks_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            } else {
                val filteredFiles = remember(book.files, query.text) {
                    if (query.text.isEmpty()) {
                        book.files.mapIndexed { index, file -> index to file }
                    } else {
                        book.files.mapIndexed { index, file -> index to file }.filter { (_, file) ->
                            file.name.contains(query.text, ignoreCase = true)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom)
                        .asPaddingValues(),
                ) {
                    if (!isSearching) {
                        item(key = "audiobook_header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(220.dp)
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    ),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                AsyncImage(
                                    model = book.cover,
                                    contentDescription = book.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )

                            book.author?.let { author ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = author,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            book.narrator?.let { narrator ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${stringResource(R.string.narrator)}: $narrator",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }


                        }
                    }

                    // Stock Action Buttons Row (Bookmark, Play/Pause, Share)
                    item(key = "action_buttons_row") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Bookmark Button - Smaller secondary button (48.dp)
                            Surface(
                                onClick = { viewModel.toggleBookmark() },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isBookmarked) R.drawable.favorite else R.drawable.favorite_border,
                                        ),
                                        contentDescription = stringResource(R.string.bookmarked),
                                        tint = if (isBookmarked) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }

                            // Play Button - Larger primary circular button (72.dp)
                            val isBuffering = streamState is TorrentStreamState.Connecting ||
                                streamState is TorrentStreamState.FetchingMetadata ||
                                streamState is TorrentStreamState.Buffering

                            Surface(
                                onClick = {
                                    when {
                                        isBuffering -> Unit
                                        isCurrentBook && streamState !is TorrentStreamState.Idle &&
                                            streamState !is TorrentStreamState.Error -> {
                                            playerConnection.togglePlayPause()
                                        }
                                        else -> viewModel.streamChapter(audiobookEntity?.lastPlayChapterIndex ?: 0)
                                    }
                                },
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier.size(72.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    if (isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 3.dp,
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(
                                                if (isCurrentBook && isPlaying) R.drawable.pause else R.drawable.play,
                                            ),
                                            contentDescription = stringResource(R.string.play),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(32.dp),
                                        )
                                    }
                                }
                            }

                            // Share Button - Smaller secondary button (48.dp)
                            val context = LocalContext.current
                            Surface(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "${book.title}\n${book.magnetUri}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.share),
                                        contentDescription = stringResource(R.string.share),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }

                        if (streamState is TorrentStreamState.Connecting ||
                            streamState is TorrentStreamState.FetchingMetadata ||
                            streamState is TorrentStreamState.Buffering
                        ) {
                            val progressText = when (val s = streamState) {
                                is TorrentStreamState.Connecting -> stringResource(R.string.connecting)
                                is TorrentStreamState.FetchingMetadata -> stringResource(R.string.fetching_metadata)
                                is TorrentStreamState.Buffering -> "${stringResource(R.string.buffering)} • ${(s.progress * 100).toInt()}%"
                                else -> ""
                            }
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Synopsis / Description
                    if (book.description.isNotEmpty()) {
                        item(key = "audiobook_description") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.information),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                ExpandableText(
                                    text = book.description,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    }

                    // Chapters section
                    if (book.files.isNotEmpty()) {
                        if (filteredFiles.isNotEmpty()) {
                            item(key = "chapters_header") {
                                Text(
                                    text = if (isSearching) {
                                        "${stringResource(R.string.chapters)} (${filteredFiles.size}/${book.files.size})"
                                    } else {
                                        "${stringResource(R.string.chapters)} (${book.files.size})"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                )
                            }

                            items(filteredFiles, key = { (index, _) -> "chapter_$index" }) { (index, file) ->
                                val isPlayingThis = (mediaMetadata?.title == file.name) || ((streamState as? TorrentStreamState.Ready)?.fileIndex == index)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.streamChapter(index) }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.width(32.dp),
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        val trackDuration = if (isPlayingThis) {
                                            val playerDuration = try {
                                                playerConnection.player.duration
                                            } catch (_: Exception) {
                                                -1L
                                            }
                                            if (playerDuration > 0 && playerDuration != androidx.media3.common.C.TIME_UNSET) {
                                                makeTimeString(playerDuration)
                                            } else {
                                                file.duration ?: AudioBookBayParser.computeTrackDuration(
                                                    fileName = file.name,
                                                    fileSizeStr = file.size,
                                                    bitrateStr = book.bitrate,
                                                    totalDurationStr = book.duration,
                                                    totalAudioFiles = book.files.size,
                                                ) ?: file.size
                                            }
                                        } else {
                                            file.duration ?: AudioBookBayParser.computeTrackDuration(
                                                fileName = file.name,
                                                fileSizeStr = file.size,
                                                bitrateStr = book.bitrate,
                                                totalDurationStr = book.duration,
                                                totalAudioFiles = book.files.size,
                                            ) ?: file.size
                                        }
                                        trackDuration?.let { text ->
                                            Text(
                                                text = text,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                    }

                                    if (isPlayingThis) {
                                        Icon(
                                            painter = painterResource(R.drawable.volume_up),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        } else if (isSearching) {
                            item(key = "no_search_results") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.no_results_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
