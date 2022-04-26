package com.github.andreyasadchy.xtra.ui.player

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.di.Injectable
import com.github.andreyasadchy.xtra.model.NotLoggedIn
import com.github.andreyasadchy.xtra.model.User
import com.github.andreyasadchy.xtra.ui.common.AlertDialogFragment
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.follow.FollowFragment
import com.github.andreyasadchy.xtra.ui.common.follow.FollowViewModel
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.clip.ClipPlayerFragment
import com.github.andreyasadchy.xtra.ui.player.offline.OfflinePlayerFragment
import com.github.andreyasadchy.xtra.ui.player.stream.StreamPlayerFragment
import com.github.andreyasadchy.xtra.ui.view.CustomPlayerView
import com.github.andreyasadchy.xtra.ui.view.SlidingLayout
import com.github.andreyasadchy.xtra.util.*
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import kotlinx.android.synthetic.main.view_chat.view.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Suppress("PLUGIN_WARNING")
abstract class BasePlayerFragment : BaseNetworkFragment(), Injectable, LifecycleListener, SlidingLayout.Listener, FollowFragment, SleepTimerDialog.OnSleepTimerStartedListener, AlertDialogFragment.OnDialogResultListener {

    lateinit var slidingLayout: SlidingLayout
    private lateinit var playerView: CustomPlayerView
    private lateinit var aspectRatioFrameLayout: AspectRatioFrameLayout
    private lateinit var chatLayout: ViewGroup
    private lateinit var fullscreenToggle: ImageButton
    private lateinit var playerAspectRatioToggle: ImageButton
    private lateinit var showChat: ImageButton
    private lateinit var hideChat: ImageButton
    private lateinit var toggleChatBar: ImageButton
    private lateinit var pauseButton: ImageButton
    private var disableChat: Boolean = false

    protected abstract val layoutId: Int
    protected abstract val chatContainerId: Int

    protected abstract val viewModel: PlayerViewModel

    protected var isPortrait = false
        private set
    private var isKeyboardShown = false

    protected abstract val shouldEnterPictureInPicture: Boolean
    open val controllerAutoShow: Boolean = true
    open val controllerShowTimeoutMs: Int = 3000
    private var resizeMode = 0

    protected lateinit var prefs: SharedPreferences
    protected abstract val channelId: String?
    protected abstract val channelLogin: String?
    protected abstract val channelName: String?
    protected abstract val channelImage: String?

    val playerWidth: Int
        get() = playerView.width
    val playerHeight: Int
        get() = playerView.height

    private var chatWidthLandscape = 0

