package com.example.api

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Chat Data Models ---

enum class ChatSender { USER, AI }

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList()
)

// --- Moshi Compatible Models ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Base64 encoded bytes
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): GenerateContentResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiService {
    private const val SYSTEM_PROMPT = """You are the core intelligence of "Alter Space," a minimalist productivity scratchpad. Your sole purpose is to ingest raw, messy audio transcriptions, chaotic text, images, or PDFs and output highly polished, structured data.
RULES:
1. STRICT ANTI-HALLUCINATION & SILENCE DETECTION:
   - For audio recordings: If the audio contains silence, ambient room tone, background static, breathing, unintelligible mumbles, or no clear human speech, you MUST output EXACTLY and ONLY: `[NO_SPEECH_DETECTED]`.
   - Never hallucinate, invent words, fabricate tasks, or produce YouTube subtitles (e.g. "Thank you for watching", "Please subscribe", "Subtitles by") when no speech exists.
   - For text or images: Never invent facts or items not present in the input.
2. FILTER SLOP: Remove all stutters, filler words ("um", "like", "uh"), false starts, and conversational dead-ends.
3. TRANSLATE: If the user issues a translation command, return the final output in that target language.
4. STRUCTURE: Output clean Markdown. Use bolding for key terms.
5. CHECKBOX CONTRACT: Format all actionable to-do items, reminders, errands, and tasks using markdown checkboxes (`- [ ] <task>`) on their own separate line so they can be tracked seamlessly in the Upcoming task lists. For non-actionable context, facts, or descriptions, use standard bullet points (`- <item>`).
CRITICAL: Output ONLY the processed text. Never include conversational prefixes like "Here is your text" or "I have cleaned this up for you." """

    private val CANDIDATE_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-flash-lite-preview",
        "gemini-3.1-pro-preview"
    )

    private fun getApiKey(): String {
        val buildConfigKey = com.example.BuildConfig.GEMINI_API_KEY
        if (!buildConfigKey.isNullOrBlank() && buildConfigKey != "MY_GEMINI_API_KEY" && buildConfigKey != "GEMINI_API_KEY") {
            return buildConfigKey
        }
        return "AQ.Ab8RN6KqYE73frbmUl47DDYEJORNGiDeFCdMm6qK9hUtFavsAg"
    }

    private suspend fun executeWithFallbackAndRetry(
        request: Map<String, Any>
    ): String {
        val apiKey = getApiKey()
        var lastException: Exception? = null

        for (model in CANDIDATE_MODELS) {
            var delayMs = 1000L
            val maxAttemptsPerModel = 2

            for (attempt in 1..maxAttemptsPerModel) {
                try {
                    val response = GeminiRetrofitClient.service.generateContent(model, apiKey, request)
                    val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!text.isNullOrBlank()) {
                        return text
                    }
                } catch (e: retrofit2.HttpException) {
                    lastException = e
                    val code = e.code()
                    if (code == 429 || code == 503 || code >= 500) {
                        // Rate limited (429) or transient server load (503/5xx) - back off with jitter
                        val jitter = (Math.random() * 500).toLong()
                        kotlinx.coroutines.delay(delayMs + jitter)
                        delayMs = (delayMs * 1.8).toLong()
                    } else if (code == 404 || code == 400) {
                        // Model unavailable or not found - immediately try the next model candidate
                        break
                    } else {
                        // Other non-transient client error
                        val errorBody = e.response()?.errorBody()?.string()
                        return "Network Exception: HTTP $code - $errorBody"
                    }
                } catch (e: java.net.UnknownHostException) {
                    return "Network Exception: No internet connection. Please check your Wi-Fi or mobile data."
                } catch (e: java.io.IOException) {
                    lastException = e
                    val jitter = (Math.random() * 500).toLong()
                    kotlinx.coroutines.delay(delayMs + jitter)
                    delayMs = (delayMs * 1.5).toLong()
                } catch (e: Exception) {
                    lastException = e
                    break
                }
            }
        }

        // If all models failed
        if (lastException is retrofit2.HttpException) {
            if (lastException.code() == 429) {
                return "Gemini AI rate limit reached (HTTP 429). Please wait a moment and try again."
            }
            val errorBody = lastException.response()?.errorBody()?.string()
            return "Network Exception: HTTP ${lastException.code()} - $errorBody"
        } else if (lastException != null) {
            val msg = lastException.message ?: ""
            if (msg.contains("resolve host") || msg.contains("UnknownHost")) {
                return "Network Exception: No internet connection. Please check your Wi-Fi or mobile data."
            }
            return "Network Exception: $msg"
        }

        return "No response received. Please check connection and retry."
    }

    /**
     * Sends raw text to Gemini to apply clean formatting.
     */
    suspend fun processText(userInput: String): String = withContext(Dispatchers.IO) {
        val request = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to "Process this text input raw thoughts:\n\n$userInput")
                    )
                )
            ),
            "generationConfig" to mapOf("temperature" to 0.2f),
            "systemInstruction" to mapOf(
                "parts" to listOf(
                    mapOf("text" to SYSTEM_PROMPT)
                )
            )
        )

        executeWithFallbackAndRetry(request)
    }

    /**
     * Sends voice audio recording to Gemini for transcription, slop filtering, and formatting.
     */
    suspend fun processAudio(audioBase64: String, mimeType: String = "audio/aac"): String = withContext(Dispatchers.IO) {
        val request = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf(
                            "inlineData" to mapOf(
                                "mimeType" to mimeType,
                                "data" to audioBase64
                            )
                        ),
                        mapOf("text" to "Transcribe and process this voice memo audio. If there is no clear human speech or only silence/ambient noise, output exactly '[NO_SPEECH_DETECTED]'. Otherwise, filter filler words and format cleanly into Markdown.")
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.1f,
                "topP" to 0.95f
            ),
            "systemInstruction" to mapOf(
                "parts" to listOf(
                    mapOf("text" to SYSTEM_PROMPT)
                )
            )
        )

        val result = executeWithFallbackAndRetry(request)
        if (result.isBlank()) "No input" else result
    }

    /**
     * Sends static image / screenshot to Gemini for OCR extraction and formatting.
     */
    suspend fun processImage(imageBase64: String, mimeType: String = "image/jpeg", userPrompt: String? = null): String = withContext(Dispatchers.IO) {
        val promptText = userPrompt ?: "Extract text and structured goals from this screenshot image. Filter any noise/ads, structure the core information using clean Markdown, and format it nicely."
        val request = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf(
                            "inlineData" to mapOf(
                                "mimeType" to mimeType,
                                "data" to imageBase64
                            )
                        ),
                        mapOf("text" to promptText)
                    )
                )
            ),
            "generationConfig" to mapOf("temperature" to 0.2f),
            "systemInstruction" to mapOf(
                "parts" to listOf(
                    mapOf("text" to SYSTEM_PROMPT)
                )
            )
        )

        executeWithFallbackAndRetry(request)
    }

    /**
     * Contextual conversational chatbot equipped with full memory and insights from saved notes, memos, and canvas cards.
     */
    suspend fun processChat(
        userMessage: String,
        history: List<ChatMessage>,
        appContext: String
    ): String = withContext(Dispatchers.IO) {
        val systemPrompt = """You are Alter AI, the intelligent conversational assistant embedded inside ALTER space.
You have direct, full insight into all notes, audio transcripts, canvas boards, scanned documents, and tasks saved by the user in this app over time.

USER'S SAVED MEMORIES & IN-APP CONTEXT DATA:
===
$appContext
===

CRITICAL RULES & FORMATTING STYLE:
1. ALWAYS PREFER POINTERS OVER PARAGRAPHS:
   - Present responses primarily using bullet points / pointers (`• ` or `- `) rather than dense paragraphs.
   - Break down information, details, findings, and memory analysis into clear, scannable pointers so the user can quickly scan the information.
   - If a brief headline or intro line is helpful, keep it to 1 short sentence, followed immediately by concise bullet pointers.
2. SCANNABLE FORMATTING:
   - Use standard bullet points (`• ` or `- `) for pointers. Avoid markdown symbols like asterisks (`**` or `*`), hashtags (`#`), or bold headers.
3. IN-APP MEMORY SEARCH & RECALL:
   - Search the context data above when asked about past notes or memories.
   - Refer to and cite cards using `[Card #<id>: <short title>]` alongside or within the pointers. Card citations are encouraged as references.
4. DIRECT & CONCISE:
   - Keep each pointer brief, direct, and focused on key facts. Avoid long paragraphs, verbose fluff, or conversational filler.
5. NO DISCLAIMERS:
   - Never say "As an AI..." or "I don't have access to your device".
"""

        val contentsList = mutableListOf<Map<String, Any>>()

        // Filter previous history (excluding the current user message if already present)
        val priorHistory = if (history.isNotEmpty() && history.last().text == userMessage && history.last().sender == ChatSender.USER) {
            history.dropLast(1)
        } else {
            history
        }

        // Take up to 10 prior turns and ensure valid alternating turns
        val recentPriorTurns = priorHistory.takeLast(10)
        for (msg in recentPriorTurns) {
            if (msg.text.isNotBlank()) {
                val role = if (msg.sender == ChatSender.USER) "user" else "model"
                val lastRole = (contentsList.lastOrNull()?.get("role") as? String)
                if (contentsList.isEmpty()) {
                    if (role == "user") {
                        contentsList.add(mapOf("role" to "user", "parts" to listOf(mapOf("text" to msg.text))))
                    }
                } else if (lastRole != role) {
                    contentsList.add(mapOf("role" to role, "parts" to listOf(mapOf("text" to msg.text))))
                }
            }
        }

        // If the last turn in contentsList is already 'user', remove it so we don't have consecutive user turns
        if (contentsList.isNotEmpty() && contentsList.last()["role"] == "user") {
            contentsList.removeAt(contentsList.size - 1)
        }

        // Add current user prompt as the final user turn
        contentsList.add(
            mapOf(
                "role" to "user",
                "parts" to listOf(mapOf("text" to userMessage))
            )
        )

        val request = mapOf(
            "contents" to contentsList,
            "generationConfig" to mapOf(
                "temperature" to 0.4f,
                "topP" to 0.95f
            ),
            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemPrompt))
            )
        )

        executeWithFallbackAndRetry(request)
    }
}
