#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
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

    bool start(int32_t sampleRate = 48000);
    void stop();
    void setPlaying(bool playing);
    void setVolume(float volume);
    void setSound(SoundId id, float crossfadeSeconds = 0.35f);
    void loadPcm(SoundId id, const float *samples, int32_t frameCount, int32_t sampleRate);
    bool isPlaying() const { return playing_.load(); }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *stream,
            void *audioData,
            int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    void ensureSynthBuffer(SoundId id, int32_t sampleRate);
    float readSample(SoundId id, double &phase, int32_t sampleRate);

    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex mutex_;
    std::atomic<bool> playing_{false};
    std::atomic<float> volume_{0.5f};

    SoundId current_{SoundId::WhiteNoise};
    SoundId target_{SoundId::WhiteNoise};
    float crossfadePos_ = 1.0f;
    float crossfadeStep_ = 0.0f;

    double phaseA_ = 0.0;
    double phaseB_ = 0.0;
    int32_t sampleRate_ = 48000;

    struct Buffer {
        std::vector<float> samples;
        int32_t sampleRate = 48000;
    };
    Buffer buffers_[static_cast<size_t>(SoundId::Count)];
};

}  // namespace noise
