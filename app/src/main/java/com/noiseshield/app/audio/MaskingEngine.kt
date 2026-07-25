package com.noiseshield.app.audio

import com.noiseshield.app.data.BroadProfile
import com.noiseshield.app.data.MaskingSoundId
import com.noiseshield.app.data.NoiseEstimate
import com.noiseshield.app.data.NoiseLevelBucket
import com.noiseshield.audio.NativeAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kotlin facade over the Oboe JNI engine. Owns estimate polling.
 */
class MaskingEngine(
    private val scope: CoroutineScope,
    private val native: NativeAudioEngine = NativeAudioEngine(),
) {
    private val _estimate = MutableStateFlow<NoiseEstimate?>(null)
    val estimate: StateFlow<NoiseEstimate?> = _estimate.asStateFlow()

    private var pollJob: Job? = null
    private var initialized = false

    fun init(): Boolean {
        if (initialized) return true
        initialized = native.init()
        return initialized
    }

    fun release() {
        stopCapture()
        native.release()
        initialized = false
    }

    fun setPlaying(playing: Boolean) = native.setPlaying(playing)

    fun setVolume(volume: Float) = native.setVolume(volume)

    fun setSound(sound: MaskingSoundId, crossfadeSeconds: Float = 0.35f) {
        native.setSound(sound.index, crossfadeSeconds)
    }

    fun isPlaying(): Boolean = native.isPlaying()

    fun startCapture(): Boolean {
        val ok = native.startCapture()
        if (ok) {
            pollJob?.cancel()
            pollJob = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    val raw = withContext(Dispatchers.IO) { native.pollEstimate() }
                    if (raw != null && raw.size >= 4) {
                        _estimate.value = NoiseEstimate(
                            levelBucket = NoiseLevelBucket.fromOrdinal(raw[0].toInt()),
                            rmsDb = raw[1],
                            broadProfile = BroadProfile.fromOrdinal(raw[2].toInt()),
                            confidence = raw[3],
                        )
                    }
                    delay(1_000)
                }
            }
        }
        return ok
    }

    fun stopCapture() {
        pollJob?.cancel()
        pollJob = null
        native.stopCapture()
        _estimate.value = null
    }
}
