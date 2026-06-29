package com.bbioon.plantdisease.data.remote

import com.bbioon.plantdisease.data.model.AnalysisResult
import com.bbioon.plantdisease.data.model.ApiError
import com.bbioon.plantdisease.data.model.ModelInfo
import com.bbioon.plantdisease.data.model.ModelsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        private const val MAX_ATTEMPTS = 3
    }

    suspend fun analyzeImage(
        base64Image: String,
        language: String,
        model: String,
        apiKey: String,
    ): AnalysisResult = withContext(Dispatchers.IO) {
        val url = "$API_BASE/models/$model:generateContent?key=$apiKey"
        val isGemma = model.lowercase().startsWith("gemma")

        // Gemini supports native JSON output; Gemma does not, but it thinks by default — and only
        // thinkingLevel:"MINIMAL" reliably stops it (includeThoughts/thinkingBudget are ignored or
        // rejected). Minimizing thinking keeps the answer from being truncated and saves tokens.
        val generationConfig = if (isGemma)
            ""","generationConfig":{"thinkingConfig":{"thinkingLevel":"MINIMAL"}}"""
        else
            ""","generationConfig":{"response_mime_type":"application/json"}"""

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

        fun buildBody(genConfig: String) = """
            {"contents":[{"parts":[
                {"text":"${prompt.replace("\"", "\\\"")}"},
                {"inline_data":{"mime_type":"image/jpeg","data":"$base64Image"}}
            ]}]$genConfig}
        """.trimIndent()

        val responseBody = try {
            postForBody(url, buildBody(generationConfig))
        } catch (e: Exception) {
            // Some Gemma variants reject thinkingConfig; retry once without it (parser tolerates thinking).
            if (isGemma && e.message?.contains("thinking", ignoreCase = true) == true) {
                postForBody(url, buildBody(""))
            } else throw e
        }

        val rawText = GeminiResponseParser.extractAnswerText(responseBody)
            ?: throw Exception("EMPTY_RESPONSE")
        val result = GeminiResponseParser.parseAnalysis(rawText) ?: throw Exception("PARSE_ERROR")

        result.copy(
            plantName = result.plantName.ifBlank { "Unknown" },
            plantType = result.plantType.ifBlank { "Unknown" },
        )
    }

    /**
     * POSTs [body] and returns the response body string. Retries transient 5xx errors (the API
     * intermittently returns HTTP 500 "Internal error" for Gemma) with linear backoff. Maps
     * terminal errors to the sentinel messages the UI knows about.
     */
    private suspend fun postForBody(url: String, body: String): String {
        var attempt = 0
        while (true) {
            attempt++
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            val code = response.code

            if (response.isSuccessful) {
                return response.body?.string() ?: throw Exception("EMPTY_RESPONSE")
            }

            val errorBody = response.body?.string() ?: ""
            response.close()

            when (code) {
                429 -> throw Exception("RATE_LIMIT")
                401, 403 -> throw Exception("INVALID_API_KEY")
            }

            // Transient server-side errors: back off and retry.
            if (code in 500..599 && attempt < MAX_ATTEMPTS) {
                delay(1000L * attempt)
                continue
            }

            val errorMsg = try {
                json.decodeFromString<ApiError>(errorBody).error?.message
            } catch (_: Exception) { null }
            throw Exception(errorMsg ?: "API error ($code)")
        }
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
}
