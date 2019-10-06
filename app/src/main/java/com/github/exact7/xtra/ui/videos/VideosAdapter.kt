package com.github.exact7.xtra.ui.videos

import android.text.format.DateUtils
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import com.github.exact7.xtra.R
import com.github.exact7.xtra.model.kraken.video.Video
import com.github.exact7.xtra.ui.common.BasePagedListAdapter
import com.github.exact7.xtra.ui.common.OnChannelSelectedListener
import com.github.exact7.xtra.util.TwitchApiHelper
import com.github.exact7.xtra.util.loadImage
import kotlinx.android.synthetic.main.fragment_videos_list_item.view.*

class VideosAdapter(
        private val clickListener: BaseVideosFragment.OnVideoSelectedListener,
        private val channelClickListener: OnChannelSelectedListener,
        private val showDownloadDialog: (Video) -> Unit) : BasePagedListAdapter<Video>(
        object : DiffUtil.ItemCallback<Video>() {
            override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean =
                    oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean =
                    false &&
                    oldItem.views == newItem.views &&
                            oldItem.preview == newItem.preview &&
                            oldItem.title == newItem.title
        }) {

    private var positions: Map<Long, Long>? = null

    override val layoutId: Int = R.layout.fragment_videos_list_item

    override fun bind(item: Video, view: View) {
        val channelListener: (View) -> Unit = { channelClickListener.viewChannel(item.channel) }
        with(view) {
            setOnClickListener { clickListener.startVideo(item) }
            setOnLongClickListener { showDownloadDialog(item); true }
            thumbnail.loadImage(item.preview.large)
            date.text = TwitchApiHelper.formatTime(context, item.createdAt)
            views.text = TwitchApiHelper.formatCount(context, item.views)
            duration.text = DateUtils.formatElapsedTime(item.length.toLong())
            progressBar.progress = positions?.get(item.id.substring(1).toLong()).let { if (it != null) (it / (item.length * 10L)).toInt() else 0 }
            userImage.apply {
                setOnClickListener(channelListener)
                loadImage(item.channel.logo, circle = true)
            }
            username.apply {
                setOnClickListener(channelListener)
                text = item.channel.displayName
            }
            title.text = item.title
            gameName.text = item.game
            options.setOnClickListener {
                PopupMenu(context, it).apply {
                    inflate(R.menu.media_item)
                    setOnMenuItemClickListener { showDownloadDialog(item); true }
                    show()
                }
            }
        }
    }

    fun setPositions(positions: Map<Long, Long>) {
        this.positions = positions
        if (!currentList.isNullOrEmpty()) {
            notifyDataSetChanged()
        }
    }
}