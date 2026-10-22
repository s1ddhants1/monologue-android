package com.monologue.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "audiobook")
data class AudiobookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String? = null,
    val narrator: String? = null,
    val cover: String = "",
    val format: String? = null,
    val bitrate: String? = null,
    val fileSize: String? = null,
    val description: String = "",
    val duration: String? = null,
    val infoHash: String? = null,
    val magnetUri: String = "",
    val downloadPath: String? = null,
    val downloadProgress: Float = 0f,
    val isSeeding: Boolean = false,
    val bookmarkedAt: LocalDateTime? = null,
    val lastPlayedAt: LocalDateTime? = null,
    val lastPlayChapterIndex: Int = 0,
    val lastPlayPosition: Long = 0,
) {
    fun toggleBookmark() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
    )

    val inLibrary: Boolean get() = bookmarkedAt != null
}
