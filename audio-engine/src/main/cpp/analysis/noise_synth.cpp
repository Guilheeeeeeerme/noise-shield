#include "analysis/noise_synth.h"

#include <algorithm>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace noise {

std::vector<float> synthesizeSound(SoundId id, int32_t sampleRate, int32_t seconds) {
    const int32_t n = sampleRate * seconds;
    std::vector<float> out(static_cast<size_t>(n), 0.0f);
    uint32_t rng = 0xC0FFEEu + static_cast<uint32_t>(id) * 97u;

    float brown = 0.0f;
    float pink[7] = {0};
    float lfoPhase = 0.0f;

    for (int32_t i = 0; i < n; ++i) {
        float white = nextUniform(rng);
        float sample = 0.0f;
        const float t = static_cast<float>(i) / static_cast<float>(sampleRate);

        switch (id) {
            case SoundId::WhiteNoise:
                sample = white * 0.35f;
                break;
            case SoundId::PinkNoise: {
                pink[0] = 0.99886f * pink[0] + white * 0.0555179f;
                pink[1] = 0.99332f * pink[1] + white * 0.0750759f;
                pink[2] = 0.96900f * pink[2] + white * 0.1538520f;
                pink[3] = 0.86650f * pink[3] + white * 0.3104856f;
                pink[4] = 0.55000f * pink[4] + white * 0.5329522f;
                pink[5] = -0.7616f * pink[5] - white * 0.0168980f;
                sample = (pink[0] + pink[1] + pink[2] + pink[3] + pink[4] + pink[5] + pink[6] +
                          white * 0.5362f) *
                         0.11f;
                pink[6] = white * 0.115926f;
                break;
            }
            case SoundId::BrownNoise:
                brown += white * 0.02f;
                brown = std::clamp(brown, -1.0f, 1.0f);
                sample = brown * 0.4f;
                break;
            case SoundId::OceanWaves: {
                lfoPhase += 2.0f * static_cast<float>(M_PI) * 0.08f / sampleRate;
                float envelope = 0.55f + 0.45f * std::sin(lfoPhase);
                brown += white * 0.015f;
                brown = std::clamp(brown, -1.0f, 1.0f);
                sample = brown * 0.35f * envelope;
                break;
            }
            case SoundId::Rain: {
                float drip = (white > 0.92f) ? white * 0.5f : white * 0.08f;
                sample = drip * 0.45f;
                break;
            }
            case SoundId::Fan: {
                float hum = std::sin(2.0f * static_cast<float>(M_PI) * 120.0f * t);
                float blade = std::sin(2.0f * static_cast<float>(M_PI) * 22.0f * t);
                sample = (hum * 0.25f + blade * 0.15f + white * 0.12f) * 0.5f;
                break;
            }
            case SoundId::AirConditioner: {
                float hum = std::sin(2.0f * static_cast<float>(M_PI) * 60.0f * t);
                brown += white * 0.01f;
                brown = std::clamp(brown, -1.0f, 1.0f);
                sample = (hum * 0.2f + brown * 0.3f + white * 0.05f) * 0.5f;
                break;
            }
            case SoundId::CafeAmbience: {
                float murmur = std::sin(2.0f * static_cast<float>(M_PI) * (180.0f + 40.0f * white) * t);
                sample = (murmur * 0.12f + white * 0.18f) * 0.45f;
                break;
            }
            default:
                sample = white * 0.3f;
                break;
        }

        out[static_cast<size_t>(i)] = std::clamp(sample, -1.0f, 1.0f);
    }

    // Equalize loop ends to reduce click on wrap.
    if (n > 256) {
        for (int32_t i = 0; i < 128; ++i) {
            float w = static_cast<float>(i) / 128.0f;
            float a = out[static_cast<size_t>(i)];
            float b = out[static_cast<size_t>(n - 128 + i)];
            float mixed = a * w + b * (1.0f - w);
            out[static_cast<size_t>(i)] = mixed;
            out[static_cast<size_t>(n - 128 + i)] = mixed;
        }
    }

    return out;
}

}  // namespace noise
