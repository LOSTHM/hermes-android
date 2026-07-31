package com.luka.hermes.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ── Domain models ───────────────────────────────────────────────────────────

/**
 * A Hermes conversation session.
 *
 * Only fields confirmed by the protocol are modelled; everything else is
 * accessible from the raw [JsonElement] returned by `request<JsonElement>()`.
 */
@Serializable
data class Session(
    val id: String = "",
    val title: String? = null,
    val preview: String? = null,
    @SerialName("message_count") val messageCount: Int? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val status: String? = null,
)

/**
 * Returned inside the `session.info` event payload.
 */
@Serializable
data class SessionInfo(
    @SerialName("session_id") val sessionId: String = "",
    val title: String? = null,
    val status: String? = null,
)

/**
 * Token usage for a session, as returned by [RpcMethods.SESSION_USAGE].
 *
 * The wire shape is snake_case; the daemon reports both the generic
 * `prompt_tokens`/`completion_tokens`/`total_tokens` keys and the Hermes‑native
 * `prompt`/`completion`/`total` plus context‑window fields.
 */
@Serializable
data class SessionUsage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null,
    @SerialName("context_window") val contextWindow: Int? = null,
    @SerialName("prompt") val prompt: Int? = null,
    @SerialName("completion") val completion: Int? = null,
    @SerialName("total") val total: Int? = null,
    @SerialName("context_used") val contextUsed: Int? = null,
    @SerialName("context_max") val contextMax: Int? = null,
    @SerialName("context_percent") val contextPercent: Int? = null,
    val calls: Int? = null,
    val model: String? = null,
)

/**
 * Configuration block returned by [RpcMethods.CONFIG_GET].
 * The [value] is a raw JSON node whose shape depends on the requested key
 * (e.g. `{"type":"openai","model":"gpt-4",…}` for key `"provider"`).
 */
@Serializable
data class ConfigEntry(
    val key: String = "",
    val value: JsonElement? = null,
)

// ── JSON‑RPC wire types ─────────────────────────────────────────────────────

/**
 * Lightweight JSON-RPC 2.0 error from a response frame.
 */
data class RpcError(
    val code: Int? = null,
    val message: String? = null,
)

// ── Exceptions ──────────────────────────────────────────────────────────────

/** Generic exception thrown by the gateway layer. */
open class GatewayException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** Thrown when an RPC request times out. */
class GatewayTimeoutException(
    method: String,
) : GatewayException("Request timed out: $method")

/** Thrown when an RPC response carries an error. */
class GatewayRpcException(
    message: String,
    val code: Int? = null,
) : GatewayException(message)

/** Thrown when the WebSocket is not in OPEN state. */
class GatewayNotConnectedException(
    message: String = "gateway not connected",
) : GatewayException(message)
