package com.bbioon.plantdisease.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests built from REAL gemma-4-31b-it / gemini responses captured from the live API.
 * The shapes here are exactly what broke the old regex parser after Google's Gemma update.
 */
class GeminiResponseParserTest {

    // Real gemma-4-31b-it (thinking on): reasoning parts (one even contains a JSON DRAFT and a
    // stray "```"), followed by the actual markdown-fenced answer part.
    private val gemmaThinkingResponse = """
        {"candidates":[{"content":{"parts":[
        {"text":"The user wants JSON. My draft:\n```json\n{\"plant_name\":\"DRAFTPLANT\"}\n```","thought":true},
        {"text":"```","thought":true},
        {"text":"```json\n{\n  \"plant_name\": \"Strawberry\",\n  \"is_healthy\": false,\n  \"disease_name\": \"Grey Mold\"\n}\n```"}
        ],"role":"model"},"finishReason":"STOP","index":0}],"usageMetadata":{"thoughtsTokenCount":267}}
    """.trimIndent()

    // gemma-4-31b-it with thinkingLevel=MINIMAL: an EMPTY thought part, then the answer.
    private val minimalResponse = """
        {"candidates":[{"content":{"parts":[
        {"text":"","thought":true},
        {"text":"```json\n{\n  \"plant_name\": \"Mint\",\n  \"is_healthy\": true\n}\n```"}
        ]}}]}
    """.trimIndent()

    // Answer split across TWO non-thought parts. The old code took only the LAST part -> broke.
    private val splitAnswerResponse = """
        {"candidates":[{"content":{"parts":[
        {"text":"some reasoning","thought":true},
        {"text":"{\"plant_name\": \"Tomato\","},
        {"text":"\"is_healthy\": true}"}
        ]}}]}
    """.trimIndent()

    // A Gemini model with native JSON mode (response_mime_type): clean, single, un-fenced part.
    private val geminiCleanResponse = """
        {"candidates":[{"content":{"parts":[{"text":"{\"plant_name\":\"Basil\",\"is_healthy\":true,\"description\":\"Looks fine\"}"}],"role":"model"}}]}
    """.trimIndent()

    @Test
    fun `extractAnswerText skips thought parts and returns the real answer`() {
        val text = GeminiResponseParser.extractAnswerText(gemmaThinkingResponse)
        assertNotNull(text)
        // Must be the real answer, NOT the reasoning's JSON draft.
        assertTrue("answer should contain Strawberry", text!!.contains("Strawberry"))
        assertFalse("answer must not leak the reasoning draft", text.contains("DRAFTPLANT"))
    }

    @Test
    fun `extractAnswerText concatenates an answer split across multiple parts`() {
        val text = GeminiResponseParser.extractAnswerText(splitAnswerResponse)
        assertEquals("""{"plant_name": "Tomato","is_healthy": true}""", text)
    }

    @Test
    fun `extractAnswerText handles MINIMAL empty-thought response`() {
        val text = GeminiResponseParser.extractAnswerText(minimalResponse)
        assertNotNull(text)
        assertTrue(text!!.contains("Mint"))
    }

    @Test
    fun `parseAnalysis reads markdown-fenced gemma answer`() {
        val text = GeminiResponseParser.extractAnswerText(gemmaThinkingResponse)!!
        val result = GeminiResponseParser.parseAnalysis(text)
        assertNotNull(result)
        assertEquals("Strawberry", result!!.plantName)
        assertFalse(result.isHealthy)
        assertEquals("Grey Mold", result.diseaseName)
    }

    @Test
    fun `parseAnalysis reads clean gemini json`() {
        val text = GeminiResponseParser.extractAnswerText(geminiCleanResponse)!!
        val result = GeminiResponseParser.parseAnalysis(text)
        assertNotNull(result)
        assertEquals("Basil", result!!.plantName)
        assertTrue(result.isHealthy)
    }

    @Test
    fun `full path on real split answer yields valid result`() {
        val text = GeminiResponseParser.extractAnswerText(splitAnswerResponse)!!
        val result = GeminiResponseParser.parseAnalysis(text)
        assertNotNull(result)
        assertEquals("Tomato", result!!.plantName)
        assertTrue(result.isHealthy)
    }
}
