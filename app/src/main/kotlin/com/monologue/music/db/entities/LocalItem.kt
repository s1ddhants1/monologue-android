/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.monologue.music.db.entities

sealed class LocalItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnailUrl: String?
}

data class AudiobookItem(
    val audiobook: AudiobookEntity,
) : LocalItem() {
    override val id: String
        get() = audiobook.id
    override val title: String
        get() = audiobook.title
    override val thumbnailUrl: String?
        get() = audiobook.cover.takeIf { it.isNotEmpty() }
}

