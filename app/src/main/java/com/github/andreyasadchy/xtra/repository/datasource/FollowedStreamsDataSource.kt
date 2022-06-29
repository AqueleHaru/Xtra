package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.model.helix.stream.Stream
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class FollowedStreamsDataSource(
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val userId: String?,
    private val helixClientId: String?,
    private val helixToken: String?,
    private val helixApi: HelixApi,
    private val gqlClientId: String?,
    private val gqlToken: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Stream>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Stream>) {
        loadInitial(params, callback) {
            val list = mutableListOf<Stream>()
            val localIds = localFollowsChannel.loadFollows().map { it.user_id }
            val local = if (localIds.isNotEmpty()) {
                try {
                    if (!helixToken.isNullOrBlank()) helixLocal(localIds) else throw Exception()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else mutableListOf()
            if (local.isNotEmpty()) {
                list.addAll(local)
            }
            val remote = try {
                when (apiPref.elementAt(0)?.second) {
                    C.HELIX -> if (!helixToken.isNullOrBlank()) helixInitial() else throw Exception()
                    C.GQL -> if (!gqlToken.isNullOrBlank()) gqlInitial() else throw Exception()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                try {
                    when (apiPref.elementAt(1)?.second) {
                        C.HELIX -> if (!helixToken.isNullOrBlank()) helixInitial() else throw Exception()
                        C.GQL -> if (!gqlToken.isNullOrBlank()) gqlInitial() else throw Exception()
                        else -> throw Exception()
                    }
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
            if (remote.isNotEmpty()) {
                for (i in remote) {
                    val item = list.find { it.user_id == i.user_id }
                    if (item == null) {
                        list.add(i)
                    }
                }
                if (api == C.HELIX) {
                    val userIds = remote.mapNotNull { it.user_id }
                    for (ids in userIds.chunked(100)) {
                        val users = helixApi.getUsersById(helixClientId, helixToken, ids).data
                        if (users != null) {
                            for (i in users) {
                                val item = list.find { it.user_id == i.id }
                                if (item != null) {
                                    item.profileImageURL = i.profile_image_url
                                }
                            }
                        }
                    }
                }
            }
            list.sortByDescending { it.viewer_count }
            list
        }
    }

    private suspend fun helixInitial(): List<Stream> {
        api = C.HELIX
        val get = helixApi.getFollowedStreams(helixClientId, helixToken, userId, 100, offset)
        return if (get.data != null) {
            offset = get.pagination?.cursor
            get.data
        } else mutableListOf()
    }

    private suspend fun gqlInitial(): List<Stream> {
        api = C.GQL
        val get = gqlApi.loadFollowedStreams(gqlClientId, gqlToken, 100, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Stream>) {
        loadRange(params, callback) {
            val list = if (!offset.isNullOrBlank()) {
                when (api) {
                    C.HELIX -> helixRange()
                    C.GQL -> gqlRange()
                    else -> mutableListOf()
                }
            } else mutableListOf()
            if (api == C.HELIX && list.isNotEmpty()) {
                val userIds = list.mapNotNull { it.user_id }
                for (ids in userIds.chunked(100)) {
                    val users = helixApi.getUsersById(helixClientId, helixToken, ids).data
                    if (users != null) {
                        for (i in users) {
                            val item = list.find { it.user_id == i.id }
                            if (item != null) {
                                item.profileImageURL = i.profile_image_url
                            }
                        }
                    }
                }
            }
            list
        }
    }

    private suspend fun helixRange(): List<Stream> {
        val get = helixApi.getFollowedStreams(helixClientId, helixToken, userId, 100, offset)
        return if (get.data != null) {
            offset = get.pagination?.cursor
            get.data
        } else mutableListOf()
    }

    private suspend fun gqlRange(): List<Stream> {
        val get = gqlApi.loadFollowedStreams(gqlClientId, gqlToken, 100, offset)
        offset = get.cursor
        return get.data
    }

    private suspend fun helixLocal(ids: List<String>): List<Stream> {
        val streams = mutableListOf<Stream>()
        for (localIds in ids.chunked(100)) {
            val get = helixApi.getStreams(helixClientId, helixToken, localIds).data
            if (get != null) {
                for (i in get) {
                    if (i.viewer_count != null) {
                        streams.add(i)
                    }
                }
            }
        }
        if (streams.isNotEmpty()) {
            val userIds = streams.mapNotNull { it.user_id }
            for (streamIds in userIds.chunked(100)) {
                val users = helixApi.getUsersById(helixClientId, helixToken, streamIds).data
                if (users != null) {
                    for (i in users) {
                        val item = streams.find { it.user_id == i.id }
                        if (item != null) {
                            item.profileImageURL = i.profile_image_url
                        }
                    }
                }
            }
        }
        return streams
    }

    class Factory(
        private val localFollowsChannel: LocalFollowChannelRepository,
        private val userId: String?,
        private val helixClientId: String?,
        private val helixToken: String?,
        private val helixApi: HelixApi,
        private val gqlClientId: String?,
        private val gqlToken: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Stream, FollowedStreamsDataSource>() {

        override fun create(): DataSource<Int, Stream> =
                FollowedStreamsDataSource(localFollowsChannel, userId, helixClientId, helixToken, helixApi, gqlClientId, gqlToken, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}