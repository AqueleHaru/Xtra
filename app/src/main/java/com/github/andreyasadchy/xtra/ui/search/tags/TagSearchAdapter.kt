package com.github.andreyasadchy.xtra.ui.search.tags

import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.common.BasePagedListAdapter
import com.github.andreyasadchy.xtra.ui.games.GamesFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.visible
import kotlinx.android.synthetic.main.fragment_search_channels_list_item.view.*

class TagSearchAdapter(
        private val fragment: Fragment,
        private val gamesListener: GamesFragment.OnTagGames,
        private val streamsListener: GamesFragment.OnGameSelectedListener) : BasePagedListAdapter<Tag>(
        object : DiffUtil.ItemCallback<Tag>() {
            override fun areItemsTheSame(oldItem: Tag, newItem: Tag): Boolean =
                    oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Tag, newItem: Tag): Boolean = true
        }) {

    override val layoutId: Int = R.layout.fragment_search_channels_list_item

    override fun bind(item: Tag, view: View) {
        with(view) {
            if (item.name != null) {
                userName.visible()
                userName.text = item.name
            } else {
                userName.gone()
            }
            if (item.scope == "CATEGORY") {
                if (item.id != null) {
                    setOnClickListener { gamesListener.openTagGames(listOf(item.id)) }
                }
            } else {
                if (item.name != null) {
                    setOnClickListener { streamsListener.openGame(tags = listOf(item.name), id = fragment.parentFragment?.arguments?.getString(C.GAME_ID), name = fragment.parentFragment?.arguments?.getString(C.GAME_NAME)) }
                }
            }
        }
    }
}