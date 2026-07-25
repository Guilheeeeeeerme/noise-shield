#include "player/masking_player.h"

#include "analysis/noise_synth.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <numeric>
#include <limits>

namespace noise {

namespace {
constexpr float kHalfPi = 1.57079632679f;
constexpr float kRampSeconds = 0.150f;
}

MaskingPlayer::MaskingPlayer() {
    for (auto &buffer : publishedBuffers_) buffer.store(nullptr);
    restartThread_ = std::thread(&MaskingPlayer::restartLoop, this);
}
MaskingPlayer::~MaskingPlayer() {
    shuttingDown_.store(true);
    restartCondition_.notify_one();
    if (restartThread_.joinable()) restartThread_.join();
    stop();
}

bool MaskingPlayer::start() {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    if (stream_) return true;
    return openStreamLocked();
}

bool MaskingPlayer::openStreamLocked() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setDataCallback(this)
            ->setErrorCallback(this);
    const int32_t deviceId = preferredDeviceId_.load(std::memory_order_acquire);
    if (deviceId > 0) builder.setDeviceId(deviceId);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        builder.setSharingMode(oboe::SharingMode::Shared);
        if (deviceId > 0) builder.setDeviceId(deviceId);
        result = builder.openStream(stream_);
    }
    if (result != oboe::Result::OK || !stream_) return false;

    sampleRate_ = stream_->getSampleRate();
    currentBuffer_ = prepareBuffer(current_);
    targetBuffer_ = prepareBuffer(target_);
    retiredBuffers_.clear();
    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        stream_->close();
        stream_.reset();
        return false;
    }
    restartRequested_.store(false);
    return true;
}

void MaskingPlayer::setPreferredDeviceId(int32_t deviceId) {
    preferredDeviceId_.store(std::max(0, deviceId), std::memory_order_release);
    // Apply on next open if the stream is not running yet.
    if (!stream_) return;
    restartRequested_.store(true, std::memory_order_release);
    restartCondition_.notify_one();
}

void MaskingPlayer::stop() {
    playRequested_.store(false);
    restartRequested_.store(false);
    recovering_.store(false);
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    if (!stream_) return;
    stream_->stop();
    stream_->close();
    stream_.reset();
}

void MaskingPlayer::setPlaying(bool playing) {
    if (playing && (restartRequested_.load() || !stream_)) start();
    playRequested_.store(playing, std::memory_order_release);
}

void MaskingPlayer::setVolume(float sliderPosition) {
    const float slider = std::clamp(sliderPosition, 0.0f, 1.0f);
    targetGain_.store(slider * slider, std::memory_order_release);
}

void MaskingPlayer::loadPcm16(
        SoundId id, const int16_t *samples, int32_t frameCount, int32_t sampleRate) {
    if (id < SoundId::WhiteNoise || id >= SoundId::Count ||
        !samples || frameCount <= 0 || sampleRate <= 0) return;
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    auto buffer = std::make_shared<Buffer>();
    if (sampleRate == sampleRate_) {
        buffer->samples.assign(samples, samples + frameCount);
    } else {
        const int32_t outputFrames = static_cast<int32_t>(
                static_cast<int64_t>(frameCount) * sampleRate_ / sampleRate);
        buffer->samples.resize(outputFrames);
        const double ratio = static_cast<double>(sampleRate) / sampleRate_;
        for (int32_t i = 0; i < outputFrames; ++i) {
            const double sourcePosition = i * ratio;
            const int32_t sourceIndex = std::min(
                    static_cast<int32_t>(sourcePosition), frameCount - 1);
            const int32_t next = std::min(sourceIndex + 1, frameCount - 1);
            const float fraction = static_cast<float>(sourcePosition - sourceIndex);
            const float value = samples[sourceIndex] +
                    (samples[next] - samples[sourceIndex]) * fraction;
            buffer->samples[i] = static_cast<int16_t>(std::clamp(
                    value,
                    static_cast<float>(std::numeric_limits<int16_t>::min()),
                    static_cast<float>(std::numeric_limits<int16_t>::max())));
        }
    }
    buffer->sampleRate = sampleRate_;
    const auto index = static_cast<size_t>(id);
    if (buffers_[index]) retiredBuffers_.push_back(buffers_[index]);
    buffers_[index] = buffer;
    publishedBuffers_[index].store(buffer.get(), std::memory_order_release);
}

