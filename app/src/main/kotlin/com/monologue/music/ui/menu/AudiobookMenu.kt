/**
 * Monologue Audiobook Player
 * Licensed under GPL-3.0
 */

package com.monologue.music.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.monologue.music.LocalDatabase
import com.monologue.music.LocalNavController
import com.monologue.music.R
import com.monologue.music.db.entities.AudiobookEntity
import com.monologue.music.ui.component.AudiobookListItem
import com.monologue.music.ui.component.Material3MenuGroup
import com.monologue.music.ui.component.Material3MenuItemData
import com.monologue.music.ui.component.NewAction
import com.monologue.music.ui.component.NewActionGrid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun AudiobookMenu(
    audiobook: AudiobookEntity,
    onDismiss: () -> Unit,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    AudiobookListItem(
        audiobook = audiobook,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        database.update(audiobook.toggleBookmark())
                    }
                },
            ) {
                Icon(
                    painter = painterResource(
                        if (audiobook.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border
                    ),
                    tint = if (audiobook.bookmarkedAt != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    contentDescription = null,
                )
            }
        },
    )

    HorizontalDivider()

    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(
        contentPadding = PaddingValues(
            start = 0.dp,
            top = 0.dp,
            end = 0.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            NewActionGrid(
                actions = buildList {
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(R.string.play),
                            onClick = {
                                onDismiss()
                                val encoded = URLEncoder.encode(audiobook.id, "UTF-8")
                                navController.navigate("audiobook/$encoded")
                            },
                        )
                    )
                    add(
                        NewAction(
                            icon = {
                                Icon(
                                    painter = painterResource(
                                        if (audiobook.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = if (audiobook.bookmarkedAt != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            text = stringResource(
                                if (audiobook.bookmarkedAt != null) R.string.remove_from_library else R.string.add_to_library
                            ),
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.update(audiobook.toggleBookmark())
                                }
                                onDismiss()
                            },
                        )
                    )
                },
            )
        }

        item {
            Material3MenuGroup(
                items = listOf(
                    Material3MenuItemData(
                        title = { Text(stringResource(R.string.audiobook_detail)) },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.info),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            onDismiss()
                            val encoded = URLEncoder.encode(audiobook.id, "UTF-8")
                            navController.navigate("audiobook/$encoded")
                        },
                    ),
                ),
            )
        }
    }
}
