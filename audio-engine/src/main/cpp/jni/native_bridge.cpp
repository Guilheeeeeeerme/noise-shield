#include <jni.h>

#include <algorithm>
#include <memory>
#include <mutex>
#include <vector>

#include "analysis/heuristic_analyzer.h"
#include "capture/mic_capture.h"
#include "player/masking_player.h"

namespace {
std::mutex gEngineMutex;
std::unique_ptr<noise::MaskingPlayer> gPlayer;
std::unique_ptr<noise::MicCapture> gCapture;
noise::HeuristicAnalyzer gAnalyzer;

std::vector<float> resampleTo16k(
        const float *input, int32_t inputFrames, int32_t inputSampleRate) {
    if (inputSampleRate == 16000) return {input, input + inputFrames};
    const int32_t outputFrames = static_cast<int32_t>(
            static_cast<int64_t>(inputFrames) * 16000 / inputSampleRate);
    std::vector<float> output(outputFrames);
    const double ratio = static_cast<double>(inputSampleRate) / 16000.0;
    for (int32_t i = 0; i < outputFrames; ++i) {
        const double source = i * ratio;
        const int32_t index = std::min(static_cast<int32_t>(source), inputFrames - 1);
        const int32_t next = std::min(index + 1, inputFrames - 1);
        const float fraction = static_cast<float>(source - index);
        output[i] = input[index] + (input[next] - input[index]) * fraction;
    }
    return output;
}
}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeInit(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gPlayer) gPlayer = std::make_unique<noise::MaskingPlayer>();
    if (!gCapture) gCapture = std::make_unique<noise::MicCapture>();
    return gPlayer->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeRelease(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gPlayer) {
        gPlayer->stop();
        gPlayer.reset();
    }
    if (gCapture) {
        gCapture->stop();
        gCapture.reset();
    }
}

JNIEXPORT void JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeSetPlaying(JNIEnv *, jobject, jboolean playing) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gPlayer) gPlayer->setPlaying(playing == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeSetVolume(JNIEnv *, jobject, jfloat volume) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gPlayer) gPlayer->setVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeSetSound(
        JNIEnv *,
        jobject,
        jint soundId,
        jfloat crossfadeSeconds) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gPlayer) return;
    if (soundId < 0 || soundId >= static_cast<int>(noise::SoundId::Count)) return;
    gPlayer->setSound(static_cast<noise::SoundId>(soundId), crossfadeSeconds);
}

JNIEXPORT void JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeLoadPcm16(
        JNIEnv *env,
        jobject,
        jint soundId,
        jshortArray samples,
        jint sampleRate) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gPlayer || samples == nullptr) return;
    if (soundId < 0 || soundId >= static_cast<int>(noise::SoundId::Count)) return;
    const jsize count = env->GetArrayLength(samples);
    jshort *data = env->GetShortArrayElements(samples, nullptr);
    if (!data) return;
    gPlayer->loadPcm16(
            static_cast<noise::SoundId>(soundId),
            reinterpret_cast<int16_t *>(data),
            count,
            sampleRate);
    env->ReleaseShortArrayElements(samples, data, JNI_ABORT);
}

JNIEXPORT jboolean JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeStartCapture(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gCapture) gCapture = std::make_unique<noise::MicCapture>();
    return gCapture->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeStopCapture(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (gCapture) gCapture->stop();
}

JNIEXPORT jfloatArray JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativePollEstimate(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gCapture || !gCapture->isRunning()) return nullptr;

    const int32_t sourceFrames = std::max(2048, gCapture->sampleRate());
    std::vector<float> window(sourceFrames);
    const int32_t n = gCapture->copyLatestWindow(window.data(), static_cast<int32_t>(window.size()));
    if (n < 2048) return nullptr;

    const auto resampled = resampleTo16k(window.data(), n, gCapture->sampleRate());
    const auto analysis = gAnalyzer.analyze(resampled.data(), static_cast<int32_t>(resampled.size()));

    // [relativeDbfs, levelBucket, suggestedSoundId, confidence, 24 mel energies]
    constexpr int32_t kPayloadSize = 28;
    jfloatArray out = env->NewFloatArray(kPayloadSize);
    if (out == nullptr) return nullptr;
    jfloat values[kPayloadSize] = {
            analysis.relativeDbfs,
            static_cast<jfloat>(analysis.levelBucket),
            static_cast<jfloat>(analysis.suggestedSoundId),
            analysis.confidence,
    };
    for (int i = 0; i < 24; ++i) values[4 + i] = analysis.melBandEnergies[i];
    env->SetFloatArrayRegion(out, 0, kPayloadSize, values);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativePollRecoveryState(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    int32_t state = 0;
    if (gPlayer && gPlayer->isRecovering()) state |= 1;
    if (gCapture && gCapture->isRecovering()) state |= 2;
    return state;
}

JNIEXPORT jint JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeGetXRunCount(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    return gPlayer ? gPlayer->xRunCount() : 0;
}

}  // extern "C"
