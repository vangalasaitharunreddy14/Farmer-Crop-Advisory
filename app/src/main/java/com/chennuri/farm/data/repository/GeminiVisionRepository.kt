package com.chennuri.farm.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.chennuri.farm.data.model.ContextPayload
import com.chennuri.farm.data.model.DiagnosisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class GeminiVisionRepository(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-2.5-flash:generateContent"

    suspend fun diagnose(
        leafBitmap: Bitmap,
        context: ContextPayload
    ): Result<DiagnosisResult> = withContext(Dispatchers.IO) {
        try {
            val base64Image = bitmapToBase64(leafBitmap)

            val requestBodyJson = buildRequestBody(
                prompt = buildPrompt(context),
                base64Image = base64Image
            )

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("x-goog-api-key", apiKey)
                .post(
                    requestBodyJson.toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            var lastError: Exception? = null

            repeat(4) { attempt ->
                try {
                    val responseBody = client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string().orEmpty()

                            throw IOException(
                                "Gemini request failed: HTTP ${response.code} $errorBody"
                            )
                        }

                        response.body?.string()
                            ?: throw IOException("Empty response from Gemini")
                    }

                    return@withContext Result.success(
                        DiagnosisResult.fromJson(
                            extractTextFromResponse(responseBody)
                        )
                    )
                } catch (error: IOException) {
                    lastError = error

                    val isTemporaryFailure =
                        error.message?.contains("HTTP 503") == true ||
                                error.message?.contains("HTTP 429") == true

                    if (!isTemporaryFailure || attempt == 3) {
                        return@withContext Result.failure(error)
                    }

                    val retryDelayMs =
                        (1000L * (1 shl attempt)) + Random.nextLong(0, 500)

                    delay(retryDelayMs)
                } catch (error: Exception) {
                    return@withContext Result.failure(error)
                }
            }

            Result.failure(
                lastError ?: IOException("Gemini diagnosis could not be completed.")
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)

        return Base64.encodeToString(
            output.toByteArray(),
            Base64.NO_WRAP
        )
    }

    private fun buildPrompt(context: ContextPayload): String = """
        You are an agricultural plant-pathology assistant for Indian farmers.
        Analyze the attached crop-leaf image.

        Field context:
        - Temperature: ${context.temperatureCelsius} °C
        - Rain probability: ${context.rainProbabilityNext24hPercent}%
        - Days after sowing: ${context.daysAfterSowing}
        - Crop stage: ${context.cropStage.label}

        Identify the crop and any visible disease or nutrient deficiency.
        Reply with only valid JSON, with this exact structure:

        {
          "crop": "string",
          "disease": "string",
          "confidence": 0.0,
          "advisoryEnglish": "concise actionable advice",
          "advisoryTelugu": "natural Telugu translation of the advice"
        }
    """.trimIndent()

    private fun buildRequestBody(
        prompt: String,
        base64Image: String
    ): JSONObject {
        val textPart = JSONObject().put("text", prompt)

        val imagePart = JSONObject().put(
            "inline_data",
            JSONObject()
                .put("mime_type", "image/jpeg")
                .put("data", base64Image)
        )

        val contents = JSONArray().put(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(textPart).put(imagePart))
        )

        return JSONObject()
            .put("contents", contents)
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("responseMimeType", "application/json")
            )
    }

    private fun extractTextFromResponse(body: String): String {
        val root = JSONObject(body)

        return root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }
}