int32_t MaskingPlayer::xRunCount() {
    std::lock_guard<std::mutex> lock(lifecycleMutex_);
    if (!stream_) return 0;
    const auto result = stream_->getXRunCount();
    return result ? result.value() : 0;
}

void MaskingPlayer::setSound(SoundId id, float crossfadeSeconds) {
    const uint64_t write = commandWriteSequence_.load(std::memory_order_relaxed);
    commandRing_[write % kCommandRingSize] = {id, crossfadeSeconds};
    commandWriteSequence_.store(write + 1, std::memory_order_release);
    cleanupPending_.store(true, std::memory_order_release);
    restartCondition_.notify_one();
}

const MaskingPlayer::Buffer *MaskingPlayer::prepareBuffer(SoundId id) {
    const auto index = static_cast<size_t>(id);
    auto &slot = buffers_[index];
    if (slot && slot->sampleRate == sampleRate_) return slot.get();
    auto generated = std::make_shared<Buffer>();
    if (id > SoundId::BrownNoise) {
        const auto source = synthesizeSound(id, 48000, 30);
        std::vector<float> synthesized;
        if (sampleRate_ == 48000) {
            synthesized = source;
        } else {
            synthesized.resize(
                    static_cast<size_t>(static_cast<int64_t>(source.size()) * sampleRate_ / 48000));
            const double ratio = 48000.0 / sampleRate_;
            for (size_t i = 0; i < synthesized.size(); ++i) {
                const double sourcePosition = i * ratio;
                const size_t sourceIndex = std::min(
                        static_cast<size_t>(sourcePosition), source.size() - 1);
                const size_t next = std::min(sourceIndex + 1, source.size() - 1);
                const float fraction = static_cast<float>(sourcePosition - sourceIndex);
                synthesized[i] = source[sourceIndex] +
                        (source[next] - source[sourceIndex]) * fraction;
            }
        }
        const float peak = std::max(1.0e-6f, std::accumulate(
                synthesized.begin(), synthesized.end(), 0.0f,
                [](float current, float sample) {
                    return std::max(current, std::abs(sample));
                }));
        const float normalization = 0.7079458f / peak;
        generated->samples.resize(synthesized.size());
        std::transform(synthesized.begin(), synthesized.end(), generated->samples.begin(), [normalization](float sample) {
            return static_cast<int16_t>(
                    std::clamp(sample * normalization, -0.7079458f, 0.7079458f) * 32767.0f);
        });
    }
    generated->sampleRate = sampleRate_;
    if (slot) retiredBuffers_.push_back(slot);
    slot = generated;
    publishedBuffers_[index].store(slot.get(), std::memory_order_release);
    return slot.get();
}

float MaskingPlayer::readSample(const Buffer &buffer, double &phase) {
    if (buffer.samples.empty()) return 0.0f;
    const auto size = static_cast<double>(buffer.samples.size());
    while (phase >= size) phase -= size;
    const auto index = static_cast<size_t>(phase);
    const auto next = (index + 1) % buffer.samples.size();
    const float fraction = static_cast<float>(phase - index);
    const float a = buffer.samples[index] / 32768.0f;
    const float b = buffer.samples[next] / 32768.0f;
    float sample = a + (b - a) * fraction;
    const auto fadeFrames = static_cast<size_t>(
            std::min(size / 4.0, buffer.sampleRate * 0.75));
    const auto fadeStart = buffer.samples.size() - fadeFrames;
    if (index >= fadeStart && fadeFrames > 1) {
        const size_t headIndex = index - fadeStart;
        const size_t headNext = (headIndex + 1) % buffer.samples.size();
        const float headA = buffer.samples[headIndex] / 32768.0f;
        const float headB = buffer.samples[headNext] / 32768.0f;
        const float head = headA + (headB - headA) * fraction;
        const float position = static_cast<float>(index - fadeStart) / (fadeFrames - 1);
        sample = sample * std::cos(position * kHalfPi) +
                head * std::sin(position * kHalfPi);
    }
    phase += 1.0;
    if (phase >= size && fadeFrames > 0) phase = static_cast<double>(fadeFrames);
    return sample;
}

