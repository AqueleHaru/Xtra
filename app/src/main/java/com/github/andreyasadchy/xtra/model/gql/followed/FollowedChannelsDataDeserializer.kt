package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.ui.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class FollowedChannelsDataDeserializer : JsonDeserializer<FollowedChannelsDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FollowedChannelsDataResponse {
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("follows")?.getAsJsonArray("edges")
        val cursor = dataJson?.lastOrNull()?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        val hasNextPage = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("follows")?.get("pageInfo")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("hasNextPage")?.takeIf { !it.isJsonNull }?.asBoolean
        dataJson?.forEach { item ->
            item?.asJsonObject?.getAsJsonObject("node")?.let { obj ->
                data.add(User(
                    channelId = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    channelLogin = obj.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    channelName = obj.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    followedAt = obj.get("self")?.takeIf { it.isJsonObject }?.asJsonObject?.get("follower")?.takeIf { it.isJsonObject }?.asJsonObject?.get("followedAt")?.takeIf { !it.isJsonNull }?.asString,
                    profileImageUrl = obj.get("profileImageURL")?.takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return FollowedChannelsDataResponse(data, cursor, hasNextPage)
    }
}
