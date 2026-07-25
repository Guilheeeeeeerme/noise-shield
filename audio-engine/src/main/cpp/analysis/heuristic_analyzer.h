#pragma once

#include <array>
#include <cstdint>

namespace noise {

enum class LevelBucket : int32_t { Low = 0, Medium = 1, High = 2 };

struct NoiseAnalysis {
    float relativeDbfs = -100.0f;
    LevelBucket levelBucket = LevelBucket::Low;
    int32_t suggestedSoundId = 0;
    float confidence = 0.0f;
    std::array<float, 24> melBandEnergies{};
    float spectralCentroid = 0.0f;
    float spectralFlatness = 0.0f;
    std::array<float, 3> bandRatios{};
};

class HeuristicAnalyzer {
public:
    NoiseAnalysis analyze(const float *samples, int32_t frameCount);

private:
    std::array<float, 24> smoothedMel_{};
    float smoothedDbfs_ = -100.0f;
    bool hasSmoothed_ = false;
};

}  // namespace noise
