package com.noiseshield.audio

/**
 * JNI facade over the Oboe masking player + mic analyzer.
 * Load from the app process before use.
 */
class NativeAudioEngine {
    fun init(): Boolean = nativeInit()
    fun release() = nativeRelease()

    fun setPlaying(playing: Boolean) = nativeSetPlaying(playing)
    fun setVolume(volume: Float) = nativeSetVolume(volume)
    fun setSound(soundId: Int, crossfadeSeconds: Float = 0.35f) =
        nativeSetSound(soundId, crossfadeSeconds)

    fun loadPcm(soundId: Int, samples: FloatArray, sampleRate: Int) =
        nativeLoadPcm(soundId, samples, sampleRate)

    fun startCapture(): Boolean = nativeStartCapture()
    fun stopCapture() = nativeStopCapture()

    /**
     * Returns float[4] = [levelBucket, rmsDb, broadProfile, confidence], or null.
     */
    fun pollEstimate(): FloatArray? = nativePollEstimate()

    fun isPlaying(): Boolean = nativeIsPlaying()

    private external fun nativeInit(): Boolean
    private external fun nativeRelease()
    private external fun nativeSetPlaying(playing: Boolean)
    private external fun nativeSetVolume(volume: Float)
    private external fun nativeSetSound(soundId: Int, crossfadeSeconds: Float)
    private external fun nativeLoadPcm(soundId: Int, samples: FloatArray, sampleRate: Int)
    private external fun nativeStartCapture(): Boolean
    private external fun nativeStopCapture()
    private external fun nativePollEstimate(): FloatArray?
    private external fun nativeIsPlaying(): Boolean

    companion object {
        init {
            System.loadLibrary("noise_shield_audio")
        }
    }
}
