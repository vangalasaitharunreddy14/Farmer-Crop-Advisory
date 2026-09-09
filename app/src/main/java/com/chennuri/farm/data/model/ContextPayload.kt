package com.chennuri.farm.data.model

/**
 * Aggregated context sent alongside the leaf image to Gemini Vision.
 */
data class ContextPayload(
    val latitude: Double,
    val longitude: Double,
    val temperatureCelsius: Double,
    val rainProbabilityNext24hPercent: Int,
    val weatherDescription: String,
    val sowingDateEpochMillis: Long,
    val daysAfterSowing: Int,
    val cropStage: CropStage
)

enum class CropStage(val label: String) {
    GERMINATION("Germination"),
    SEEDLING("Seedling"),
    VEGETATIVE("Vegetative"),
    FLOWERING("Flowering"),
    MATURITY("Maturity");

    companion object {
        /**
         * Simple generic DAS -> stage bucketing for Phase 1 prototype.
         * Not crop-specific yet; refine per-crop in a later phase.
         */
        fun fromDaysAfterSowing(das: Int): CropStage = when {
            das < 10 -> GERMINATION
            das < 25 -> SEEDLING
            das < 50 -> VEGETATIVE
            das < 75 -> FLOWERING
            else -> MATURITY
        }
    }
}