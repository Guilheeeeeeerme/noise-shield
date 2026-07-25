package com.noiseshield.app.data

enum class MaskingSoundId(val index: Int, val assetFile: String) {
    WHITE_NOISE(0, "white_noise.mp3"),
    PINK_NOISE(1, "pink_noise.mp3"),
    BROWN_NOISE(2, "brown_noise.mp3"),
    OCEAN_WAVES(3, "ocean_waves.mp3"),
    RAIN(4, "rain.mp3"),
    FAN(5, "fan.mp3"),
    AIR_CONDITIONER(6, "air_conditioner.mp3"),
    CAFE_AMBIENCE(7, "cafe_ambience.mp3");

    companion object {
        fun fromIndex(index: Int): MaskingSoundId =
            entries.firstOrNull { it.index == index } ?: WHITE_NOISE
    }
}

enum class BroadProfile {
    FAN,
    TRAFFIC,
    CAFE,
    RAIN,
    AIR_CONDITIONER,
    WHITE_NOISE,
    UNKNOWN;

    companion object {
        fun fromOrdinal(value: Int): BroadProfile =
            entries.getOrElse(value) { UNKNOWN }
    }
}

enum class NoiseLevelBucket {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromOrdinal(value: Int): NoiseLevelBucket =
            entries.getOrElse(value) { LOW }
    }
}

data class NoiseEstimate(
    val levelBucket: NoiseLevelBucket,
    val rmsDb: Float,
    val broadProfile: BroadProfile,
    val confidence: Float,
)

val PROFILE_TO_SOUND = mapOf(
    BroadProfile.FAN to MaskingSoundId.FAN,
    BroadProfile.TRAFFIC to MaskingSoundId.BROWN_NOISE,
    BroadProfile.CAFE to MaskingSoundId.CAFE_AMBIENCE,
    BroadProfile.RAIN to MaskingSoundId.RAIN,
    BroadProfile.AIR_CONDITIONER to MaskingSoundId.AIR_CONDITIONER,
    BroadProfile.WHITE_NOISE to MaskingSoundId.WHITE_NOISE,
    BroadProfile.UNKNOWN to MaskingSoundId.WHITE_NOISE,
)

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String) {
    ENGLISH("en"),
    PORTUGUESE("pt");

    companion object {
        fun fromTag(tag: String): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}
