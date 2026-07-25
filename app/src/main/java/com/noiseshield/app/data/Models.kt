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

enum class CoverState {
    LISTENING,
    MASKING_EXTERNAL,
    COVERED,
}

enum class MaskingPreset(val switching: Float, val fade: Float) {
    NORMAL(0.5f, 0.5f),
    QUIET(0.15f, 0.15f),
    HOME(0.35f, 0.35f),
    BUSY(0.75f, 0.7f),
    TRAVEL(1f, 0.9f),
}

data class NoiseAnalysis(
    val relativeDbfs: Float,
    val levelBucket: NoiseLevelBucket,
    val suggestedSoundId: MaskingSoundId,
    val confidence: Float,
    val melBandEnergies: List<Float>,
    val capturedAtElapsedRealtime: Long,
    val selfMatch: Float = 0f,
    val residualDbfs: Float = relativeDbfs,
    val coverState: CoverState = CoverState.LISTENING,
)

/** Stable fingerprint for rematching audio devices after reconnect. */
data class AudioDevicePreference(
    val fingerprint: String = FINGERPRINT_AUTO,
    val deviceId: Int = DEVICE_ID_AUTO,
) {
    val isAuto: Boolean get() = fingerprint == FINGERPRINT_AUTO

    companion object {
        const val FINGERPRINT_AUTO = "auto"
        const val DEVICE_ID_AUTO = 0
    }
}

data class AudioRouteDevice(
    val id: Int,
    val fingerprint: String,
    val name: String,
    val type: Int,
    val isBuiltin: Boolean,
    val isBluetooth: Boolean,
)

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    PORTUGUESE("pt-BR"),
    SPANISH("es"),
    CHINESE_SIMPLIFIED("zh-Hans"),
    FRENCH("fr");

    companion object {
        fun fromTag(tag: String): AppLanguage {
            val first = tag.substringBefore(',').trim().lowercase().replace('_', '-')
            if (first.isEmpty()) return SYSTEM
            val primary = first.substringBefore('-')
            val scriptOrRegion = first.substringAfter('-', missingDelimiterValue = "")
            return when {
                primary == "en" -> ENGLISH
                primary == "pt" -> PORTUGUESE
                primary == "es" -> SPANISH
                primary == "fr" -> FRENCH
                primary == "zh" && (
                    scriptOrRegion.startsWith("hans") ||
                        scriptOrRegion == "cn" ||
                        scriptOrRegion.isEmpty()
                    ) -> CHINESE_SIMPLIFIED
                else -> entries.firstOrNull { it.tag.equals(first, ignoreCase = true) } ?: ENGLISH
            }
        }
    }
}
