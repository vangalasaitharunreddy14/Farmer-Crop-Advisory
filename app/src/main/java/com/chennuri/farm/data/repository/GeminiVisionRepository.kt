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
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

) {
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-3.8-flash:generateContent"

    suspend fun diagnose(
        leafBitmap: Bitmap,
        context: ContextPayload
    ): Result<DiagnosisResult> = withContext(Dispatchers.IO) {

        try {
            val totalStart = System.currentTimeMillis()

            val imageStart = System.currentTimeMillis()
            val base64Image = bitmapToBase64(leafBitmap)
            android.util.Log.d(
                "GeminiDiagnosis",
                "Image compression: ${System.currentTimeMillis() - imageStart} ms, " +
                        "Base64 size: ${base64Image.length / 1024} KB"
            )

            val bodyStart = System.currentTimeMillis()
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

            android.util.Log.d(
                "GeminiDiagnosis",
                "Request preparation: ${System.currentTimeMillis() - bodyStart} ms"
            )

            var lastError: Exception? = null

            repeat(4) { attempt ->
                try {
                    android.util.Log.d("GeminiDiagnosis", "Sending request to Gemini")
                    val requestStart = System.currentTimeMillis()

                    val responseBody = client.newCall(request).execute().use { response ->
                        android.util.Log.d(
                            "GeminiDiagnosis",
                            "Gemini HTTP ${response.code}, response time: " +
                                    "${System.currentTimeMillis() - requestStart} ms"
                        )

                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string().orEmpty()
                            throw IOException(
                                "Gemini request failed: HTTP ${response.code} $errorBody"
                            )
                        }

                        response.body?.string()
                            ?: throw IOException("Empty response from Gemini")
                    }

                    val parseStart = System.currentTimeMillis()
                    val result = DiagnosisResult.fromJson(
                        extractTextFromResponse(responseBody)
                    )

                    android.util.Log.d(
                        "GeminiDiagnosis",
                        "JSON parsing: ${System.currentTimeMillis() - parseStart} ms"
                    )
                    android.util.Log.d(
                        "GeminiDiagnosis",
                        "Total repository time: ${System.currentTimeMillis() - totalStart} ms"
                    )

                    return@withContext Result.success(result)

                } catch (error: IOException) {
                    lastError = error

                    val isTemporaryFailure =
                        error.message?.contains("HTTP 503") == true ||
                                error.message?.contains("HTTP 429") == true

                    if (!isTemporaryFailure || attempt == 3) {
                        android.util.Log.e(
                            "GeminiDiagnosis",
                            "Request failed on attempt ${attempt + 1}",
                            error
                        )
                        return@withContext Result.failure(error)
                    }

                    val retryDelayMs =
                        (5000L * (1 shl attempt)) + Random.nextLong(0, 1000)

                    android.util.Log.w(
                        "GeminiDiagnosis",
                        "Retrying after ${retryDelayMs} ms"
                    )
                    delay(retryDelayMs)

                } catch (error: Exception) {
                    return@withContext Result.failure(error)
                }
            }

            return@withContext Result.failure(
                lastError ?: IOException("Gemini request failed")
            )
        } catch (error: Exception) {
            return@withContext Result.failure(error)
        }

    }



    private fun bitmapToBase64(bitmap: Bitmap): String {
        val maxSize = 1024
        val scale = minOf(
            1f,
            maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        )

        val resizedBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(
            Bitmap.CompressFormat.JPEG,
            75,
            outputStream
        )

        val imageBytes = outputStream.toByteArray()

        return Base64.encodeToString(
            imageBytes,
            Base64.NO_WRAP
        )
    }



    private fun buildPrompt(context: ContextPayload): String = """
    You are an agricultural plant-pathology assistant for Indian farmers.
    Analyze the attached crop-leaf image carefully.

    Field context:
    - Temperature: ${context.temperatureCelsius} °C
    - Rain probability: ${context.rainProbabilityNext24hPercent}%
    - Days after sowing: ${context.daysAfterSowing}
    - Crop stage: ${context.cropStage.label}

    Identify the crop and any visible disease or nutrient deficiency.

    LANGUAGE RULES:
    - The "crop" value must be in Telugu only.
    - The "disease" value must be in Telugu only.
    - Do not include English disease names in parentheses.
    - The "advisoryTelugu" value must be natural, simple Telugu.
    - The "advisoryEnglish" value must be in English.
    - If the plant appears healthy, state that in Telugu in the "disease" field.
    - Do not invent a disease if the image is unclear.
    - Keep "confidence" as a number between 0.0 and 1.0.

    Reply with only valid JSON using this exact structure:
    {
      "crop": "తెలుగు పంట పేరు",
      "disease": "తెలుగులో వ్యాధి పేరు",
      "confidence": 0.0,
      "advisoryEnglish": "Concise actionable advice in English",
      "advisoryTelugu": "సరళమైన తెలుగులో సూచనలు"
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