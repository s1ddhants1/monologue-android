package com.monologue.kugou.models

import kotlinx.serialization.Serializable

@Serializable
data class DownloadLyricsResponse(
    val content: String,
)
