package com.bbioon.plantdisease.data.remote

import com.bbioon.plantdisease.data.model.AnalysisResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses Google Generative Language API (`generateContent`) responses into [AnalysisResult].
 *
 * Handles the post-update Gemma behaviour where responses contain multiple `parts`:
 * "thinking" parts (`"thought": true`) carrying reasoning — which may themselves contain JSON
 * drafts and ```` ``` ```` fences — followed by the actual answer part. We deserialize properly
 * (no regex over nested/escaped JSON), drop thought parts, and join the remaining answer text.
 */
object GeminiResponseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Serializable
    private data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())

    @Serializable
    private data class Candidate(val content: Content? = null, val finishReason: String? = null)

    @Serializable
    private data class Content(val parts: List<Part> = emptyList())

    @Serializable
    private data class Part(val text: String? = null, val thought: Boolean? = null)

    /** Returns the model's answer text, excluding "thinking" parts. */
    fun extractAnswerText(responseBody: String): String? {
        val parsed = try {
            json.decodeFromString<GenerateContentResponse>(responseBody)
        } catch (_: Exception) {
            return null
        }

        val parts = parsed.candidates.firstOrNull()?.content?.parts.orEmpty()
        if (parts.isEmpty()) return null

        // Prefer the actual answer: non-thought text parts, joined (an answer can span parts).
        val answer = parts
            .filter { it.thought != true }
            .mapNotNull { it.text }
            .joinToString("")
        if (answer.isNotBlank()) return answer

        // Fallback: if everything was (mis)tagged as thought, use all text we have.
        val all = parts.mapNotNull { it.text }.joinToString("")
        return all.ifBlank { null }
    }

    /** Parses the answer text (raw JSON or ```json``` fenced, possibly with preamble) into [AnalysisResult]. */
    fun parseAnalysis(text: String): AnalysisResult? {
        // 1. Direct parse
        try {
            return json.decodeFromString<AnalysisResult>(text.trim())
        } catch (_: Exception) {
        }

        // 2. Markdown code block
        Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?\s*```""").find(text)?.groupValues?.get(1)?.let { block ->
            try {
                return json.decodeFromString<AnalysisResult>(block.trim())
            } catch (_: Exception) {
            }
        }

        // 3. Raw object extraction (first { ... last })
        Regex("""\{[\s\S]*\}""").find(text)?.value?.let { obj ->
            try {
                return json.decodeFromString<AnalysisResult>(obj)
            } catch (_: Exception) {
            }
        }

        return null
    }
}
