package com.noiseshield.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.noiseshield.app.MainActivity
import com.noiseshield.app.NoiseShieldApp
import com.noiseshield.app.R
import com.noiseshield.app.audio.MaskingEngine
import com.noiseshield.app.data.MaskingSoundId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service for background masking playback (Oboe via [MaskingEngine]).
 */
class MaskingPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    private val binder = LocalBinder()
    private lateinit var engine: MaskingEngine
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private var sound: MaskingSoundId = MaskingSoundId.WHITE_NOISE
    private var volume: Float = 0.5f
    private var playing: Boolean = false

    inner class LocalBinder : Binder() {
        fun getService(): MaskingPlaybackService = this@MaskingPlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        engine = (application as NoiseShieldApp).maskingEngine
        engine.init()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createChannel()
        startAsForeground()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> setPlaying(true)
            ACTION_PAUSE -> setPlaying(false)
            ACTION_STOP -> {
                setPlaying(false)
                stopCapture()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TOGGLE -> setPlaying(!playing)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        abandonFocus()
        engine.setPlaying(false)
        engine.stopCapture()
        _isRunning.value = false
        super.onDestroy()
    }

    fun configure(soundId: MaskingSoundId, vol: Float, shouldPlay: Boolean) {
        sound = soundId
        volume = vol
        engine.setSound(soundId)
        engine.setVolume(vol)
        setPlaying(shouldPlay)
        updateNotification()
    }

    fun setSound(soundId: MaskingSoundId, crossfade: Float = 0.35f) {
        sound = soundId
        engine.setSound(soundId, crossfade)
        updateNotification()
    }

    fun setVolume(vol: Float) {
        volume = vol
        engine.setVolume(vol)
    }

    fun setPlaying(shouldPlay: Boolean) {
        if (shouldPlay) {
            if (!requestFocus()) {
                playing = false
                engine.setPlaying(false)
                _isRunning.value = false
                updateNotification()
                return
            }
            engine.setPlaying(true)
            playing = true
            _isRunning.value = true
        } else {
            engine.setPlaying(false)
            playing = false
            _isRunning.value = false
            abandonFocus()
        }
        updateNotification()
    }

    fun startCapture(): Boolean = engine.startCapture()

    fun stopCapture() = engine.stopCapture()

    fun isPlaying(): Boolean = playing

    val estimate get() = engine.estimate

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> setPlaying(false)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> engine.setVolume(volume * 0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                engine.setVolume(volume)
                if (playing) engine.setPlaying(true)
            }
        }
    }

    private fun requestFocus(): Boolean {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(this)
            .setAcceptsDelayedFocusGain(true)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val open = pendingActivity()
        val playPause = pendingAction(if (playing) ACTION_PAUSE else ACTION_PLAY)
        val stop = pendingAction(ACTION_STOP)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                if (playing) {
                    getString(R.string.notification_playing, sound.name.lowercase())
                } else {
                    getString(R.string.notification_paused)
                },
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(playing)
            .setSilent(true)
            .addAction(
                R.drawable.ic_notification,
                getString(if (playing) R.string.action_pause else R.string.action_play),
                playPause,
            )
            .addAction(R.drawable.ic_notification, getString(R.string.action_stop), stop)
            .build()
    }

    private fun pendingActivity(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pendingAction(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, MaskingPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val CHANNEL_ID = "masking_playback"
        const val NOTIFICATION_ID = 42
        const val ACTION_PLAY = "com.noiseshield.app.PLAY"
        const val ACTION_PAUSE = "com.noiseshield.app.PAUSE"
        const val ACTION_STOP = "com.noiseshield.app.STOP"
        const val ACTION_TOGGLE = "com.noiseshield.app.TOGGLE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, MaskingPlaybackService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MaskingPlaybackService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
