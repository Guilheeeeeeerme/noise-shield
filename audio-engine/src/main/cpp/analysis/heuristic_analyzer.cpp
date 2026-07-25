#include "analysis/heuristic_analyzer.h"
#include "analysis/mask_mel_templates.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <complex>
#include <limits>
#include <numeric>

namespace noise {
namespace {
constexpr int kFftSize = 2048;
constexpr int kHopSize = 1024;
constexpr int kSampleRate = 16000;
constexpr float kPi = 3.14159265359f;
constexpr float kEpsilon = 1.0e-12f;

float hzToMel(float hz) { return 2595.0f * std::log10(1.0f + hz / 700.0f); }
float melToHz(float mel) { return 700.0f * (std::pow(10.0f, mel / 2595.0f) - 1.0f); }

void fft(std::array<std::complex<float>, kFftSize> &values) {
    for (int i = 1, j = 0; i < kFftSize; ++i) {
        int bit = kFftSize >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(values[i], values[j]);
    }
    for (int length = 2; length <= kFftSize; length <<= 1) {
        const std::complex<float> step =
                std::polar(1.0f, -2.0f * kPi / static_cast<float>(length));
        for (int offset = 0; offset < kFftSize; offset += length) {
            std::complex<float> rotation(1.0f, 0.0f);
            for (int j = 0; j < length / 2; ++j) {
                const auto even = values[offset + j];
                const auto odd = values[offset + j + length / 2] * rotation;
                values[offset + j] = even + odd;
                values[offset + j + length / 2] = even - odd;
                rotation *= step;
            }
        }
    }
}

std::array<float, 26> melEdges() {
    std::array<float, 26> result{};
    const float low = hzToMel(80.0f);
    const float high = hzToMel(8000.0f);
    for (size_t i = 0; i < result.size(); ++i) {
        result[i] = melToHz(low + (high - low) * static_cast<float>(i) / 25.0f);
    }
    return result;
}

}  // namespace

NoiseAnalysis HeuristicAnalyzer::analyze(const float *samples, int32_t frameCount) {
    NoiseAnalysis result;
    if (!samples || frameCount < kFftSize) return result;

    const auto edges = melEdges();
    std::array<float, 24> mel{};
    double squareSum = 0.0;
    int64_t sampleCount = 0;
    double centroidNumerator = 0.0;
    double spectrumTotal = 0.0;
    double logMagnitude = 0.0;
    int32_t spectrumBins = 0;

    int windows = 0;
    for (int32_t start = 0; start + kFftSize <= frameCount; start += kHopSize) {
        std::array<std::complex<float>, kFftSize> spectrum{};
        for (int i = 0; i < kFftSize; ++i) {
            const float sample = samples[start + i];
            const float hann = 0.5f - 0.5f * std::cos(2.0f * kPi * i / (kFftSize - 1));
            spectrum[i] = sample * hann;
            squareSum += static_cast<double>(sample) * sample;
            ++sampleCount;
        }
        fft(spectrum);
        for (int bin = 1; bin <= kFftSize / 2; ++bin) {
            const float hz = static_cast<float>(bin * kSampleRate) / kFftSize;
            const float power = std::norm(spectrum[bin]) + kEpsilon;
            centroidNumerator += hz * power;
            spectrumTotal += power;
            logMagnitude += std::log(power);
            ++spectrumBins;
            for (int band = 0; band < 24; ++band) {
                if (hz < edges[band] || hz > edges[band + 2]) continue;
                const float weight = hz <= edges[band + 1]
                        ? (hz - edges[band]) / (edges[band + 1] - edges[band])
                        : (edges[band + 2] - hz) / (edges[band + 2] - edges[band + 1]);
                mel[band] += power * std::max(0.0f, weight);
            }
        }
        ++windows;
    }
    if (windows == 0 || sampleCount == 0) return result;

    const float rms = std::sqrt(static_cast<float>(squareSum / sampleCount));
    const float dbfs = 20.0f * std::log10(std::max(rms, 1.0e-5f));
    const float melTotal = std::max(kEpsilon, std::accumulate(mel.begin(), mel.end(), 0.0f));
    for (float &energy : mel) energy /= melTotal;

    constexpr float emaAlpha = 1.0f / 3.0f;
    if (!hasSmoothed_) {
        smoothedMel_ = mel;
        smoothedDbfs_ = dbfs;
        hasSmoothed_ = true;
    } else {
        for (int band = 0; band < 24; ++band) {
            smoothedMel_[band] += emaAlpha * (mel[band] - smoothedMel_[band]);
        }
        smoothedDbfs_ += emaAlpha * (dbfs - smoothedDbfs_);
    }

    result.relativeDbfs = smoothedDbfs_;
    result.levelBucket = smoothedDbfs_ < -40.0f ? LevelBucket::Low
            : smoothedDbfs_ > -20.0f ? LevelBucket::High : LevelBucket::Medium;
    result.melBandEnergies = smoothedMel_;
    result.spectralCentroid = spectrumTotal > 0.0
            ? static_cast<float>(centroidNumerator / spectrumTotal) : 0.0f;
    const float arithmeticMean = static_cast<float>(spectrumTotal / std::max(1, spectrumBins));
    result.spectralFlatness = arithmeticMean > 0.0f
            ? std::exp(static_cast<float>(logMagnitude / std::max(1, spectrumBins))) /
                    arithmeticMean : 0.0f;
    for (int band = 0; band < 24; ++band) {
        result.bandRatios[band < 8 ? 0 : band < 16 ? 1 : 2] += smoothedMel_[band];
    }

    float bestScore = -std::numeric_limits<float>::infinity();
    float secondScore = bestScore;
    int bestSound = 0;
    const auto &templates = kMaskMelTemplates;
    for (int sound = 0; sound < 8; ++sound) {
        float coverage = 0.0f;
        float distance = 0.0f;
        for (int band = 0; band < 24; ++band) {
            coverage += std::min(smoothedMel_[band], templates[sound][band]);
            distance += std::abs(smoothedMel_[band] - templates[sound][band]);
        }
        const float score = coverage - 0.25f * distance;
        if (score > bestScore) {
            secondScore = bestScore;
            bestScore = score;
            bestSound = sound;
        } else if (score > secondScore) {
            secondScore = score;
        }
    }
    result.suggestedSoundId = bestSound;
    result.confidence = std::clamp(bestScore - secondScore, 0.0f, 1.0f);
    return result;
}

}  // namespace noise
