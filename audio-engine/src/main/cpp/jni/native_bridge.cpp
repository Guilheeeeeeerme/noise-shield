#include <jni.h>

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
constexpr float kMinConfidence = 0.55f;
}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeInit(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gPlayer) gPlayer = std::make_unique<noise::MaskingPlayer>();
    if (!gCapture) gCapture = std::make_unique<noise::MicCapture>();
    return gPlayer->start(48000) ? JNI_TRUE : JNI_FALSE;
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
Java_com_noiseshield_audio_NativeAudioEngine_nativeLoadPcm(
        JNIEnv *env,
        jobject,
        jint soundId,
        jfloatArray samples,
        jint sampleRate) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gPlayer || samples == nullptr) return;
    if (soundId < 0 || soundId >= static_cast<int>(noise::SoundId::Count)) return;
    const jsize n = env->GetArrayLength(samples);
    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    if (data == nullptr) return;
    gPlayer->loadPcm(static_cast<noise::SoundId>(soundId), data, n, sampleRate);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
}

JNIEXPORT jboolean JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeStartCapture(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    if (!gCapture) gCapture = std::make_unique<noise::MicCapture>();
    return gCapture->start(16000) ? JNI_TRUE : JNI_FALSE;
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

    std::vector<float> window(16000);
    const int32_t n = gCapture->copyLatestWindow(window.data(), static_cast<int32_t>(window.size()));
    if (n < 1024) return nullptr;

    const auto estimate = gAnalyzer.analyze(window.data(), n);
    if (estimate.confidence < kMinConfidence) return nullptr;

    // [levelBucket, rmsDb, broadProfile, confidence]
    jfloatArray out = env->NewFloatArray(4);
    if (out == nullptr) return nullptr;
    jfloat values[4] = {
            static_cast<jfloat>(estimate.levelBucket),
            estimate.rmsDb,
            static_cast<jfloat>(estimate.broadProfile),
            estimate.confidence,
    };
    env->SetFloatArrayRegion(out, 0, 4, values);
    return out;
}

JNIEXPORT jboolean JNICALL
Java_com_noiseshield_audio_NativeAudioEngine_nativeIsPlaying(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(gEngineMutex);
    return (gPlayer && gPlayer->isPlaying()) ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
