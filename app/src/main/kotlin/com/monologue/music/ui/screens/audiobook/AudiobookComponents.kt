/**
 * Monologue Audiobook Player
 * Licensed under GPL-3.0
 */

package com.monologue.music.ui.screens.audiobook

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.monologue.music.R
import com.monologue.music.api.audiobookbay.AudiobookSummary
import com.monologue.music.api.audiobookbay.CategoryInfo
import com.monologue.music.constants.ThumbnailCornerRadius
import com.monologue.music.db.entities.AudiobookEntity
import com.monologue.music.torrent.TorrentStatus
import com.monologue.music.torrent.TorrentStreamState
import com.monologue.music.ui.component.GridItem
import java.util.Locale

@Composable
fun AudiobookEntityGridItem(
    audiobook: AudiobookEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GridItem(
        modifier = modifier.clickable(onClick = onClick),
        title = {
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee().fillMaxWidth(),
            )
        },
        subtitle = {
            val author = audiobook.author.orEmpty()
            if (author.isNotEmpty()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        thumbnailContent = {
            AsyncImage(
                model = audiobook.cover,
                contentDescription = audiobook.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        },
    )
}

@Composable
fun AudiobookGenresGrid(
    categories: List<CategoryInfo>,
    selectedCategory: CategoryInfo?,
    onCategoryClick: (CategoryInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.height((48.dp + 8.dp) * 2 + 16.dp),
    ) {
        items(categories, key = { it.slug }) { category ->
            val isSelected = selectedCategory?.slug == category.slug
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .height(48.dp)
                    .width(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer
                    )
                    .clickable { onCategoryClick(category) }
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun AudiobookGridItem(
    audiobook: AudiobookSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GridItem(
        modifier = modifier.clickable(onClick = onClick),
        title = {
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee().fillMaxWidth(),
            )
        },
        subtitle = {
            val author = audiobook.author.orEmpty()
            if (author.isNotEmpty()) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        thumbnailContent = {
            AsyncImage(
                model = audiobook.cover,
                contentDescription = audiobook.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        },
    )
}

@Composable
fun AudiobookListItem(
    audiobook: AudiobookSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .size(64.dp)
                .shadow(4.dp, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            AsyncImage(
                model = audiobook.cover,
                contentDescription = audiobook.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            audiobook.author?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                audiobook.fileSize?.let { size ->
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    text = "${audiobook.seeders} S / ${audiobook.leechers} L",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
fun TorrentStreamCard(
    streamState: TorrentStreamState,
    status: TorrentStatus,
    onStartStream: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.torrent_health),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                val stateLabel = when (streamState) {
                    is TorrentStreamState.Idle -> "Idle"
                    is TorrentStreamState.Connecting -> stringResource(R.string.connecting)
                    is TorrentStreamState.FetchingMetadata -> stringResource(R.string.fetching_metadata)
                    is TorrentStreamState.Buffering -> stringResource(R.string.buffering)
                    is TorrentStreamState.Ready -> "Playing / Caching"
                    is TorrentStreamState.Seeding -> stringResource(R.string.seeding)
                    is TorrentStreamState.Error -> "Error"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (streamState) {
                        is TorrentStreamState.Ready, is TorrentStreamState.Seeding -> MaterialTheme.colorScheme.primaryContainer
                        is TorrentStreamState.Buffering, is TorrentStreamState.Connecting, is TorrentStreamState.FetchingMetadata -> MaterialTheme.colorScheme.secondaryContainer
                        is TorrentStreamState.Error -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (streamState) {
                            is TorrentStreamState.Ready, is TorrentStreamState.Seeding -> MaterialTheme.colorScheme.onPrimaryContainer
                            is TorrentStreamState.Buffering, is TorrentStreamState.Connecting, is TorrentStreamState.FetchingMetadata -> MaterialTheme.colorScheme.onSecondaryContainer
                            is TorrentStreamState.Error -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (streamState) {
                is TorrentStreamState.Idle -> {
                    FilledTonalButton(
                        onClick = onStartStream,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.stream_audiobook))
                    }
                }

                is TorrentStreamState.Connecting, is TorrentStreamState.FetchingMetadata -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (streamState is TorrentStreamState.Connecting) {
                            stringResource(R.string.connecting)
                        } else {
                            stringResource(R.string.fetching_metadata)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                is TorrentStreamState.Buffering -> {
                    LinearProgressIndicator(
                        progress = { streamState.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${(streamState.progress * 100).toInt()}% • ${streamState.peers} ${stringResource(R.string.peers)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = formatSpeed(streamState.downloadSpeed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                is TorrentStreamState.Ready -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${status.peers} ${stringResource(R.string.peers)} • ${(status.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "↓ ${formatSpeed(streamState.downloadSpeed)}  ↑ ${formatSpeed(streamState.uploadSpeed)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                is TorrentStreamState.Seeding -> {
                    FilledTonalButton(
                        onClick = onStartStream,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.play))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Seeding • ${streamState.peers} ${stringResource(R.string.peers)} • ↑ ${formatSpeed(streamState.uploadSpeed)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                is TorrentStreamState.Error -> {
                    Text(
                        text = streamState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onStartStream,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(text = "Retry")
                    }
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec.toFloat() / (1024 * 1024))
        bytesPerSec >= 1024 -> String.format(Locale.US, "%d KB/s", bytesPerSec / 1024)
        else -> "$bytesPerSec B/s"
    }
}
