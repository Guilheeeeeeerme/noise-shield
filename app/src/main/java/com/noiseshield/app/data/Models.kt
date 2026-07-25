package com.noiseshield.app.data

enum class MaskingSoundId(val index: Int, val assetFile: String? = null) {
    WHITE_NOISE(0),
    PINK_NOISE(1),
    BROWN_NOISE(2),
    OCEAN_WAVES(3, "ocean_waves.ogg"),
    RAIN(4, "rain.ogg"),
    FAN(5, "fan.ogg"),
    AIR_CONDITIONER(6, "air_conditioner.ogg"),
    CAFE_AMBIENCE(7, "cafe_ambience.ogg");

    companion object {
        fun fromIndex(index: Int): MaskingSoundId =
            entries.firstOrNull { it.index == index } ?: WHITE_NOISE
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

data class NoiseAnalysis(
    val relativeDbfs: Float,
    val levelBucket: NoiseLevelBucket,
    val suggestedSoundId: MaskingSoundId,
    val confidence: Float,
    val melBandEnergies: List<Float>,
    val capturedAtElapsedRealtime: Long,
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
