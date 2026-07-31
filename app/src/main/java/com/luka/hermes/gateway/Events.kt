package com.luka.hermes.gateway

import kotlinx.serialization.json.*

/**
 * Union type for all events that can arrive over the Hermes WebSocket gateway.
 *
 * Every subclass carries the raw [payload] as a [JsonObject?] so callers can
 * reach any field the server sends, even ones that aren't modelled here.
 *
 * Commonly-used fields are exposed as computed properties on the relevant
 * subclass (e.g. [MessageDelta.text]) — everything else lives inside [payload].
 */
sealed class GatewayEvent(
    open val type: String,
    open val sessionId: String?,
    open val payload: JsonObject?,
)

// ── Lifecycle ───────────────────────────────────────────────────────────────

data class GatewayReady(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("gateway.ready", sessionId, payload)

data class SkinChanged(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("skin.changed", sessionId, payload)

// ── Session ─────────────────────────────────────────────────────────────────

data class SessionInfoEvent(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("session.info", sessionId, payload) {
    val infoSessionId: String?
        get() = payload?.get("session_id")?.jsonPrimitive?.contentOrNull
}

// ── Message events ──────────────────────────────────────────────────────────

data class MessageStart(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("message.start", sessionId, payload) {
    val messageId: String?
        get() = payload?.get("message_id")?.jsonPrimitive?.contentOrNull
}

data class MessageDelta(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("message.delta", sessionId, payload) {
    val text: String?
        get() = payload?.get("text")?.jsonPrimitive?.contentOrNull
}

data class MessageInterim(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("message.interim", sessionId, payload) {
    val text: String?
        get() = payload?.get("text")?.jsonPrimitive?.contentOrNull
}

data class MessageComplete(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("message.complete", sessionId, payload) {
    val messageId: String?
        get() = payload?.get("message_id")?.jsonPrimitive?.contentOrNull
}

// ── Thinking / Reasoning ────────────────────────────────────────────────────

data class ThinkingDelta(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("thinking.delta", sessionId, payload) {
    val text: String?
        get() = payload?.get("text")?.jsonPrimitive?.contentOrNull
}

data class ReasoningDelta(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("reasoning.delta", sessionId, payload) {
    val text: String?
        get() = payload?.get("text")?.jsonPrimitive?.contentOrNull
}

data class ReasoningAvailable(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("reasoning.available", sessionId, payload)

// ── Status ──────────────────────────────────────────────────────────────────

data class StatusUpdate(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("status.update", sessionId, payload) {
    val status: String?
        get() = payload?.get("status")?.jsonPrimitive?.contentOrNull
}

// ── Tool events ─────────────────────────────────────────────────────────────

data class ToolStart(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("tool.start", sessionId, payload) {
    val toolName: String?
        get() = payload?.get("tool")?.jsonPrimitive?.contentOrNull
    val toolId: String?
        get() = payload?.get("id")?.jsonPrimitive?.contentOrNull
}

data class ToolProgress(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("tool.progress", sessionId, payload) {
    val toolName: String?
        get() = payload?.get("tool")?.jsonPrimitive?.contentOrNull
    val toolId: String?
        get() = payload?.get("id")?.jsonPrimitive?.contentOrNull
}

data class ToolComplete(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("tool.complete", sessionId, payload) {
    val toolName: String?
        get() = payload?.get("tool")?.jsonPrimitive?.contentOrNull
    val toolId: String?
        get() = payload?.get("id")?.jsonPrimitive?.contentOrNull
}

data class ToolGenerating(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("tool.generating", sessionId, payload) {
    val toolName: String?
        get() = payload?.get("tool")?.jsonPrimitive?.contentOrNull
}

// ── Interaction requests ────────────────────────────────────────────────────

data class ClarifyRequest(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("clarify.request", sessionId, payload) {
    val requestId: String?
        get() = payload?.get("id")?.jsonPrimitive?.contentOrNull
    val question: String?
        get() = payload?.get("question")?.jsonPrimitive?.contentOrNull
}

data class ApprovalRequest(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("approval.request", sessionId, payload) {
    val requestId: String?
        get() = payload?.get("id")?.jsonPrimitive?.contentOrNull
    val title: String?
        get() = payload?.get("title")?.jsonPrimitive?.contentOrNull
    val description: String?
        get() = payload?.get("description")?.jsonPrimitive?.contentOrNull
}

data class SudoRequest(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("sudo.request", sessionId, payload) {
    val requestId: String?
        get() = payload?.get("id")?.jsonPrimitive?.contentOrNull
    val command: String?
        get() = payload?.get("command")?.jsonPrimitive?.contentOrNull
}

data class SecretRequest(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("secret.request", sessionId, payload) {
    val requestId: String?
        get() = payload?.get("id")?.jsonPrimitive?.contentOrNull
    val prompt: String?
        get() = payload?.get("prompt")?.jsonPrimitive?.contentOrNull
}

data class TerminalReadRequest(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("terminal.read.request", sessionId, payload) {
    val requestId: String?
        get() = payload?.get("request_id")?.jsonPrimitive?.contentOrNull
    val start: Int?
        get() = payload?.get("start")?.jsonPrimitive?.intOrNull
    val count: Int?
        get() = payload?.get("count")?.jsonPrimitive?.intOrNull
}

// ── Terminal events ─────────────────────────────────────────────────────────

data class BackgroundComplete(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("background.complete", sessionId, payload)

data class EventError(
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent("error", sessionId, payload) {
    val errorMessage: String?
        get() = payload?.get("message")?.jsonPrimitive?.contentOrNull
    val errorCode: Int?
        get() = (payload?.get("code")?.jsonPrimitive)?.intOrNull
}

// ── Catch-all for future / unknown event types ──────────────────────────────

data class UnknownGatewayEvent(
    override val type: String,
    override val sessionId: String?,
    override val payload: JsonObject?,
) : GatewayEvent(type, sessionId, payload)

// ── Factory ─────────────────────────────────────────────────────────────────

/**
 * Parse the `params` object of a JSON-RPC event frame into a typed
 * [GatewayEvent].
 *
 * The input [params] must be the whole `params` value from the frame:
 * ```json
 * {"type":"message.delta","session_id":"...","payload":{"text":"hello"}}
 * ```
 */
fun parseGatewayEvent(params: JsonObject): GatewayEvent {
    val type = params["type"]?.jsonPrimitive?.contentOrNull
        ?: return UnknownGatewayEvent(
            type = "unknown",
            sessionId = params["session_id"]?.jsonPrimitive?.contentOrNull,
            payload = params["payload"]?.jsonObject,
        )

    val sessionId = params["session_id"]?.jsonPrimitive?.contentOrNull
    val payload = params["payload"]?.jsonObject

    return when (type) {
        "gateway.ready" -> GatewayReady(sessionId, payload)
        "skin.changed" -> SkinChanged(sessionId, payload)

        "session.info" -> SessionInfoEvent(sessionId, payload)

        "message.start" -> MessageStart(sessionId, payload)
        "message.delta" -> MessageDelta(sessionId, payload)
        "message.interim" -> MessageInterim(sessionId, payload)
        "message.complete" -> MessageComplete(sessionId, payload)

        "thinking.delta" -> ThinkingDelta(sessionId, payload)
        "reasoning.delta" -> ReasoningDelta(sessionId, payload)
        "reasoning.available" -> ReasoningAvailable(sessionId, payload)

        "status.update" -> StatusUpdate(sessionId, payload)

        "tool.start" -> ToolStart(sessionId, payload)
        "tool.progress" -> ToolProgress(sessionId, payload)
        "tool.complete" -> ToolComplete(sessionId, payload)
        "tool.generating" -> ToolGenerating(sessionId, payload)

        "clarify.request" -> ClarifyRequest(sessionId, payload)
        "approval.request" -> ApprovalRequest(sessionId, payload)
        "sudo.request" -> SudoRequest(sessionId, payload)
        "secret.request" -> SecretRequest(sessionId, payload)
        "terminal.read.request" -> TerminalReadRequest(sessionId, payload)

        "background.complete" -> BackgroundComplete(sessionId, payload)
        "error" -> EventError(sessionId, payload)

        else -> UnknownGatewayEvent(type, sessionId, payload)
    }
}
