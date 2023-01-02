package com.github.andreyasadchy.xtra.ui.player

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.ui.common.BaseAndroidViewModel
import com.github.andreyasadchy.xtra.ui.common.OnQualityChangeListener
import com.github.andreyasadchy.xtra.ui.player.stream.StreamPlayerViewModel
import com.github.andreyasadchy.xtra.util.*
import com.github.andreyasadchy.xtra.util.C
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.EVENT_PLAYBACK_STATE_CHANGED
import com.google.android.exoplayer2.Player.EVENT_PLAY_WHEN_READY_CHANGED
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.schedule


abstract class PlayerViewModel(context: Application) : BaseAndroidViewModel(context), Player.Listener, OnQualityChangeListener {

    protected val tag: String = javaClass.simpleName

    protected val httpDataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(Util.getUserAgent(context, context.getString(R.string.app_name)))
    protected val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    protected val trackSelector = DefaultTrackSelector(context)
    private val useFirstDecoder = context.prefs().getBoolean(C.PLAYER_FORCE_FIRST_DECODER, false)
    private val rewind = context.prefs().getString(C.PLAYER_REWIND, "10000")!!.toLong()
    private val forward = context.prefs().getString(C.PLAYER_FORWARD, "10000")!!.toLong()
    private val minBuffer = context.prefs().getString(C.PLAYER_BUFFER_MIN, "15000")?.toIntOrNull() ?: 15000
    private val maxBuffer = context.prefs().getString(C.PLAYER_BUFFER_MAX, "50000")?.toIntOrNull() ?: 50000
    private val playbackBuffer = context.prefs().getString(C.PLAYER_BUFFER_PLAYBACK, "2000")?.toIntOrNull() ?: 2000
    private val rebuffer = context.prefs().getString(C.PLAYER_BUFFER_REBUFFER, "5000")?.toIntOrNull() ?: 5000
    val player: ExoPlayer = ExoPlayer.Builder(context).apply {
        if (useFirstDecoder) {
            setRenderersFactory(DefaultRenderersFactory(context).setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder).firstOrNull()?.let {
                    listOf(it)
                } ?: emptyList()
            })
        }
        setTrackSelector(trackSelector)
        setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(minBuffer, maxBuffer, playbackBuffer, rebuffer).build())
        setSeekBackIncrementMs(rewind)
        setSeekForwardIncrementMs(forward)
    }.build().apply { addListener(this@PlayerViewModel) }
    protected lateinit var mediaSource: MediaSource //TODO maybe redo these viewmodels to custom players

    protected val _currentPlayer = MutableLiveData<ExoPlayer>().apply { value = player }
    val currentPlayer: LiveData<ExoPlayer>
        get() = _currentPlayer
    protected val _playerMode = MutableLiveData<PlayerMode>().apply { value = PlayerMode.NORMAL }
    val playerMode: LiveData<PlayerMode>
        get() = _playerMode
    var qualityIndex = 0
        protected set
    protected var previousQuality = 0
    protected var playbackPosition: Long = 0

    protected var binder: AudioPlayerService.AudioBinder? = null

    protected var isResumed = true
    var userLeaveHint = false

    private val _showPauseButton = MutableLiveData<Boolean>()
    val showPauseButton: LiveData<Boolean>
        get() = _showPauseButton

    private val _isPlaying = MutableLiveData<Boolean>()
    val isPlaying: LiveData<Boolean>
        get() = _isPlaying

    private val _subtitlesAvailable = MutableLiveData<Boolean>()
    val subtitlesAvailable: LiveData<Boolean>
        get() = _subtitlesAvailable

    private var timer: Timer? = null
    private val _sleepTimer = MutableLiveData<Boolean>()
    val sleepTimer: LiveData<Boolean>
        get() = _sleepTimer
    private var timerEndTime = 0L
    val timerTimeLeft
        get() = timerEndTime - System.currentTimeMillis()

    init {
        val volume = context.prefs().getInt(C.PLAYER_VOLUME, 100) / 100f
        setVolume(volume)
    }

    fun setTimer(duration: Long) {
        timer?.let {
            it.cancel()
            timerEndTime = 0L
            timer = null
        }
        if (duration > 0L) {
            timer = Timer().apply {
                timerEndTime = System.currentTimeMillis() + duration
                schedule(duration) {
                    stopBackgroundAudio()
                    _sleepTimer.postValue(true)
                }
            }
        }
    }

    fun isPaused(): Boolean {
        return !player.isPlaying && player.playbackState == Player.STATE_READY
    }

    open fun onResume() {
        play()
    }

    open fun onPause() {
        player.stop()
    }

    open fun restartPlayer() {
        playbackPosition = currentPlayer.value!!.currentPosition
        player.stop()
        play()
        player.seekTo(playbackPosition)
    }

    protected fun play() {
        if (this::mediaSource.isInitialized) { //TODO
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        }
    }

    protected fun startBackgroundAudio(playlistUrl: String, channelName: String?, title: String?, imageUrl: String?, usePlayPause: Boolean, type: Int, videoId: Number?, showNotification: Boolean) {
        val context = XtraApp.INSTANCE //TODO
        val intent = Intent(context, AudioPlayerService::class.java).apply {
            putExtra(AudioPlayerService.KEY_PLAYLIST_URL, playlistUrl)
            putExtra(AudioPlayerService.KEY_CHANNEL_NAME, channelName)
            putExtra(AudioPlayerService.KEY_TITLE, title)
            putExtra(AudioPlayerService.KEY_IMAGE_URL, imageUrl)
            putExtra(AudioPlayerService.KEY_USE_PLAY_PAUSE, usePlayPause)
            putExtra(AudioPlayerService.KEY_CURRENT_POSITION, player.currentPosition)
            putExtra(AudioPlayerService.KEY_TYPE, type)
            putExtra(AudioPlayerService.KEY_VIDEO_ID, videoId)
        }
        player.stop()
        val connection = object : ServiceConnection {

            override fun onServiceDisconnected(name: ComponentName) {
            }

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                binder = service as AudioPlayerService.AudioBinder
                _currentPlayer.value = service.player
                if (showNotification) {
                    showAudioNotification()
                }
            }
        }
        AudioPlayerService.connection = connection
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    protected fun stopBackgroundAudio() {
        AudioPlayerService.connection?.let {
//            val context = getApplication<Application>()
            XtraApp.INSTANCE.unbindService(it) //TODO
        }
    }

    protected fun showAudioNotification() {
        binder?.showNotification()
    }

    protected fun hideAudioNotification() {
        if (AudioPlayerService.connection != null) {
            binder?.hideNotification()
        } else {
            qualityIndex = previousQuality
            _currentPlayer.value = player
            play()
            player.seekTo(AudioPlayerService.position)
        }
    }

    //Player.EventListener

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED)) {
            _showPauseButton.postValue(player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE && player.playWhenReady)
        }
        super.onEvents(player, events)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.postValue(isPlaying)
    }

    override fun onTracksChanged(tracks: Tracks) {
        _subtitlesAvailable.postValue(tracks.groups.find { it.type == com.google.android.exoplayer2.C.TRACK_TYPE_TEXT } != null)
    }

    override fun onPlayerError(error: PlaybackException) {
        val playerError = player.playerError
        Log.e(tag, "Player error", playerError)
        playbackPosition = player.currentPosition
        val context = getApplication<Application>()
        if (context.isNetworkAvailable) {
            try {
                val isStreamEnded = try {
                    playerError?.type == ExoPlaybackException.TYPE_SOURCE &&
                            this@PlayerViewModel is StreamPlayerViewModel &&
                            playerError.sourceException.let { it is HttpDataSource.InvalidResponseCodeException && it.responseCode == 404 }
                } catch (e: IllegalStateException) {
//                    Crashlytics.log(Log.ERROR, tag, "onPlayerError: Stream end check error. Type: ${error.type}")
//                    Crashlytics.logException(e)
                    return
                }
                if (isStreamEnded) {
                    context.toast(R.string.stream_ended)
                } else {
                    context.shortToast(R.string.player_error)
                    viewModelScope.launch {
                        delay(1500L)
                        try {
                            restartPlayer()
                        } catch (e: Exception) {
//                            Crashlytics.log(Log.ERROR, tag, "onPlayerError: Retry error. ${e.message}")
//                            Crashlytics.logException(e)
                        }
                    }
                }
            } catch (e: Exception) {
//                Crashlytics.log(Log.ERROR, tag, "onPlayerError ${e.message}")
//                Crashlytics.logException(e)
            }
        }
    }

    override fun onCleared() {
        player.release()
        timer?.cancel()
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    fun setVolume(volume: Float) {
        player.volume = volume
    }

    fun subtitlesEnabled(): Boolean {
        return player.currentTracks.groups.find { it.type == com.google.android.exoplayer2.C.TRACK_TYPE_TEXT }?.isSelected == true
    }

    fun toggleSubtitles(enabled: Boolean) {
        if (enabled) {
            player.currentTracks.groups.find { it.type == com.google.android.exoplayer2.C.TRACK_TYPE_TEXT }?.let {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup,0))
                    .build()
            }
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(com.google.android.exoplayer2.C.TRACK_TYPE_TEXT)
                .build()
        }
    }
}