package com.github.andreyasadchy.xtra.repository.datasource

import androidx.core.util.Pair
import androidx.paging.DataSource
import com.github.andreyasadchy.xtra.model.helix.video.Video
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.util.C
import kotlinx.coroutines.CoroutineScope

class SearchVideosDataSource private constructor(
    private val query: String,
    private val gqlClientId: String?,
    private val gqlApi: GraphQLRepository,
    private val apiPref: ArrayList<Pair<Long?, String?>?>?,
    coroutineScope: CoroutineScope) : BasePositionalDataSource<Video>(coroutineScope) {
    private var api: String? = null
    private var offset: String? = null

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Video>) {
        loadInitial(params, callback) {
            try {
                when (apiPref?.elementAt(0)?.second) {
                    C.GQL -> gqlInitial()
                    else -> throw Exception()
                }
            } catch (e: Exception) {
                mutableListOf()
            }
        }
    }

    private suspend fun gqlInitial(): List<Video> {
        val get = gqlApi.loadSearchVideos(gqlClientId, query, offset)
        offset = get.cursor
        return get.data
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Video>) {
        loadRange(params, callback) {
            when (api) {
                C.GQL -> gqlRange()
                else -> mutableListOf()
            }
        }
    }

    private suspend fun gqlRange(): List<Video> {
        val get = gqlApi.loadSearchVideos(gqlClientId, query, offset)
        return if (offset != null && offset != "") {
            offset = get.cursor
            get.data
        } else mutableListOf()
    }

    class Factory(
        private val query: String,
        private val gqlClientId: String?,
        private val gqlApi: GraphQLRepository,
        private val apiPref: ArrayList<Pair<Long?, String?>?>?,
        private val coroutineScope: CoroutineScope) : BaseDataSourceFactory<Int, Video, SearchVideosDataSource>() {

        override fun create(): DataSource<Int, Video> =
                SearchVideosDataSource(query, gqlClientId, gqlApi, apiPref, coroutineScope).also(sourceLiveData::postValue)
    }
}
