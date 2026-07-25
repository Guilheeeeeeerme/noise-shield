#include "player/masking_player.h"

#include "analysis/noise_synth.h"

#include <android/log.h>

#include <algorithm>
#include <cmath>

#define LOG_TAG "MaskingPlayer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace noise {

MaskingPlayer::MaskingPlayer() = default;

MaskingPlayer::~MaskingPlayer() {
    stop();
}

bool MaskingPlayer::start(int32_t sampleRate) {
    std::lock_guard<std::mutex> lock(mutex_);
    sampleRate_ = sampleRate;
    for (int i = 0; i < static_cast<int>(SoundId::Count); ++i) {
        ensureSynthBuffer(static_cast<SoundId>(i), sampleRate_);
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(sampleRate_)
            ->setDataCallback(this)
            ->setErrorCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(result));
        return false;
    }

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("requestStart failed: %s", oboe::convertToText(result));
        stream_.reset();
        return false;
    }
    return true;
}

void MaskingPlayer::stop() {
    playing_.store(false);
    std::lock_guard<std::mutex> lock(mutex_);
    if (stream_) {
        stream_->stop();
        stream_->close();
        stream_.reset();
    }
}

void MaskingPlayer::setPlaying(bool playing) {
    playing_.store(playing);
}

void MaskingPlayer::setVolume(float volume) {
    volume_.store(std::clamp(volume, 0.0f, 1.0f));
}

void MaskingPlayer::setSound(SoundId id, float crossfadeSeconds) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (id == target_) return;
    current_ = target_;
    target_ = id;
    phaseB_ = 0.0;
    crossfadePos_ = 0.0f;
    const float frames = std::max(1.0f, crossfadeSeconds * static_cast<float>(sampleRate_));
    crossfadeStep_ = 1.0f / frames;
}

void MaskingPlayer::loadPcm(SoundId id, const float *samples, int32_t frameCount, int32_t sampleRate) {
    if (id < SoundId::WhiteNoise || id >= SoundId::Count || samples == nullptr || frameCount <= 0) {
        return;
    }
    std::lock_guard<std::mutex> lock(mutex_);
    auto &buf = buffers_[static_cast<size_t>(id)];
    buf.samples.assign(samples, samples + frameCount);
    buf.sampleRate = sampleRate > 0 ? sampleRate : sampleRate_;
}

void MaskingPlayer::ensureSynthBuffer(SoundId id, int32_t sampleRate) {
    auto &buf = buffers_[static_cast<size_t>(id)];
    if (!buf.samples.empty() && buf.sampleRate == sampleRate) return;
    buf.samples = synthesizeSound(id, sampleRate, 4);
    buf.sampleRate = sampleRate;
}

float MaskingPlayer::readSample(SoundId id, double &phase, int32_t /*sampleRate*/) {
    const auto &buf = buffers_[static_cast<size_t>(id)];
    if (buf.samples.empty()) return 0.0f;
    const auto size = static_cast<double>(buf.samples.size());
    while (phase >= size) phase -= size;
    while (phase < 0.0) phase += size;
    const auto idx = static_cast<size_t>(phase);
    const auto next = (idx + 1) % buf.samples.size();
    const float frac = static_cast<float>(phase - static_cast<double>(idx));
    const float a = buf.samples[idx];
    const float b = buf.samples[next];
    phase += 1.0;
    return a + (b - a) * frac;
}

oboe::DataCallbackResult MaskingPlayer::onAudioReady(
        oboe::AudioStream * /*stream*/,
        void *audioData,
        int32_t numFrames) {
    auto *out = static_cast<float *>(audioData);
    const float vol = volume_.load();
    const bool playing = playing_.load();

    std::lock_guard<std::mutex> lock(mutex_);
    for (int32_t i = 0; i < numFrames; ++i) {
        if (!playing) {
            out[i] = 0.0f;
            continue;
        }

        float a = readSample(current_, phaseA_, sampleRate_);
        float b = readSample(target_, phaseB_, sampleRate_);
        float mix = b;
        if (crossfadePos_ < 1.0f) {
            mix = a * (1.0f - crossfadePos_) + b * crossfadePos_;
            crossfadePos_ = std::min(1.0f, crossfadePos_ + crossfadeStep_);
            if (crossfadePos_ >= 1.0f) {
                current_ = target_;
                phaseA_ = phaseB_;
            }
        } else {
            current_ = target_;
        }
        out[i] = mix * vol;
    }
    return oboe::DataCallbackResult::Continue;
}

void MaskingPlayer::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    LOGE("stream error: %s", oboe::convertToText(error));
    playing_.store(false);
}

}  // namespace noise
