#include "capture/mic_capture.h"

#include <algorithm>
#include <chrono>

namespace noise {

MicCapture::MicCapture() : restartThread_(&MicCapture::restartLoop, this) {}

MicCapture::~MicCapture() {
    shuttingDown_.store(true);
    restartCondition_.notify_one();
    if (restartThread_.joinable()) restartThread_.join();
    stop();
}

bool MicCapture::start() {
    desiredRunning_.store(true);
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    if (stream_) return true;
    writeSequence_.store(0);
    return openStreamLocked();
}

bool MicCapture::openStreamLocked() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setInputPreset(oboe::InputPreset::Unprocessed)
            ->setDataCallback(this)
            ->setErrorCallback(this);
    const int32_t deviceId = preferredDeviceId_.load(std::memory_order_acquire);
    if (deviceId > 0) builder.setDeviceId(deviceId);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        builder.setInputPreset(oboe::InputPreset::Generic)
                ->setSharingMode(oboe::SharingMode::Shared);
        if (deviceId > 0) builder.setDeviceId(deviceId);
        result = builder.openStream(stream_);
    }
    if (result != oboe::Result::OK || !stream_) return false;
    sampleRate_.store(stream_->getSampleRate());
    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        stream_->close();
        stream_.reset();
        return false;
    }
    running_.store(true);
    restartRequested_.store(false);
    return true;
}

void MicCapture::setPreferredDeviceId(int32_t deviceId) {
    const int32_t next = std::max(0, deviceId);
    if (preferredDeviceId_.exchange(next, std::memory_order_acq_rel) == next) return;
    if (!desiredRunning_.load()) return;
    restartRequested_.store(true, std::memory_order_release);
    restartCondition_.notify_one();
}

void MicCapture::stop() {
    desiredRunning_.store(false);
    restartRequested_.store(false);
    recovering_.store(false);
    running_.store(false);
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    if (!stream_) return;
    stream_->stop();
    stream_->close();
    stream_.reset();
}

int32_t MicCapture::copyLatestWindow(float *dst, int32_t maxFrames) const {
    if (!dst || maxFrames <= 0) return 0;
    const uint64_t end = writeSequence_.load(std::memory_order_acquire);
    const int32_t count = static_cast<int32_t>(
            std::min<uint64_t>(std::min<uint32_t>(maxFrames, kRingFrames), end));
    const uint64_t start = end - count;
    for (int32_t i = 0; i < count; ++i) {
        dst[i] = ring_[(start + static_cast<uint64_t>(i)) % kRingFrames];
    }
    return count;
}

oboe::DataCallbackResult MicCapture::onAudioReady(
        oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    const bool floatInput = stream->getFormat() == oboe::AudioFormat::Float;
    const auto *inputFloat = static_cast<const float *>(audioData);
    const auto *inputI16 = static_cast<const int16_t *>(audioData);
    uint64_t sequence = writeSequence_.load(std::memory_order_relaxed);
    for (int32_t i = 0; i < numFrames; ++i) {
        ring_[sequence % kRingFrames] =
                floatInput ? inputFloat[i] : inputI16[i] / 32768.0f;
        ++sequence;
    }
    writeSequence_.store(sequence, std::memory_order_release);
    return oboe::DataCallbackResult::Continue;
}

void MicCapture::onErrorAfterClose(oboe::AudioStream *, oboe::Result) {
    running_.store(false, std::memory_order_release);
    recovering_.store(true, std::memory_order_release);
    restartRequested_.store(true, std::memory_order_release);
    restartCondition_.notify_one();
}

void MicCapture::restartLoop() {
    std::mutex waitMutex;
    std::unique_lock<std::mutex> waitLock(waitMutex);
    while (!shuttingDown_.load()) {
        restartCondition_.wait(waitLock, [this] {
            return shuttingDown_.load() || restartRequested_.load();
        });
        if (shuttingDown_.load()) break;
        restartRequested_.store(false);
        if (!desiredRunning_.load()) continue;
        std::lock_guard<std::mutex> lifecycleLock(lifecycleMutex_);
        if (stream_) {
            stream_->close();
            stream_.reset();
        }
        if (openStreamLocked()) recovering_.store(false, std::memory_order_release);
        else {
            std::this_thread::sleep_for(std::chrono::milliseconds(250));
            restartRequested_.store(true, std::memory_order_release);
        }
    }
}

}  // namespace noise
