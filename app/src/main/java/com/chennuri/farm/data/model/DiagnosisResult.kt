package com.chennuri.farm.data.model

import org.json.JSONObject

/**
 * Parsed response from Gemini Vision structured JSON output.
 */
data class DiagnosisResult(
    val crop: String,
    val disease: String,
    val confidence: Float,
    val advisoryEnglish: String,
    val advisoryTelugu: String
) {
    val isHighConfidence: Boolean
        get() = confidence >= 0.6f

    companion object {
        /**
         * Parses the raw JSON text Gemini returns (after stripping any
         * markdown fences) into a DiagnosisResult.
         * Throws JSONException / NumberFormatException on malformed input —
         * callers should catch and map to an Error state.
         */
        fun fromJson(raw: String): DiagnosisResult {
            val cleaned = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = JSONObject(cleaned)
            return DiagnosisResult(
                crop = obj.getString("crop"),
                disease = obj.getString("disease"),
                confidence = obj.getDouble("confidence").toFloat(),
                advisoryEnglish = obj.getString("advisoryEnglish"),
                advisoryTelugu = obj.getString("advisoryTelugu")
            )
        }
    }
}