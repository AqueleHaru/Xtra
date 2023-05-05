package com.github.andreyasadchy.xtra.ui.search.videos

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.repository.*
import com.github.andreyasadchy.xtra.repository.datasource.SearchVideosDataSource
import com.github.andreyasadchy.xtra.ui.videos.BaseVideosViewModel
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class VideoSearchViewModel @Inject constructor(
    @ApplicationContext context: Context,
    playerRepository: PlayerRepository,
    bookmarksRepository: BookmarksRepository,
    repository: ApiRepository,
    private val graphQLRepository: GraphQLRepository) : BaseVideosViewModel(playerRepository, bookmarksRepository, repository) {

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = query.flatMapLatest { query ->
        Pager(
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        ) {
            SearchVideosDataSource(
                query = query,
                gqlClientId = context.prefs().getString(C.GQL_CLIENT_ID2, "kd1unb4b3q4t58fwlpcbzcbnm76a8fp"),
                gqlApi = graphQLRepository,
                apiPref = TwitchApiHelper.listFromPrefs(context.prefs().getString(C.API_PREF_SEARCH_VIDEOS, ""), TwitchApiHelper.searchVideosApiDefaults))
        }.flow
    }.cachedIn(viewModelScope)

    fun setQuery(query: String) {
        if (this.query.value != query) {
            this.query.value = query
        }
    }
}