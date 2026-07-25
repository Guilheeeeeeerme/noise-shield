#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>

#include <oboe/Oboe.h>

namespace noise {

class MicCapture : public oboe::AudioStreamDataCallback,
                   public oboe::AudioStreamErrorCallback {
public:
    MicCapture();
    ~MicCapture() override;

    bool start(int32_t sampleRate = 16000);
    void stop();
    bool isRunning() const { return running_.load(); }

    /** Copy latest mono window into dst; returns frames copied. */
    int32_t copyLatestWindow(float *dst, int32_t maxFrames);

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *stream,
            void *audioData,
            int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex mutex_;
    std::atomic<bool> running_{false};
    std::vector<float> ring_;
    int32_t writePos_ = 0;
    int32_t filled_ = 0;
    int32_t sampleRate_ = 16000;
    static constexpr int32_t kWindowFrames = 16000;  // ~1s @ 16k
};

}  // namespace noise