float MaskingPlayer::readSource(
        SoundId id, const Buffer *buffer, double &phase, NoiseState &state) {
    state.rng ^= state.rng << 13;
    state.rng ^= state.rng >> 17;
    state.rng ^= state.rng << 5;
    const float white = (static_cast<float>(state.rng) / 2147483648.0f) - 1.0f;
    switch (id) {
        case SoundId::WhiteNoise:
            return white * 0.7079458f;  // -3 dBFS peak scale
        case SoundId::PinkNoise:
            state.pink[0] = 0.99886f * state.pink[0] + white * 0.0555179f;
            state.pink[1] = 0.99332f * state.pink[1] + white * 0.0750759f;
            state.pink[2] = 0.96900f * state.pink[2] + white * 0.1538520f;
            state.pink[3] = 0.86650f * state.pink[3] + white * 0.3104856f;
            state.pink[4] = 0.55000f * state.pink[4] + white * 0.5329522f;
            state.pink[5] = -0.7616f * state.pink[5] - white * 0.0168980f;
            state.pink[6] = white * 0.115926f;
            return std::clamp(
                    (state.pink[0] + state.pink[1] + state.pink[2] + state.pink[3] +
                     state.pink[4] + state.pink[5] + state.pink[6] + white * 0.5362f) *
                            0.11f,
                    -0.7079458f, 0.7079458f);
        case SoundId::BrownNoise:
            state.brown = std::clamp(state.brown + white * 0.02f, -1.0f, 1.0f);
            return state.brown * 0.7079458f;
        default:
            return buffer ? readSample(*buffer, phase) : 0.0f;
    }
}

oboe::DataCallbackResult MaskingPlayer::onAudioReady(
        oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    const bool floatOutput = stream->getFormat() == oboe::AudioFormat::Float;
    auto *outFloat = static_cast<float *>(audioData);
    auto *outI16 = static_cast<int16_t *>(audioData);
    const auto requested = static_cast<SoundId>(requestedSound_.load(std::memory_order_acquire));
    if (requested != target_) {
        current_ = target_;
        currentBuffer_ = targetBuffer_;
        phaseA_ = phaseB_;
        noiseA_ = noiseB_;
        target_ = requested;
        callbackCurrentSound_.store(static_cast<int32_t>(current_), std::memory_order_release);
        callbackTargetSound_.store(static_cast<int32_t>(target_), std::memory_order_release);
        targetBuffer_ = publishedBuffers_[static_cast<size_t>(target_)]
                .load(std::memory_order_acquire);
        phaseB_ = 0.0;
        noiseB_ = NoiseState{};
        noiseB_.rng += static_cast<uint32_t>(target_) * 97u;
        crossfadePos_ = 0.0f;
        crossfadeStep_ = 1.0f / requestedCrossfadeFrames_.load(std::memory_order_relaxed);
    }

    const float requestedGain =
            playRequested_.load(std::memory_order_acquire) ? targetGain_.load() : 0.0f;
    if (requestedGain != lastRequestedGain_) {
        lastRequestedGain_ = requestedGain;
        gainFramesRemaining_ = std::max(1, static_cast<int32_t>(kRampSeconds * sampleRate_));
        gainStep_ = (requestedGain - currentGain_) / gainFramesRemaining_;
    }
    for (int32_t i = 0; i < numFrames; ++i) {
        if (gainFramesRemaining_ > 0) {
            currentGain_ += gainStep_;
            --gainFramesRemaining_;
            if (gainFramesRemaining_ == 0) currentGain_ = lastRequestedGain_;
        }

        const float a = readSource(current_, currentBuffer_, phaseA_, noiseA_);
        const float b = readSource(target_, targetBuffer_, phaseB_, noiseB_);
        float mix = b;
        if (crossfadePos_ < 1.0f) {
            const float angle = crossfadePos_ * kHalfPi;
            mix = a * std::cos(angle) + b * std::sin(angle);
            crossfadePos_ = std::min(1.0f, crossfadePos_ + crossfadeStep_);
            if (crossfadePos_ >= 1.0f) {
                current_ = target_;
                currentBuffer_ = targetBuffer_;
                phaseA_ = phaseB_;
                noiseA_ = noiseB_;
                callbackCurrentSound_.store(
                        static_cast<int32_t>(current_), std::memory_order_release);
                callbackTargetSound_.store(
                        static_cast<int32_t>(target_), std::memory_order_release);
            }
        }
        const float output = std::clamp(mix * currentGain_, -1.0f, 1.0f);
        if (floatOutput) outFloat[i] = output;
        else outI16[i] = static_cast<int16_t>(output * 32767.0f);
    }
    return oboe::DataCallbackResult::Continue;
}

