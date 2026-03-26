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

        val generationConfig = if (!isGemma)
            ""","generationConfig":{"response_mime_type":"application/json"}""" else ""

        val prompt = if (language == "ar") {
            """أنت خبير أمراض نباتات. حلّل صورة النبات وشخّص حالته.

أجب بالعربية. الأسماء العلمية باللاتينية دائماً.
مهم جداً: كن مختصراً. كل حقل نصي جملة أو جملتين فقط.

أعد JSON صالح بالمفاتيح التالية:
- "plant_name": الاسم الشائع بالعربية
- "scientific_name": الاسم العلمي باللاتينية
- "plant_type": التصنيف (مثل: "خضروات"، "شجرة فاكهة")
- "is_healthy": true/false
- "disease_name": اسم المرض بالعربية (null إذا سليم)
- "scientific_disease_name": اسم المرض باللاتينية (null إذا سليم)
- "pathogen_type": "فطر"/"بكتيريا"/"فيروس"/"نيماتودا"/"نقص غذائي"/"آفة" أو null
- "severity": "منخفضة"/"متوسطة"/"شديدة"/"حرجة" (null إذا سليم)
- "disease_stage": مرحلة المرض الحالية، جملة واحدة (null إذا سليم)
- "spread_risk": خطر الانتشار، جملة واحدة (null إذا سليم)
- "symptoms": الأعراض المرئية، جملتان كحد أقصى
- "cause": السبب والعوامل البيئية، جملة أو جملتان
- "description": ملخص مبسط لغير المتخصصين، جملتان
- "treatment": العلاج: ممارسات زراعية + مبيد (مادة فعالة) + بديل حيوي، 3 جمل كحد أقصى
- "prevention": إجراءات وقائية، جملتان كحد أقصى
- "favorable_conditions": ظروف مساعدة (حرارة، رطوبة)، جملة واحدة (null إذا سليم)
- "notes": ملاحظة مهنية أو تشخيص تفريقي، جملة واحدة (null إذا سليم)"""
        } else {
            """You are an expert plant pathologist. Analyze this plant image and diagnose its condition.

Respond in English. IMPORTANT: Keep each text field to 1-2 sentences max. Be concise.

Return VALID JSON with these keys:
- "plant_name": Common name
- "scientific_name": Binomial Latin name
- "plant_type": Category (e.g., "Vegetable", "Fruit tree", "Ornamental")
- "is_healthy": boolean
- "disease_name": Common disease name (null if healthy)
- "scientific_disease_name": Latin disease name (null if healthy)
- "pathogen_type": "Fungus"/"Bacteria"/"Virus"/"Nematode"/"Nutrient deficiency"/"Pest damage" or null
- "severity": "Low"/"Moderate"/"Severe"/"Critical" (null if healthy)
- "disease_stage": Current stage, 1 sentence (null if healthy)
- "spread_risk": Spread risk, 1 sentence (null if healthy)
- "symptoms": Visible symptoms, max 2 sentences
- "cause": Cause + environmental factors, 1-2 sentences
- "description": Simple summary for non-experts, 2 sentences
- "treatment": Cultural + chemical (active ingredient) + biological options, max 3 sentences
- "prevention": Preventive measures, max 2 sentences
- "favorable_conditions": Temperature, humidity conditions, 1 sentence (null if healthy)
- "notes": Differential diagnosis or lab suggestions, 1 sentence (null if healthy)"""
        }

        val body = """
            {"contents":[{"parts":[
                {"text":"${prompt.replace("\"", "\\\"")}"},
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
