#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>

#include <oboe/Oboe.h>

namespace noise {

class MicCapture : public oboe::AudioStreamDataCallback,
                   public oboe::AudioStreamErrorCallback {
public:
    MicCapture();
    ~MicCapture() override;

    bool start();
    void stop();
    bool isRunning() const { return running_.load(); }
    bool isRecovering() const { return recovering_.load(std::memory_order_acquire); }
    int32_t sampleRate() const { return sampleRate_.load(); }
    int32_t copyLatestWindow(float *dst, int32_t maxFrames) const;
    /** 0 = system default device. Reopens the stream when capture is active. */
    void setPreferredDeviceId(int32_t deviceId);

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    bool openStreamLocked();
    void restartLoop();
    static constexpr uint32_t kRingFrames = 65536;
    std::shared_ptr<oboe::AudioStream> stream_;
    std::mutex lifecycleMutex_;
    std::condition_variable restartCondition_;
    std::thread restartThread_;
    std::array<float, kRingFrames> ring_{};
    std::atomic<uint64_t> writeSequence_{0};
    std::atomic<int32_t> sampleRate_{16000};
    std::atomic<bool> running_{false};
    std::atomic<bool> restartRequested_{false};
    std::atomic<bool> desiredRunning_{false};
    std::atomic<bool> shuttingDown_{false};
    std::atomic<bool> recovering_{false};
    std::atomic<int32_t> preferredDeviceId_{0};
};

}  // namespace noise
