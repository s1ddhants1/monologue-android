package com.monologue.music.api.audiobookbay

import androidx.compose.runtime.Immutable

@Immutable
data class AudiobookFile(
    val name: String,
    val size: String? = null,
    val index: Int = 0,
    val extension: String? = null,
    val isAudio: Boolean = false,
    val duration: String? = null,
)

@Immutable
data class AudiobookSummary(
    val id: String,
    val title: String,
    val author: String? = null,
    val cover: String,
    val format: String? = null,
    val bitrate: String? = null,
    val fileSize: String? = null,
    val postedDate: String? = null,
    val url: String,
    val categories: List<String> = emptyList(),
    val seeders: Int = 0,
    val leechers: Int = 0,
) {
    val slug: String get() = id
}

@Immutable
data class AudiobookDetail(
    val id: String,
    val title: String,
    val author: String? = null,
    val narrator: String? = null,
    val cover: String,
    val format: String? = null,
    val bitrate: String? = null,
    val fileSize: String? = null,
    val description: String = "",
    val duration: String? = null,
    val infoHash: String? = null,
    val trackers: List<String> = emptyList(),
    val magnetUri: String = "",
    val isAbridged: Boolean = false,
    val files: List<AudiobookFile> = emptyList(),
    val totalAudioFiles: Int = 0,
    val isMultiPart: Boolean = false,
    val url: String,
    val categories: List<String> = emptyList(),
    val seeders: Int = 0,
    val leechers: Int = 0,
) {
    val slug: String get() = id
}

data class CategoryInfo(
    val name: String,
    val slug: String,
)

val POPULAR_CATEGORIES = listOf(
    CategoryInfo("Sci-Fi", "sci-fi"),
    CategoryInfo("Fantasy", "fantasy"),
    CategoryInfo("Thriller", "thriller"),
    CategoryInfo("Mystery", "mystery"),
    CategoryInfo("Horror", "horror"),
    CategoryInfo("History", "history"),
    CategoryInfo("Biography", "biography"),
    CategoryInfo("Business", "business"),
    CategoryInfo("Romance", "romance"),
    CategoryInfo("Fiction", "general-fiction"),
    CategoryInfo("Non-Fiction", "non-fiction"),
    CategoryInfo("Young Adult", "young-adult"),
)
