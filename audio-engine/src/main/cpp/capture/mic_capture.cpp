#include "capture/mic_capture.h"

#include <android/log.h>

#include <algorithm>
#include <cstring>

#define LOG_TAG "MicCapture"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace noise {

MicCapture::MicCapture() {
    ring_.assign(kWindowFrames, 0.0f);
}

MicCapture::~MicCapture() {
    stop();
}

bool MicCapture::start(int32_t sampleRate) {
    stop();
    sampleRate_ = sampleRate;
    {
        std::lock_guard<std::mutex> lock(mutex_);
        ring_.assign(kWindowFrames, 0.0f);
        writePos_ = 0;
        filled_ = 0;
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(sampleRate_)
            ->setInputPreset(oboe::InputPreset::VoicePerformance)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        // Fallback shared mode
        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(stream_);
        if (result != oboe::Result::OK) {
            LOGE("open input failed: %s", oboe::convertToText(result));
            return false;
        }
    }

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("start input failed: %s", oboe::convertToText(result));
        stream_.reset();
        return false;
    }
    running_.store(true);
    return true;
}

void MicCapture::stop() {
    running_.store(false);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

int32_t MicCapture::copyLatestWindow(float *dst, int32_t maxFrames) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (filled_ <= 0 || dst == nullptr || maxFrames <= 0) return 0;
    const int32_t n = std::min(maxFrames, filled_);
    const int32_t start = (writePos_ - n + kWindowFrames) % kWindowFrames;
    for (int32_t i = 0; i < n; ++i) {
        dst[i] = ring_[(start + i) % kWindowFrames];
    }
    return n;
}

oboe::DataCallbackResult MicCapture::onAudioReady(
        oboe::AudioStream * /*stream*/,
        void *audioData,
        int32_t numFrames) {
    auto *in = static_cast<float *>(audioData);
    std::lock_guard<std::mutex> lock(mutex_);
    for (int32_t i = 0; i < numFrames; ++i) {
        ring_[writePos_] = in[i];
        writePos_ = (writePos_ + 1) % kWindowFrames;
        filled_ = std::min(filled_ + 1, kWindowFrames);
    }
    return oboe::DataCallbackResult::Continue;
}

void MicCapture::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    LOGE("mic error: %s", oboe::convertToText(error));
    running_.store(false);
}

}  // namespace noise
