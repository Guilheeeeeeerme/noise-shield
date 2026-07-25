#pragma once

#include <cstdint>
#include <string>

namespace noise {

enum class LevelBucket : int32_t {
    Low = 0,
    Medium = 1,
    High = 2,
};

enum class BroadProfile : int32_t {
    Fan = 0,
    Traffic = 1,
    Cafe = 2,
    Rain = 3,
    AirConditioner = 4,
    WhiteNoise = 5,
    Unknown = 6,
};

struct NoiseEstimate {
    LevelBucket levelBucket = LevelBucket::Low;
    float rmsDb = -100.0f;
    BroadProfile broadProfile = BroadProfile::Unknown;
    float confidence = 0.0f;
};

class HeuristicAnalyzer {
public:
    NoiseEstimate analyze(const float *samples, int32_t frameCount) const;

private:
    static float computeRms(const float *samples, int32_t frameCount);
    static float rmsToDb(float rms);
    static LevelBucket bucketize(float rmsDb);
    static void extractBandRatios(
            const float *samples,
            int32_t frameCount,
            float &low,
            float &mid,
            float &high);
};

const char *profileToString(BroadProfile profile);
const char *levelToString(LevelBucket level);

}  // namespace noise
