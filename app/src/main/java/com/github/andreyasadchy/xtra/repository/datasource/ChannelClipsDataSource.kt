package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.type.ClipsPeriod
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.google.gson.JsonObject

class ChannelClipsDataSource(
    private val channelId: String?,
    private val channelLogin: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val started_at: String?,
    private val ended_at: String?,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val gqlQueryPeriod: ClipsPeriod?,
    private val gqlPeriod: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>) : PagingSource<Int, Clip>() {
    private var api: String? = null
    private var offset: String? = null
    private var nextPage: Boolean = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Clip> {
        return try {
            val response = try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                    C.GQL -> { api = C.GQL; gqlLoad(params) }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                        C.GQL -> { api = C.GQL; gqlLoad(params) }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    try {
                        when (apiPref.elementAt(2)?.second) {
                            C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                            C.GQL_QUERY -> { api = C.GQL_QUERY; gqlQueryLoad(params) }
                            C.GQL -> { api = C.GQL; gqlLoad(params) }
                            else -> throw Exception()
                        }
                    } catch (e: Exception) {
                        listOf()
                    }
                }
            }
            LoadResult.Page(
                data = response,
                prevKey = null,
                nextKey = if (!offset.isNullOrBlank() && (api == C.HELIX || nextPage)) {
                    nextPage = false
                    (params.key ?: 1) + 1
                } else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun helixLoad(params: LoadParams<Int>): List<Clip> {
        val get = helixApi.getClips(
            clientId = helixClientId,
            token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
            channelId = channelId,
            started_at = started_at,
            ended_at = ended_at,
            limit = params.loadSize,
            cursor = offset
        )
        val list = mutableListOf<Clip>()
        get.data.let { list.addAll(it) }
        val gameIds = mutableListOf<String>()
        for (i in list) {
            i.channelLogin = channelLogin
            i.gameId?.let { gameIds.add(it) }
        }
        if (gameIds.isNotEmpty()) {
            val games = helixApi.getGames(
                clientId = helixClientId,
                token = helixToken?.let { TwitchApiHelper.addTokenPrefixHelix(it) },
                ids = gameIds
            ).data
            for (i in games) {
                val items = list.filter { it.gameId == i.gameId }
                for (item in items) {
                    item.gameName = i.gameName
                }
            }
        }
        offset = get.cursor
        return list
    }

    private suspend fun gqlQueryLoad(params: LoadParams<Int>): List<Clip> {
        val context = XtraApp.INSTANCE.applicationContext
        val get = gqlApi.loadQueryUserClips(
            clientId = gqlClientId,
            query = context.resources.openRawResource(R.raw.userclips).bufferedReader().use { it.readText() },
            variables = JsonObject().apply {
                addProperty("id", if (!channelId.isNullOrBlank()) channelId else null)
                addProperty("login", if (channelId.isNullOrBlank() && !channelLogin.isNullOrBlank()) channelLogin else null)
                addProperty("sort", gqlQueryPeriod.toString())
                addProperty("first", params.loadSize)
                addProperty("after", offset)
            })
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    private suspend fun gqlLoad(params: LoadParams<Int>): List<Clip> {
        val get = gqlApi.loadChannelClips(gqlClientId, channelLogin, gqlPeriod, params.loadSize, offset)
        offset = get.cursor
        nextPage = get.hasNextPage ?: true
        return get.data
    }

    override fun getRefreshKey(state: PagingState<Int, Clip>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
