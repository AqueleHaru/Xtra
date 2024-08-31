package com.github.andreyasadchy.xtra.model.helix.video

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Video(
    val id: String? = null,
    @SerialName("user_id")
    val channelId: String? = null,
    @SerialName("user_login")
    val channelLogin: String? = null,
    @SerialName("user_name")
    val channelName: String? = null,
    val title: String? = null,
    @SerialName("created_at")
    val uploadDate: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null,
    @SerialName("view_count")
    val viewCount: Int? = null,
    val duration: String? = null,
)