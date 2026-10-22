package com.monologue.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.monologue.innertube.models.ArtistItem
import com.monologue.innertube.models.SongItem
import com.monologue.innertube.models.YTItem
import com.monologue.music.R
import com.monologue.music.constants.ThumbnailCornerRadius
import com.monologue.music.db.entities.AudiobookEntity
import com.monologue.music.ui.utils.resize

@Composable
fun SpeedDialGridItem(
    audiobook: AudiobookEntity,
    modifier: Modifier = Modifier,
    isPinned: Boolean = audiobook.inLibrary,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        AsyncImage(
            model = audiobook.cover,
            contentDescription = audiobook.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color = Color.White,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audiobook.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!audiobook.author.isNullOrBlank()) {
                    Text(
                        text = audiobook.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        if (isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(16.dp)
            )
        }
    }
}

@Composable
fun SpeedDialGridItem(
    item: YTItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Square aspect ratio
            .clip(RoundedCornerShape(ThumbnailCornerRadius))
    ) {
        // Thumbnail
        ItemThumbnail(
            thumbnailUrl = item.thumbnail?.resize(200, 200),
            isActive = isActive,
            isPlaying = isPlaying,
            shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.fillMaxSize()
        )

        // Gradient Overlay for Text Readability and Icon Contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f), // Top scrim for icon visibility on bright covers
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // Title and Chevron
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp) // Reduced padding for tighter layout
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall, // Smaller, punchier font
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            // Navigation Chevron for browsable items (Album, Playlist, Artist)
            if (item !is SongItem) {
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
        }
    }
        // Pinned Icon
        if (isPinned) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(16.dp)
            )
        }


    }
}
