package com.github.andreyasadchy.xtra.model.gql.clip

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class ClipUrlsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val clip: Clip,
    )

    @Serializable
    class Clip(
        val playbackAccessToken: PlaybackAccessToken? = null,
        val videoQualities: List<Quality>,
    )

    @Serializable
    class PlaybackAccessToken(
        val value: String? = null,
        val signature: String? = null,
    )

    @Serializable
    class Quality(
        val sourceURL: String,
        val frameRate: Float? = null,
        val quality: String? = null,
    )
}