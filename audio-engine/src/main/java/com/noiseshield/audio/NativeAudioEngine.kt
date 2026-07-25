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
    fun setSound(soundId: Int, crossfadeSeconds: Float = 0.75f) =
        nativeSetSound(soundId, crossfadeSeconds)
    fun loadPcm16(soundId: Int, samples: ShortArray, sampleRate: Int) =
        nativeLoadPcm16(soundId, samples, sampleRate)

    fun startCapture(): Boolean = nativeStartCapture()
    fun stopCapture() = nativeStopCapture()
    fun setInputDeviceId(deviceId: Int) = nativeSetInputDeviceId(deviceId)
    fun setOutputDeviceId(deviceId: Int) = nativeSetOutputDeviceId(deviceId)

    /** Returns [relative dBFS, level bucket, sound ID, confidence, 24 mel energies]. */
    fun pollEstimate(): FloatArray? = nativePollEstimate()

    fun pollRecoveryState(): Int = nativePollRecoveryState()
    fun getXRunCount(): Int = nativeGetXRunCount()

    private external fun nativeInit(): Boolean
    private external fun nativeRelease()
    private external fun nativeSetPlaying(playing: Boolean)
    private external fun nativeSetVolume(volume: Float)
    private external fun nativeSetSound(soundId: Int, crossfadeSeconds: Float)
    private external fun nativeLoadPcm16(soundId: Int, samples: ShortArray, sampleRate: Int)
    private external fun nativeStartCapture(): Boolean
    private external fun nativeStopCapture()
    private external fun nativeSetInputDeviceId(deviceId: Int)
    private external fun nativeSetOutputDeviceId(deviceId: Int)
    private external fun nativePollEstimate(): FloatArray?
    private external fun nativePollRecoveryState(): Int
    private external fun nativeGetXRunCount(): Int

    companion object {
        init {
            System.loadLibrary("noise_shield_audio")
        }
    }
}
