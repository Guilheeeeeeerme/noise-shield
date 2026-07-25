#pragma once

#include <cmath>
#include <cstdint>
#include <vector>

#include "player/masking_player.h"

namespace noise {

inline uint32_t xorshift32(uint32_t &state) {
    state ^= state << 13;
    state ^= state >> 17;
    state ^= state << 5;
    return state;
}

inline float nextUniform(uint32_t &state) {
    return (xorshift32(state) / static_cast<float>(UINT32_MAX)) * 2.0f - 1.0f;
}

std::vector<float> synthesizeSound(SoundId id, int32_t sampleRate, int32_t seconds = 4);

}  // namespace noise
