package com.github.andreyasadchy.xtra.ui.view.chat

import android.graphics.drawable.Animatable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.text.getSpans
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.TwitchBadge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.util.chat.ChatAdapterUtils
import java.util.Random

class MessageClickedChatAdapter(
    private val enableTimestamps: Boolean,
    private val timestampFormat: String?,
    private val firstMsgVisibility: Int,
    private val firstChatMsg: String,
    private val redeemedChatMsg: String,
    private val redeemedNoMsg: String,
    private val rewardChatMsg: String,
    private val replyMessage: String,
    private val replyClick: (ChatMessage) -> Unit,
    private val useRandomColors: Boolean,
    private val useReadableColors: Boolean,
    private val isLightTheme: Boolean,
    private val nameDisplay: String?,
    private val useBoldNames: Boolean,
    private val showSystemMessageEmotes: Boolean,
    private val chatUrl: String?,
    private val getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?,
    private val fragment: Fragment,
    private val imageLibrary: String?,
    private val emoteSize: Int,
    private val badgeSize: Int,
    private val emoteQuality: String,
    private val animateGifs: Boolean,
    private val enableZeroWidth: Boolean,
    messages: MutableList<ChatMessage>?,
    private val userColors: HashMap<String, Int>,
    private val savedColors: HashMap<String, Int>,
    var loggedInUser: String?,
    var localTwitchEmotes: List<TwitchEmote>?,
    var globalStvEmotes: List<Emote>?,
    var channelStvEmotes: List<Emote>?,
    var globalBttvEmotes: List<Emote>?,
    var channelBttvEmotes: List<Emote>?,
    var globalFfzEmotes: List<Emote>?,
    var channelFfzEmotes: List<Emote>?,
    var globalBadges: List<TwitchBadge>?,
    var channelBadges: List<TwitchBadge>?,
    var cheerEmotes: List<CheerEmote>?,
    var selectedMessage: ChatMessage?) : RecyclerView.Adapter<MessageClickedChatAdapter.ViewHolder>() {

    val userId = selectedMessage?.userId
    val userLogin = selectedMessage?.userLogin
    var messages: MutableList<ChatMessage>? = if (!userId.isNullOrBlank() || !userLogin.isNullOrBlank()) {
        messages?.filter {
            (!userId.isNullOrBlank() && it.userId == userId) || (!userLogin.isNullOrBlank() && it.userLogin == userLogin)
        }?.toMutableList()
    } else null ?: selectedMessage?.let { mutableListOf(it) }
        set(value) {
            val oldSize = field?.size ?: 0
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize)
            }
            field = value
        }
    private val random = Random()
    private val savedLocalTwitchEmotes = mutableMapOf<String, ByteArray>()
    private val savedLocalBadges = mutableMapOf<String, ByteArray>()
    private val savedLocalCheerEmotes = mutableMapOf<String, ByteArray>()
    private val savedLocalEmotes = mutableMapOf<String, ByteArray>()

    var messageClickListener: ((ChatMessage, ChatMessage?) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chatMessage = messages?.get(position) ?: return
        val pair = ChatAdapterUtils.prepareChatMessage(
            chatMessage, holder.textView, enableTimestamps, timestampFormat, firstMsgVisibility, firstChatMsg, redeemedChatMsg, redeemedNoMsg,
            rewardChatMsg, true, replyMessage, { replyClick(chatMessage) }, useRandomColors, random, useReadableColors, isLightTheme, nameDisplay,
            useBoldNames, showSystemMessageEmotes, loggedInUser, chatUrl, getEmoteBytes, userColors, savedColors, localTwitchEmotes, globalStvEmotes,
            channelStvEmotes, globalBttvEmotes, channelBttvEmotes, globalFfzEmotes, channelFfzEmotes, globalBadges, channelBadges, cheerEmotes,
            savedLocalTwitchEmotes, savedLocalBadges, savedLocalCheerEmotes, savedLocalEmotes
        )
        if (chatMessage == selectedMessage) {
            holder.textView.setBackgroundResource(R.color.chatMessageSelected)
        }
        holder.bind(chatMessage, pair.first)
        ChatAdapterUtils.loadImages(
            fragment, holder.textView, { holder.bind(chatMessage, it) }, pair.second, imageLibrary, pair.first, emoteSize, badgeSize, emoteQuality,
            animateGifs, enableZeroWidth
        )
    }

    fun updateBackground(chatMessage: ChatMessage, item: TextView) {
        if (chatMessage.message.isNullOrBlank()) {
            item.setBackgroundResource(0)
        } else {
            when {
                chatMessage.isFirst && firstMsgVisibility < 2 -> item.setBackgroundResource(R.color.chatMessageFirst)
                chatMessage.reward?.id != null && firstMsgVisibility < 2 -> item.setBackgroundResource(R.color.chatMessageReward)
                chatMessage.systemMsg != null || chatMessage.msgId != null -> item.setBackgroundResource(R.color.chatMessageNotice)
                loggedInUser?.let { user ->
                    if (chatMessage.userId != null && chatMessage.userLogin != user) {
                        item.text.split(" ").find {
                            !Patterns.WEB_URL.matcher(it).matches() && it.contains(user, true)
                        } != null
                    } else false
                } == true -> item.setBackgroundResource(R.color.chatMessageMention)
                else -> item.setBackgroundResource(0)
            }
        }
    }

    override fun getItemCount(): Int = messages?.size ?: 0

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (animateGifs) {
            (holder.textView.text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
                (it.drawable as? Animatable)?.start()
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (animateGifs) {
            (holder.textView.text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
                (it.drawable as? Animatable)?.stop()
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        val childCount = recyclerView.childCount
        if (animateGifs) {
            for (i in 0 until childCount) {
                ((recyclerView.getChildAt(i) as TextView).text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
                    (it.drawable as? Animatable)?.stop()
                }
            }
        }
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView = itemView as TextView

        fun bind(chatMessage: ChatMessage, formattedMessage: SpannableStringBuilder) {
            textView.apply {
                text = formattedMessage
                movementMethod = LinkMovementMethod.getInstance()
                TooltipCompat.setTooltipText(this, chatMessage.message ?: chatMessage.systemMsg)
                setOnClickListener {
                    if (selectionStart == -1 && selectionEnd == -1 && chatMessage != selectedMessage) {
                        messageClickListener?.invoke(chatMessage, selectedMessage)
                        selectedMessage = chatMessage
                        setBackgroundResource(R.color.chatMessageSelected)
                    }
                }
            }
        }
    }
}
