#include "analysis/heuristic_analyzer.h"

#include <algorithm>
#include <cmath>

namespace noise {

float HeuristicAnalyzer::computeRms(const float *samples, int32_t frameCount) {
    if (frameCount <= 0) return 0.0f;
    double sum = 0.0;
    for (int32_t i = 0; i < frameCount; ++i) {
        const double v = samples[i];
        sum += v * v;
    }
    return static_cast<float>(std::sqrt(sum / frameCount));
}

float HeuristicAnalyzer::rmsToDb(float rms) {
    if (rms <= 0.0f) return -100.0f;
    return 20.0f * std::log10(rms);
}

LevelBucket HeuristicAnalyzer::bucketize(float rmsDb) {
    if (rmsDb < -40.0f) return LevelBucket::Low;
    if (rmsDb > -20.0f) return LevelBucket::High;
    return LevelBucket::Medium;
}

void HeuristicAnalyzer::extractBandRatios(
        const float *samples,
        int32_t frameCount,
        float &low,
        float &mid,
        float &high) {
    const int32_t third = std::max(1, frameCount / 3);
    double l = 0, m = 0, h = 0;
    for (int32_t i = 0; i < frameCount; ++i) {
        const double v = samples[i] * samples[i];
        if (i < third) l += v;
        else if (i < third * 2) m += v;
        else h += v;
    }
    const double total = l + m + h;
    if (total <= 0.0) {
        low = mid = high = 0.0f;
        return;
    }
    low = static_cast<float>(l / total);
    mid = static_cast<float>(m / total);
    high = static_cast<float>(h / total);
}

NoiseEstimate HeuristicAnalyzer::analyze(const float *samples, int32_t frameCount) const {
    NoiseEstimate estimate;
    const float rms = computeRms(samples, frameCount);
    estimate.rmsDb = rmsToDb(rms);
    estimate.levelBucket = bucketize(estimate.rmsDb);

    float low = 0, mid = 0, high = 0;
    extractBandRatios(samples, frameCount, low, mid, high);

    if (estimate.levelBucket == LevelBucket::Low) {
        estimate.broadProfile = BroadProfile::Unknown;
        estimate.confidence = 0.4f;
        return estimate;
    }

    if (high > 0.45f && mid < 0.3f) {
        estimate.broadProfile = BroadProfile::Fan;
        estimate.confidence = 0.72f;
    } else if (low > 0.5f && mid > 0.25f) {
        estimate.broadProfile = BroadProfile::Traffic;
        estimate.confidence = 0.68f;
    } else if (mid > 0.4f && high > 0.2f && high < 0.4f) {
        estimate.broadProfile = BroadProfile::Cafe;
        estimate.confidence = 0.65f;
    } else if (low > 0.35f && high < 0.25f) {
        estimate.broadProfile = BroadProfile::Rain;
        estimate.confidence = 0.7f;
    } else if (low > 0.4f && high > 0.35f) {
        estimate.broadProfile = BroadProfile::AirConditioner;
        estimate.confidence = 0.66f;
    } else if (estimate.levelBucket == LevelBucket::High && mid > 0.35f) {
        estimate.broadProfile = BroadProfile::WhiteNoise;
        estimate.confidence = 0.6f;
    } else {
        estimate.broadProfile = BroadProfile::Unknown;
        estimate.confidence = 0.45f;
    }
    return estimate;
}

const char *profileToString(BroadProfile profile) {
    switch (profile) {
        case BroadProfile::Fan:
            return "fan";
        case BroadProfile::Traffic:
            return "traffic";
        case BroadProfile::Cafe:
            return "cafe";
        case BroadProfile::Rain:
            return "rain";
        case BroadProfile::AirConditioner:
            return "air_conditioner";
        case BroadProfile::WhiteNoise:
            return "white_noise";
        default:
            return "unknown";
    }
}

const char *levelToString(LevelBucket level) {
    switch (level) {
        case LevelBucket::Low:
            return "low";
        case LevelBucket::Medium:
            return "medium";
        case LevelBucket::High:
            return "high";
        default:
            return "low";
    }
}

}  // namespace noise
