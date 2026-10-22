package com.monologue.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Immutable
@Entity(
    tableName = "audiobook_chapter",
    primaryKeys = ["audiobookId", "index"],
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["audiobookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["audiobookId"])],
)
data class AudiobookChapterEntity(
    val audiobookId: String,
    val index: Int,
    val fileName: String,
    val filePath: String? = null,
    val duration: Long = 0,
    val lastPosition: Long = 0,
    val isDownloaded: Boolean = false,
)
