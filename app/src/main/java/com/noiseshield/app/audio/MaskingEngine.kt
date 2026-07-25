package com.noiseshield.app.audio

import com.noiseshield.app.data.MaskingSoundId
import com.noiseshield.app.data.NoiseAnalysis
import com.noiseshield.app.data.NoiseLevelBucket
import android.os.SystemClock
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
    private val _estimate = MutableStateFlow<NoiseAnalysis?>(null)
    val estimate: StateFlow<NoiseAnalysis?> = _estimate.asStateFlow()
    private val _recoveryState = MutableStateFlow(0)
    val recoveryState: StateFlow<Int> = _recoveryState.asStateFlow()

    private var pollJob: Job? = null
    private var recoveryJob: Job? = null
    private var initialized = false

    fun init(): Boolean {
        if (initialized) return true
        initialized = native.init()
        if (initialized) {
            recoveryJob?.cancel()
            recoveryJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    _recoveryState.value = native.pollRecoveryState()
                    delay(250)
                }
            }
        }
        return initialized
    }

    fun release() {
        stopCapture()
        recoveryJob?.cancel()
        recoveryJob = null
        _recoveryState.value = 0
        native.release()
        initialized = false
    }

    fun setPlaying(playing: Boolean) = native.setPlaying(playing)

    fun setVolume(volume: Float) = native.setVolume(volume)

    fun setSound(sound: MaskingSoundId, crossfadeSeconds: Float = 0.75f) {
        native.setSound(sound.index, crossfadeSeconds)
    }

    fun loadPcm16(sound: MaskingSoundId, samples: ShortArray, sampleRate: Int) {
        native.loadPcm16(sound.index, samples, sampleRate)
    }

    fun xRunCount(): Int = native.getXRunCount()

    fun startCapture(): Boolean {
        val ok = native.startCapture()
        if (ok) {
            pollJob?.cancel()
            pollJob = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    val raw = withContext(Dispatchers.IO) { native.pollEstimate() }
                    if (raw != null && raw.size >= 28) {
                        _estimate.value = NoiseAnalysis(
                            relativeDbfs = raw[0],
                            levelBucket = NoiseLevelBucket.fromOrdinal(raw[1].toInt()),
                            suggestedSoundId = MaskingSoundId.fromIndex(raw[2].toInt()),
                            confidence = raw[3],
                            melBandEnergies = raw.copyOfRange(4, 28).toList(),
                            capturedAtElapsedRealtime = SystemClock.elapsedRealtime(),
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
