#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>
#include <vector>

#include <oboe/Oboe.h>

namespace noise {

enum class SoundId : int32_t {
    WhiteNoise = 0,
    PinkNoise = 1,
    BrownNoise = 2,
    OceanWaves = 3,
    Rain = 4,
    Fan = 5,
    AirConditioner = 6,
    CafeAmbience = 7,
    Count = 8,
};

class MaskingPlayer : public oboe::AudioStreamDataCallback,
                      public oboe::AudioStreamErrorCallback {
public:
    MaskingPlayer();
    ~MaskingPlayer() override;

    bool start();
    void stop();
    void setPlaying(bool playing);
    void setVolume(float sliderPosition);
    void setSound(SoundId id, float crossfadeSeconds = 0.75f);
    void loadPcm16(
            SoundId id, const int16_t *samples, int32_t frameCount, int32_t sampleRate);
    /** 0 = system default device. Reopens the stream when already open. */
    void setPreferredDeviceId(int32_t deviceId);
    bool isRecovering() const { return recovering_.load(std::memory_order_acquire); }
    int32_t xRunCount();
    bool consumeRestartRequest() { return restartRequested_.exchange(false); }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *stream,
            void *audioData,
            int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    struct Buffer {
        std::vector<int16_t> samples;
        int32_t sampleRate = 48000;
    };
    struct NoiseState {
        uint32_t rng = 0xC0FFEEu;
        float brown = 0.0f;
        std::array<float, 7> pink{};
    };
    struct Command {
        SoundId sound = SoundId::WhiteNoise;
        float crossfadeSeconds = 0.75f;
    };

    const Buffer *prepareBuffer(SoundId id);
    static float readSample(const Buffer &buffer, double &phase);
    static float readSource(SoundId id, const Buffer *buffer, double &phase, NoiseState &state);
    bool openStreamLocked();
    void restartLoop();
    void cleanupBuffersLocked();

    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex lifecycleMutex_;
    std::condition_variable restartCondition_;
    std::thread restartThread_;
    std::array<std::shared_ptr<const Buffer>, static_cast<size_t>(SoundId::Count)> buffers_{};
    std::array<std::atomic<const Buffer *>, static_cast<size_t>(SoundId::Count)> publishedBuffers_{};
    std::vector<std::shared_ptr<const Buffer>> retiredBuffers_;

    std::atomic<bool> playRequested_{false};
    std::atomic<float> targetGain_{0.09f};
    std::atomic<int32_t> requestedSound_{static_cast<int32_t>(SoundId::WhiteNoise)};
    std::atomic<int32_t> requestedCrossfadeFrames_{36000};
    static constexpr uint32_t kCommandRingSize = 16;
    std::array<Command, kCommandRingSize> commandRing_{};
    std::atomic<uint64_t> commandWriteSequence_{0};
    std::atomic<uint64_t> commandReadSequence_{0};
    std::atomic<bool> restartRequested_{false};
    std::atomic<bool> shuttingDown_{false};
    std::atomic<bool> recovering_{false};
    std::atomic<bool> cleanupPending_{false};
    std::atomic<int32_t> preferredDeviceId_{0};
    std::atomic<int32_t> callbackCurrentSound_{static_cast<int32_t>(SoundId::WhiteNoise)};
    std::atomic<int32_t> callbackTargetSound_{static_cast<int32_t>(SoundId::WhiteNoise)};

    SoundId current_{SoundId::WhiteNoise};
    SoundId target_{SoundId::WhiteNoise};
    const Buffer *currentBuffer_ = nullptr;
    const Buffer *targetBuffer_ = nullptr;
    float crossfadePos_ = 1.0f;
    float crossfadeStep_ = 0.0f;
    float currentGain_ = 0.0f;
    float lastRequestedGain_ = 0.0f;
    float gainStep_ = 0.0f;
    int32_t gainFramesRemaining_ = 0;
    double phaseA_ = 0.0;
    double phaseB_ = 0.0;
    NoiseState noiseA_;
    NoiseState noiseB_;
    int32_t sampleRate_ = 48000;
};

}  // namespace noise
