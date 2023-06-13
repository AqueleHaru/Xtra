package com.github.andreyasadchy.xtra.model.gql.chat

data class GlobalCheerEmotesDataResponse(val config: CheerConfig, val tiers: List<CheerTier>) {

    data class CheerConfig(
        val backgrounds: List<String>,
        val colors: Map<Int, String>,
        val scales: List<String>,
        val types: Map<String, String>)

    data class CheerTier(
        val template: String,
        val prefix: String,
        val tierBits: Int)
}