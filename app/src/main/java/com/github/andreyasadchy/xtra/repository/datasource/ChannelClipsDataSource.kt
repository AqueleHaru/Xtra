package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.clip.Clip
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class ChannelClipsDataSource(
    private val channelId: String?,
    private val channelLogin: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val started_at: String?,
    private val ended_at: String?,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val gqlPeriod: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Clip>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Clip>) {
        loadInitial(params, callback) {
            try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                    C.GQL -> { api = C.GQL; gqlLoad(params) }
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) { api = C.HELIX; helixLoad(params) } else throw Exception()
                        C.GQL -> { api = C.GQL; gqlLoad(params) }
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
        }
    }

    private suspend fun helixLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Clip> {
        val get = helixApi.getClips(clientId = helixClientId, token = helixToken, channelId = channelId, started_at = started_at, ended_at = ended_at, limit = initialParams?.requestedLoadSize ?: rangeParams?.loadSize, cursor = offset)
        val list = mutableListOf<Clip>()
        get.data?.let { list.addAll(it) }
        val gameIds = mutableListOf<String>()
        for (i in list) {
            i.broadcaster_login = channelLogin
            i.game_id?.let { gameIds.add(it) }
        }
        if (gameIds.isNotEmpty()) {
            val games = helixApi.getGames(helixClientId, helixToken, gameIds).data
            if (games != null) {
                for (i in games) {
                    val items = list.filter { it.game_id == i.id }
                    for (item in items) {
                        item.game_name = i.name
                    }
                }
            }
        }
        offset = get.pagination?.cursor
        return list
    }

    private suspend fun gqlLoad(initialParams: LoadInitialParams? = null, rangeParams: LoadRangeParams? = null): List<Clip> {
        val get = gqlApi.loadChannelClips(gqlClientId, channelLogin, gqlPeriod, initialParams?.requestedLoadSize ?: rangeParams?.loadSize, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Clip>) {
        loadRange(params, callback) {
            if (!offset.isNullOrBlank()) {
                when (api) {
                    C.HELIX -> helixLoad(rangeParams = params)
                    C.GQL -> gqlLoad(rangeParams = params)
                    else -> listOf()
                }
            } else listOf()
        }
    }

    class Factory(
        private val channelId: String?,
        private val channelLogin: String?,
        private val helixClientId: String?,
        private val helixToken: String?,
        private val started_at: String?,
        private val ended_at: String?,
        private val helixApi: HelixApi,
        private val gqlClientId: String?,
        private val gqlPeriod: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Clip, ChannelClipsDataSource>() {

        override fun create(): DataSource<Int, Clip> =
                ChannelClipsDataSource(channelId, channelLogin, helixClientId, helixToken, started_at, ended_at, helixApi, gqlClientId, gqlPeriod, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
