package com.noiseshield.app.service

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.noiseshield.app.R
import com.noiseshield.app.audio.MaskingEngine
import com.noiseshield.app.audio.AssetSoundDecoder
import com.noiseshield.app.data.MaskingSoundId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class NativeMaskingPlayer(
    private val context: Context,
    private val scope: CoroutineScope,
) : SimpleBasePlayer(Looper.getMainLooper()), AudioManager.OnAudioFocusChangeListener {
    private val engine = MaskingEngine(scope)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val releaseEngine = Runnable {
        if (!playWhenReady) {
            engine.release()
            initialized = false
            playbackState = Player.STATE_IDLE
            invalidateState()
        }
    }

    private var initialized = false
    private var playWhenReady = false
    private var playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var playbackState = Player.STATE_IDLE
    private var playerError: PlaybackException? = null
    private var focusSuppressed = false
    private var resumeAfterTransientLoss = false
    private var selectedSound = MaskingSoundId.WHITE_NOISE
    private var volume = 0.3f
    /** Ambient-linked intensity under the user volume ceiling (1 = full slider). */
    private var ambientScale = 1f
    /** Audio-focus duck multiplier (1 = unducked). */
    private var focusDuck = 1f
    private var focusRequest: AudioFocusRequest? = null
    private var recoveryObserver: Job? = null
    private var decodeJob: Job? = null

    val estimate get() = engine.estimate
    val currentSound get() = selectedSound
    /** Fraction of user volume currently applied after ambient + focus. */
    val maskIntensity: Float
        get() = (ambientScale * focusDuck).coerceIn(0f, 1f)

    init {
        recoveryObserver = scope.launch {
            engine.recoveryState.collect { recovery ->
                handler.post {
                    playbackState = when {
                        recovery != 0 -> Player.STATE_BUFFERING
                        initialized -> Player.STATE_READY
                        else -> Player.STATE_IDLE
                    }
                    invalidateState()
                }
            }
        }
    }

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_SET_VOLUME)
            .build()
        val playlist = MaskingSoundId.entries.map { sound ->
            val item = localizedMediaItemFor(sound)
            MediaItemData.Builder(item.mediaId)
                .setMediaItem(item)
                .build()
        }
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReady, playWhenReadyChangeReason)
            .setPlaybackState(playbackState)
            .setPlayerError(playerError)
            .setPlaybackSuppressionReason(
                if (focusSuppressed) Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
                else Player.PLAYBACK_SUPPRESSION_REASON_NONE,
            )
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(selectedSound.index)
            .setVolume(volume)
            .build()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        ensureInitialized()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        handler.removeCallbacks(releaseEngine)
        if (playWhenReady) {
            ensureInitialized()
            if (!initialized) {
                this.playWhenReady = false
                handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
                invalidateState()
                return Futures.immediateVoidFuture()
            }
            when (requestAudioFocus()) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                    focusSuppressed = false
                    resumeAfterTransientLoss = false
                    this.playWhenReady = true
                    engine.setPlaying(true)
                }
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                    focusSuppressed = true
                    resumeAfterTransientLoss = true
                    this.playWhenReady = true
                    engine.setPlaying(false)
                }
                else -> {
                    focusSuppressed = false
                    this.playWhenReady = false
                    engine.setPlaying(false)
                    handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
                }
            }
        } else {
            this.playWhenReady = false
            focusSuppressed = false
            resumeAfterTransientLoss = false
            focusDuck = 1f
            engine.setPlaying(false)
            abandonAudioFocus()
            handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playWhenReady = false
        playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        focusDuck = 1f
        engine.setPlaying(false)
        engine.stopCapture()
        resetAmbientScale()
        abandonAudioFocus()
        handler.removeCallbacks(releaseEngine)
        handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        releaseNative()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(
        volume: Float,
        volumeOperationType: Int,
    ): ListenableFuture<*> {
        this.volume = volume.coerceIn(0f, 1f)
        applyEffectiveVolume()
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    /**
     * Sets ambient intensity (0..1). Quiet rooms use a low scale so the mask
     * stays under the user volume ceiling without exceeding it.
     */
    fun setAmbientScale(scale: Float) {
        ambientScale = scale.coerceIn(0f, 1f)
        applyEffectiveVolume()
    }

    fun resetAmbientScale() {
        ambientScale = 1f
        applyEffectiveVolume()
    }

    override fun handleSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val selected = mediaItems.getOrNull(startIndex.coerceAtLeast(0)) ?: mediaItems.firstOrNull()
        val sound = selected?.mediaId?.let { id ->
            runCatching { MaskingSoundId.valueOf(id) }.getOrNull()
        } ?: selectedSound
        setSound(sound)
        return Futures.immediateVoidFuture()
    }

    fun setSound(sound: MaskingSoundId, crossfadeSeconds: Float = 0.75f) {
        if (sound == selectedSound && initialized) return
        selectedSound = sound
        if (initialized) prepareSound(sound, crossfadeSeconds)
        invalidateState()
    }

    fun startCapture(): Boolean = initialized && engine.startCapture()

    fun stopCapture() {
        engine.stopCapture()
        resetAmbientScale()
    }

    fun xRunCount(): Int = engine.xRunCount()

    override fun onAudioFocusChange(focusChange: Int) {
        handler.post {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focusSuppressed = false
                    focusDuck = 1f
                    applyEffectiveVolume()
                    if (playWhenReady && resumeAfterTransientLoss) engine.setPlaying(true)
                    resumeAfterTransientLoss = false
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    resumeAfterTransientLoss = playWhenReady
                    focusSuppressed = true
                    engine.setPlaying(false)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    focusSuppressed = false
                    focusDuck = DUCK_FACTOR
                    applyEffectiveVolume()
                }
                AudioManager.AUDIOFOCUS_LOSS -> {
                    resumeAfterTransientLoss = false
                    focusSuppressed = false
                    focusDuck = 1f
                    playWhenReady = false
                    playWhenReadyChangeReason =
                        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
                    engine.setPlaying(false)
                    abandonAudioFocus()
                    handler.postDelayed(releaseEngine, ENGINE_RELEASE_DELAY_MS)
                }
            }
            invalidateState()
        }
    }

    private fun applyEffectiveVolume() {
        if (!initialized) return
        val effective = (volume * ambientScale * focusDuck).coerceIn(0f, 1f)
        engine.setVolume(effective)
    }

    fun releaseNative() {
        recoveryObserver?.cancel()
        recoveryObserver = null
        decodeJob?.cancel()
        decodeJob = null
        handler.removeCallbacks(releaseEngine)
        engine.stopCapture()
        engine.release()
        abandonAudioFocus()
        initialized = false
    }

    private fun ensureInitialized() {
        if (initialized) return
        initialized = engine.init()
        playbackState = if (initialized) Player.STATE_READY else Player.STATE_IDLE
        playerError = if (initialized) null else PlaybackException(
            context.getString(R.string.error_audio_initialization),
            null,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        )
        if (initialized) {
            prepareSound(selectedSound, 0f)
            applyEffectiveVolume()
        }
    }

    private fun prepareSound(sound: MaskingSoundId, crossfadeSeconds: Float) {
        decodeJob?.cancel()
        val assetFile = sound.assetFile
        if (assetFile == null) {
            engine.setSound(sound, crossfadeSeconds)
            return
        }
        decodeJob = scope.launch(Dispatchers.IO) {
            val decoded = AssetSoundDecoder.decode(context, sound)
            if (decoded != null) {
                engine.loadPcm16(sound, decoded.samples, decoded.sampleRate)
            }
            handler.post {
                if (selectedSound == sound && initialized) {
                    engine.setSound(sound, crossfadeSeconds)
                }
            }
        }
    }

    private fun requestAudioFocus(): Int {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(this, handler)
            .build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun localizedMediaItemFor(sound: MaskingSoundId): MediaItem =
        MediaItem.Builder()
            .setMediaId(sound.name)
            .setUri("noiseshield://${sound.name}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(
                        context.getString(
                            when (sound) {
                                MaskingSoundId.WHITE_NOISE -> R.string.sound_white_noise
                                MaskingSoundId.PINK_NOISE -> R.string.sound_pink_noise
                                MaskingSoundId.BROWN_NOISE -> R.string.sound_brown_noise
                                MaskingSoundId.OCEAN_WAVES -> R.string.sound_ocean_waves
                                MaskingSoundId.RAIN -> R.string.sound_rain
                                MaskingSoundId.FAN -> R.string.sound_fan
                                MaskingSoundId.AIR_CONDITIONER -> R.string.sound_air_conditioner
                                MaskingSoundId.CAFE_AMBIENCE -> R.string.sound_cafe_ambience
                            },
                        ),
                    )
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

    companion object {
        private const val ENGINE_RELEASE_DELAY_MS = 30_000L
        private const val DUCK_FACTOR = 0.2f

        fun mediaItemFor(sound: MaskingSoundId): MediaItem =
            MediaItem.Builder()
                .setMediaId(sound.name)
                .setUri("noiseshield://${sound.name}")
                .build()
    }
}
