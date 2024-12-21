package com.github.andreyasadchy.xtra.model.helix.follows

import com.github.andreyasadchy.xtra.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class FollowsResponse(
    val data: List<Follow>,
    val pagination: Pagination? = null,
)