    private var systemUiFlags = (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activity = requireActivity()
        prefs = activity.prefs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            systemUiFlags = systemUiFlags or (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
        isPortrait = activity.isInPortraitOrientation
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(layoutId, container, false).also {
            (it as LinearLayout).orientation = if (isPortrait) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.keepScreenOn = true
        val activity = requireActivity() as MainActivity
        slidingLayout = view as SlidingLayout
        slidingLayout.addListener(activity)
        slidingLayout.addListener(this)
        slidingLayout.maximizedSecondViewVisibility = if (prefs.getBoolean(C.KEY_CHAT_OPENED, true)) View.VISIBLE else View.GONE //TODO
        playerView = view.findViewById(R.id.playerView)
        chatLayout = view.findViewById(chatContainerId)
        aspectRatioFrameLayout = view.findViewById(R.id.aspectRatioFrameLayout)
        aspectRatioFrameLayout.setAspectRatio(16f / 9f)
        pauseButton = view.findViewById(R.id.exo_pause)
        disableChat = prefs.getBoolean(C.CHAT_DISABLE, false)
        val isNotOfflinePlayer = this !is OfflinePlayerFragment
        if (this is StreamPlayerFragment && !prefs.getBoolean(C.PLAYER_PAUSE, false)) {
            pauseButton.layoutParams.height = 0
        }
        if (prefs.getBoolean(C.PLAYER_DOUBLETAP, true) && !disableChat) {
            playerView.setOnDoubleTapListener {
                if (!isPortrait && slidingLayout.isMaximized && isNotOfflinePlayer) {
                    if (chatLayout.isVisible) {
                        hideChat()
                    } else {
                        showChat()
                    }
                    playerView.hideController()
                }
            }
        }
        chatWidthLandscape = prefs.getInt(C.LANDSCAPE_CHAT_WIDTH, 0)
        hideChat = view.findViewById<ImageButton>(R.id.hideChat).apply {
            setOnClickListener { hideChat() }
        }
        showChat = view.findViewById<ImageButton>(R.id.showChat).apply {
            setOnClickListener { showChat() }
        }
        fullscreenToggle = view.findViewById<ImageButton>(R.id.fullscreenToggle).apply {
            setOnClickListener {
                activity.apply {
                    if (isPortrait) {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
        playerAspectRatioToggle = view.findViewById<ImageButton>(R.id.aspectRatio).apply {
            setOnClickListener {
                resizeMode = (resizeMode + 1).let { if (it < 5) it else 0 }
                playerView.resizeMode = resizeMode
                prefs.edit { putInt(C.ASPECT_RATIO_LANDSCAPE, resizeMode) }
            }
        }
        initLayout()
        playerView.controllerAutoShow = controllerAutoShow
        if (isNotOfflinePlayer) {
            view.findViewById<ImageButton>(R.id.settings).disable()
        }
        if (prefs.getBoolean(C.PLAYER_MINIMIZE, true)) {
            view.findViewById<ImageButton>(R.id.minimize).setOnClickListener { minimize() }
        } else {
            view.findViewById<ImageButton>(R.id.minimize).gone()
        }
        if (prefs.getBoolean(C.PLAYER_CHANNEL, true)) {
            view.findViewById<TextView>(R.id.channel).apply {
                text = channelName
                setOnClickListener {
                    activity.viewChannel(channelId, channelLogin, channelName, channelImage, !isNotOfflinePlayer)
                    slidingLayout.minimize()
                }
            }
        } else {
            view.findViewById<TextView>(R.id.channel).gone()
        }
        if (prefs.getBoolean(C.PLAYER_VOLUMEBUTTON, false)) {
            view.findViewById<ImageButton>(R.id.volumeButton).setOnClickListener {
                FragmentUtils.showPlayerVolumeDialog(childFragmentManager)
            }
        } else {
            view.findViewById<ImageButton>(R.id.volumeButton).gone()
        }
        if (this is StreamPlayerFragment) {
            if (User.get(activity) !is NotLoggedIn) {
                if (prefs.getBoolean(C.PLAYER_CHATBARTOGGLE, true) && !disableChat) {
                    toggleChatBar = view.findViewById(R.id.toggleChatBar)
                    toggleChatBar.visible()
                    toggleChatBar.apply { setOnClickListener { toggleChatBar() } }
                }
                slidingLayout.viewTreeObserver.addOnGlobalLayoutListener {
                    if (slidingLayout.isKeyboardShown) {
                        if (!isKeyboardShown) {
                            isKeyboardShown = true
                            if (!isPortrait) {
                                chatLayout.updateLayoutParams { width = (slidingLayout.width / 1.8f).toInt() }
                                showStatusBar()
                            }
                        }
                    } else {
                        if (isKeyboardShown) {
                            isKeyboardShown = false
                            chatLayout.clearFocus()
                            if (!isPortrait) {
                                chatLayout.updateLayoutParams { width = chatWidthLandscape }
                                if (slidingLayout.isMaximized) {
                                    hideStatusBar()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val rewind = prefs.getString("playerRewind", "10000")!!.toInt()
            val forward = prefs.getString("playerForward", "10000")!!.toInt()
            val rewindImage = when {
                rewind <= 5000 -> R.drawable.baseline_replay_5_black_48
                rewind <= 10000 -> R.drawable.baseline_replay_10_black_48
                else -> R.drawable.baseline_replay_30_black_48
            }
            val forwardImage = when {
                forward <= 5000 -> R.drawable.baseline_forward_5_black_48
                forward <= 10000 -> R.drawable.baseline_forward_10_black_48
                else -> R.drawable.baseline_forward_30_black_48
            }
            playerView.apply {
                setRewindIncrementMs(rewind)
                setFastForwardIncrementMs(forward)
            }
            view.findViewById<ImageButton>(com.google.android.exoplayer2.ui.R.id.exo_rew).setImageResource(rewindImage)
            view.findViewById<ImageButton>(com.google.android.exoplayer2.ui.R.id.exo_ffwd).setImageResource(forwardImage)
            if (isNotOfflinePlayer) {
                view.findViewById<ImageButton>(R.id.download).disable()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !requireActivity().isInPictureInPictureMode) {
            chatLayout.hideKeyboard()
            chatLayout.clearFocus()
            initLayout()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        if (isInPictureInPictureMode) {
            playerView.useController = false
            chatLayout.gone()
        } else {
            playerView.useController = true
        }
    }

    override fun initialize() {
        val activity = requireActivity() as MainActivity
        val view = requireView()
        viewModel.currentPlayer.observe(viewLifecycleOwner) {
            playerView.player = it
        }
        viewModel.playerMode.observe(viewLifecycleOwner) {
            if (it == PlayerMode.NORMAL) {
                playerView.controllerHideOnTouch = true
                playerView.controllerShowTimeoutMs = controllerShowTimeoutMs
            } else {
                playerView.controllerHideOnTouch = false
                playerView.controllerShowTimeoutMs = -1
                playerView.showController()
            }
        }
        if (this !is OfflinePlayerFragment && prefs.getBoolean(C.PLAYER_FOLLOW, true)) {
            initializeFollow(this, (viewModel as FollowViewModel), view.findViewById(R.id.follow), User.get(activity), prefs.getString(C.HELIX_CLIENT_ID, ""))
        }
        if (this !is ClipPlayerFragment && prefs.getBoolean(C.PLAYER_SLEEP, true)) {
            viewModel.sleepTimer.observe(viewLifecycleOwner) {
                onMinimize()
                activity.closePlayer()
                if (prefs.getBoolean(C.SLEEP_TIMER_LOCK, true)) {
                    lockScreen()
                }
            }
            view.findViewById<ImageButton>(R.id.sleepTimer).setOnClickListener {
                SleepTimerDialog.show(childFragmentManager, viewModel.timerTimeLeft)
            }
        } else {
            view.findViewById<ImageButton>(R.id.sleepTimer).gone()
        }
    }

    override fun onMinimize() {
        playerView.useController = false
        if (!isPortrait) {
            showStatusBar()
            val activity = requireActivity()
            activity.lifecycleScope.launch {
                delay(500L)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    override fun onMaximize() {
        playerView.useController = true
        if (!playerView.controllerHideOnTouch) { //TODO
            playerView.showController()
        }
        if (!isPortrait) {
            hideStatusBar()
        }
    }

    override fun onClose() {

    }

    override fun onSleepTimerChanged(durationMs: Long, hours: Int, minutes: Int, lockScreen: Boolean) {
        val context = requireContext()
        if (durationMs > 0L) {
            context.toast(when {
                hours == 0 -> getString(R.string.playback_will_stop, resources.getQuantityString(R.plurals.minutes, minutes, minutes))
                minutes == 0 -> getString(R.string.playback_will_stop, resources.getQuantityString(R.plurals.hours, hours, hours))
                else -> getString(R.string.playback_will_stop_hours_minutes, resources.getQuantityString(R.plurals.hours, hours, hours), resources.getQuantityString(R.plurals.minutes, minutes, minutes))
            })
        } else if (viewModel.timerTimeLeft > 0L) {
            context.toast(R.string.timer_canceled)
        }
        if (lockScreen != prefs.getBoolean(C.SLEEP_TIMER_LOCK, true)) {
            prefs.edit { putBoolean(C.SLEEP_TIMER_LOCK, lockScreen) }
        }
        viewModel.setTimer(durationMs)
    }

    override fun onDialogResult(requestCode: Int, resultCode: Int) {
        when (requestCode) {
            REQUEST_FOLLOW -> {
                //TODO
            }
        }
    }

    //    abstract fun play(obj: Parcelable) //TODO instead maybe add livedata in mainactivity and observe it

    fun minimize() {
        slidingLayout.minimize()
    }

    fun maximize() {
        slidingLayout.maximize()
    }

    fun enterPictureInPicture(): Boolean {
        return slidingLayout.isMaximized && shouldEnterPictureInPicture
    }

    private fun initLayout() {
        if (isPortrait) {
            requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener(null)
            aspectRatioFrameLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                height = LinearLayout.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            chatLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                width = LinearLayout.LayoutParams.MATCH_PARENT
                height = 0
                weight = 1f
            }
            chatLayout.visible()
            if (prefs.getBoolean(C.PLAYER_FULLSCREEN, true)) {
                fullscreenToggle.setImageResource(R.drawable.baseline_fullscreen_black_24)
            } else {
                fullscreenToggle.gone()
            }
            playerAspectRatioToggle.gone()
            hideChat.gone()
            showChat.gone()
            showStatusBar()
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            aspectRatioFrameLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
        } else {
            requireActivity().window.decorView.setOnSystemUiVisibilityChangeListener {
                if (!isKeyboardShown && slidingLayout.isMaximized) {
                    hideStatusBar()
                }
            }
            aspectRatioFrameLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                width = 0
                height = LinearLayout.LayoutParams.MATCH_PARENT
                weight = 1f
            }
            if (this !is OfflinePlayerFragment) {
                chatLayout.updateLayoutParams<LinearLayout.LayoutParams> {
                    width = chatWidthLandscape
                    height = LinearLayout.LayoutParams.MATCH_PARENT
                    weight = 0f
                }
                if (disableChat) {
                    hideChat.gone()
                    showChat.gone()
                    chatLayout.gone()
                    slidingLayout.maximizedSecondViewVisibility = View.GONE
                } else {
                    setPreferredChatVisibility()
                }
                val recycleview = requireView().findViewById<RecyclerView>(R.id.recyclerView)
                val btndwn = requireView().findViewById<Button>(R.id.btnDown)
                if (chatLayout.isVisible && btndwn != null && !btndwn.isVisible && recycleview.adapter?.itemCount != null)
                    recycleview.scrollToPosition(recycleview.adapter?.itemCount!! - 1) // scroll down
            } else {
                chatLayout.gone()
            }
            if (prefs.getBoolean(C.PLAYER_FULLSCREEN, true)) {
                fullscreenToggle.setImageResource(R.drawable.baseline_fullscreen_exit_black_24)
            } else {
                fullscreenToggle.gone()
            }
            if (prefs.getBoolean(C.PLAYER_ASPECT, true)) {
                playerAspectRatioToggle.visible()
            } else {
                playerAspectRatioToggle.gone()
            }
            slidingLayout.post {
                if (slidingLayout.isMaximized) {
                    hideStatusBar()
                } else {
                    showStatusBar()
                }
            }
            aspectRatioFrameLayout.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            resizeMode = prefs.getInt(C.ASPECT_RATIO_LANDSCAPE, AspectRatioFrameLayout.RESIZE_MODE_FIT)
        }
        playerView.resizeMode = resizeMode
    }

    private fun setPreferredChatVisibility() {
        if (prefs.getBoolean(C.KEY_CHAT_OPENED, true)) showChat() else hideChat()
    }

    private fun toggleChatBar() {
        val messageView = view?.findViewById<LinearLayout>(R.id.messageView)
        if (messageView?.isVisible == true) {
            chatLayout.hideKeyboard()
            chatLayout.clearFocus()
            chatLayout.viewPager.gone() // emote menu
            messageView.gone()
            prefs.edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, false) }
        } else {
            messageView?.visible()
            prefs.edit { putBoolean(C.KEY_CHAT_BAR_VISIBLE, true) }
        }
    }

    private fun hideChat() {
        if (prefs.getBoolean(C.PLAYER_CHATTOGGLE, true)) {
            hideChat.gone()
            showChat.visible()
        } else {
            hideChat.gone()
            showChat.gone()
        }
        chatLayout.gone()
        prefs.edit { putBoolean(C.KEY_CHAT_OPENED, false) }
        slidingLayout.maximizedSecondViewVisibility = View.GONE
    }

    private fun showChat() {
        if (prefs.getBoolean(C.PLAYER_CHATTOGGLE, true)) {
            hideChat.visible()
            showChat.gone()
        } else {
            hideChat.gone()
            showChat.gone()
        }
        chatLayout.visible()
        prefs.edit { putBoolean(C.KEY_CHAT_OPENED, true) }
        slidingLayout.maximizedSecondViewVisibility = View.VISIBLE
        val recycleview = requireView().findViewById<RecyclerView>(R.id.recyclerView)
        val btndwn = requireView().findViewById<Button>(R.id.btnDown)
        if (chatLayout.isVisible && btndwn != null && !btndwn.isVisible && recycleview.adapter?.itemCount != null)
            recycleview.scrollToPosition(recycleview.adapter?.itemCount!! - 1) // scroll down
    }

    private fun showStatusBar() {
        if (isAdded) { //TODO this check might not be needed anymore AND ANDROID 5
            requireActivity().window.decorView.systemUiVisibility = 0
        }
    }

    private fun hideStatusBar() {
        if (isAdded) {
            requireActivity().window.decorView.systemUiVisibility = systemUiFlags
        }
    }

    private fun lockScreen() {
        if ((requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager).isScreenOn) {
            try {
                (requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
            } catch (e: SecurityException) {

            }
        }
    }

    private companion object {
        const val REQUEST_FOLLOW = 0
    }
}
