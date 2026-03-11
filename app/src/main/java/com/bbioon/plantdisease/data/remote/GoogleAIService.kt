package com.bbioon.plantdisease.data.remote

import com.bbioon.plantdisease.data.model.AnalysisResult
import com.bbioon.plantdisease.data.model.ApiError
import com.bbioon.plantdisease.data.model.ModelInfo
import com.bbioon.plantdisease.data.model.ModelsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GoogleAIService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    companion object {
        private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta"
    }

    suspend fun analyzeImage(
        base64Image: String,
        language: String,
        model: String,
        apiKey: String,
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val url = "$API_BASE/models/$model:generateContent?key=$apiKey"
        val isGemma = model.lowercase().startsWith("gemma")

        val langInstruction = if (language == "ar")
            "Respond in Arabic language." else "Respond in English language."

        val prompt = """Analyze this image of a plant. Identify the plant name, type, and check for any diseases. $langInstruction Return the result in valid JSON format with keys: 'plant_name', 'plant_type', 'is_healthy' (boolean), 'disease_name' (if any, else null), 'description' (short summary), 'treatment' (treatment recommendations if disease detected, else null)."""

        val generationConfig = if (!isGemma)
            ""","generationConfig":{"response_mime_type":"application/json"}""" else ""

        val body = """
            {"contents":[{"parts":[
                {"text":"$prompt"},
                {"inline_data":{"mime_type":"image/jpeg","data":"$base64Image"}}
            ]}]$generationConfig}
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            val statusCode = response.code
            if (statusCode == 429) throw Exception("RATE_LIMIT")
            if (statusCode == 401 || statusCode == 403) throw Exception("INVALID_API_KEY")
            val errorMsg = try {
                json.decodeFromString<ApiError>(errorBody).error?.message
            } catch (_: Exception) { null }
            throw Exception(errorMsg ?: "API error ($statusCode)")
        }

        val responseBody = response.body?.string() ?: throw Exception("EMPTY_RESPONSE")
        val rawText = extractTextFromResponse(responseBody) ?: throw Exception("EMPTY_RESPONSE")
        val result = extractJsonFromText(rawText) ?: throw Exception("PARSE_ERROR")

        result.copy(
            plantName = result.plantName.ifBlank { "Unknown" },
            plantType = result.plantType.ifBlank { "Unknown" },
        )
    }

    suspend fun fetchModels(apiKey: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val url = "$API_BASE/models?key=$apiKey"
        val request = Request.Builder().url(url).build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            if (response.code == 401 || response.code == 403) throw Exception("INVALID_API_KEY")
            throw Exception("Failed to fetch models (${response.code})")
        }

        val body = response.body?.string() ?: throw Exception("EMPTY_RESPONSE")
        val modelsResponse = json.decodeFromString<ModelsResponse>(body)

        modelsResponse.models.filter { model ->
            val name = model.name.lowercase()
            val methods = model.supportedGenerationMethods
            methods.contains("generateContent") &&
                (name.contains("gemma") || name.contains("gemini"))
        }.map { it.copy(name = it.name.removePrefix("models/")) }
    }

    private fun extractTextFromResponse(responseBody: String): String? {
        // Simple extraction: find "text":"..." in the response
        val regex = """"text"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        val match = regex.find(responseBody)
        return match?.groupValues?.get(1)
            ?.replace("\\n", "\n")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    private fun extractJsonFromText(text: String): AnalysisResult? {
        // 1. Try direct parse
        try { return json.decodeFromString<AnalysisResult>(text.trim()) } catch (_: Exception) {}

        // 2. Try markdown code block
        val codeBlock = Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?\s*```""").find(text)
        codeBlock?.groupValues?.get(1)?.let { block ->
            try { return json.decodeFromString<AnalysisResult>(block.trim()) } catch (_: Exception) {}
        }

        // 3. Try raw object extraction
        val objectMatch = Regex("""\{[\s\S]*\}""").find(text)
        objectMatch?.value?.let { obj ->
            try { return json.decodeFromString<AnalysisResult>(obj) } catch (_: Exception) {}
        }

        return null
    }
}
