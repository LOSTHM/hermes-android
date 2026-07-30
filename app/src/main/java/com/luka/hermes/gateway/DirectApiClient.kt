package com.luka.hermes.gateway

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Lightweight OpenAI-compatible HTTP API client for direct LLM chat.
 *
 * Supports SSE streaming via [sendMessage] with delta callbacks.
 * Keeps a rolling message history within the session.
 */
class DirectApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // streaming: no read timeout
        .connectTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonMediaType = "application/json".toMediaType()

    @Volatile
    private var currentCall: Call? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Single message in the conversation history. */
    data class Message(
        val role: String,   // "user" | "assistant"
        val content: String,
    )

    private val messages = mutableListOf<Message>()

    /**
     * Send a message and stream back tokens.
     *
     * @param text  The user's message text.
     * @param baseUrl  API base URL (e.g. `https://llmapi.tripln.top:5000/v1/`).
     * @param apiKey  API key.
     * @param model  Model ID (e.g. `qwen3.6`).
     * @param temperature  Sampling temperature (0.0–2.0).
     * @param maxTokens  Maximum tokens to generate.
     * @param systemPrompt  Optional system message.
     * @param onDelta  Called for each new token of text.
     * @param onComplete  Called with the full response text when done.
     * @param onError  Called with an error message on failure.
     */
    fun sendMessage(
        text: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        systemPrompt: String = "",
        onDelta: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit,
    ): Job {
        messages.add(Message("user", text))

        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val bodyJson = buildRequestBody(model, temperature, maxTokens, systemPrompt)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        val call = httpClient.newCall(request)
        currentCall = call

        return scope.launch {
            try {
                val response = call.execute()
                try {
                    val source = response.body?.source() ?: run {
                        onError("Empty response body")
                        return@launch
                    }

                    val fullText = StringBuilder()

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ")
                            if (data.trim() == "[DONE]") break
                            try {
                                val json = JSONObject(data)
                                val choices = json.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    val content = delta?.optString("content", "") ?: ""
                                    if (content.isNotEmpty()) {
                                        fullText.append(content)
                                        onDelta(content)
                                    }
                                }
                            } catch (_: Exception) {
                                // Skip malformed JSON lines
                            }
                        }
                    }

                    val full = fullText.toString()
                    messages.add(Message("assistant", full))
                    onComplete(full)
                } finally {
                    response.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
            } finally {
                currentCall = null
            }
        }
    }

    /**
     * Build the JSON request body with the full message history.
     */
    private fun buildRequestBody(model: String, temperature: Float, maxTokens: Int, systemPrompt: String): String {
        val json = JSONObject()
        json.put("model", model)
        json.put("stream", true)
        json.put("temperature", temperature.toDouble())
        json.put("max_tokens", maxTokens)

        val msgsArray = JSONArray()

        // Optional system prompt
        if (systemPrompt.isNotBlank()) {
            val sysObj = JSONObject()
            sysObj.put("role", "system")
            sysObj.put("content", systemPrompt)
            msgsArray.put(sysObj)
        }

        for (msg in messages) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            msgsArray.put(msgObj)
        }
        json.put("messages", msgsArray)

        return json.toString()
    }

    /**
     * Interrupt the current generation (cancels the HTTP call).
     */
    fun interrupt() {
        currentCall?.cancel()
        currentCall = null
    }

    /**
     * Clear the conversation history for a new session.
     * Also cancels any in-flight request.
     */
    fun clearHistory() {
        interrupt()
        messages.clear()
    }

    /**
     * Cancel all pending work and release resources.
     */
    fun shutdown() {
        interrupt()
        scope.cancel()
    }
}