void MaskingPlayer::onErrorAfterClose(oboe::AudioStream *, oboe::Result) {
    recovering_.store(true, std::memory_order_release);
    restartRequested_.store(true, std::memory_order_release);
    restartCondition_.notify_one();
}

void MaskingPlayer::restartLoop() {
    std::mutex waitMutex;
    std::unique_lock<std::mutex> waitLock(waitMutex);
    while (!shuttingDown_.load()) {
        const auto hasWork = [this] {
            return shuttingDown_.load() || restartRequested_.load() ||
                    commandReadSequence_.load() < commandWriteSequence_.load();
        };
        if (cleanupPending_.load(std::memory_order_acquire)) {
            restartCondition_.wait_for(waitLock, std::chrono::milliseconds(100), hasWork);
        } else {
            restartCondition_.wait(waitLock, hasWork);
        }
        if (shuttingDown_.load()) break;
        {
            std::lock_guard<std::mutex> lifecycleLock(lifecycleMutex_);
            cleanupBuffersLocked();
        }
        if (restartRequested_.exchange(false)) {
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
        const uint64_t write = commandWriteSequence_.load(std::memory_order_acquire);
        uint64_t read = commandReadSequence_.load(std::memory_order_relaxed);
        if (read < write) {
            if (write - read > kCommandRingSize) read = write - kCommandRingSize;
            Command command = commandRing_[read % kCommandRingSize];
            while (++read < write) command = commandRing_[read % kCommandRingSize];
            commandReadSequence_.store(write, std::memory_order_release);
            std::lock_guard<std::mutex> lifecycleLock(lifecycleMutex_);
            prepareBuffer(command.sound);
            if (commandReadSequence_.load() == commandWriteSequence_.load()) {
                requestedCrossfadeFrames_.store(
                        std::max(1, static_cast<int32_t>(
                                command.crossfadeSeconds * sampleRate_)),
                        std::memory_order_relaxed);
                requestedSound_.store(
                        static_cast<int32_t>(command.sound), std::memory_order_release);
            }
        }
    }
}

void MaskingPlayer::cleanupBuffersLocked() {
    const int32_t current = callbackCurrentSound_.load(std::memory_order_acquire);
    const int32_t target = callbackTargetSound_.load(std::memory_order_acquire);
    const int32_t requested = requestedSound_.load(std::memory_order_acquire);
    const uint64_t commandWrite = commandWriteSequence_.load(std::memory_order_acquire);
    const uint64_t commandRead = commandReadSequence_.load(std::memory_order_acquire);
    const int32_t pending = commandRead < commandWrite
            ? static_cast<int32_t>(
                    commandRing_[(commandWrite - 1) % kCommandRingSize].sound)
            : -1;
    for (int32_t sound = 0; sound < static_cast<int32_t>(SoundId::Count); ++sound) {
        if (sound == current || sound == target || sound == requested || sound == pending) continue;
        const auto index = static_cast<size_t>(sound);
        publishedBuffers_[index].store(nullptr, std::memory_order_release);
        buffers_[index].reset();
    }
    if (current == target && target == requested && pending < 0) {
        cleanupPending_.store(false, std::memory_order_release);
    }
}

}  // namespace noise
