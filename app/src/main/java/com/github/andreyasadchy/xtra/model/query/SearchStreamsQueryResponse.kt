package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.stream.Stream

data class SearchStreamsQueryResponse(val data: List<Stream>, val cursor: String?, val hasNextPage: Boolean?)