package com.luka.hermes.gateway

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/**
 * High‑level repository that wraps [GatewayClient] into an API the UI layer
 * consumes naturally.
 *
 * ## Auto‑reconnect
 * After a successful [connect], the repository monitors the connection state
 * and automatically retries with exponential backoff (1 s → 2 s → 4 s … 30 s
 * cap) whenever the WebSocket drops unexpectedly.  Call [disconnect] to stop
 * reconnecting and tear down the connection cleanly.
 *
 * ## Streaming prompts
 * [sendPrompt] returns a cold [Flow] that emits typed [GatewayEvent] objects
 * as they arrive from the gateway and completes when a terminal event
 * ([MessageComplete] or [EventError]) is received.  The RPC call is fired
 * **after** the listener is wired up so no event is missed.
 */
class HermesRepository(
    private val client: GatewayClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var wsUrl: String = ""

    private var reconnectJob: Job? = null

    @Volatile
    private var intentionalClose = false

    // ── Connection ────────────────────────────────────────────────────────

    /** Observable connection state (delegated to the client). */
    val connectionState: StateFlow<ConnectionState> = client.connectionState

    /**
     * Connect to the Hermes daemon.
     *
     * The URL is built automatically as `ws://127.0.0.1:9119/api/ws?token=…`.
     * On success, background reconnect monitoring starts.
     *
     * @throws GatewayException on connection failure.
     */
    suspend fun connect(token: String) {
        wsUrl = "ws://127.0.0.1:9119/api/ws?token=$token"
        intentionalClose = false
        client.connect(wsUrl)
        startReconnectLoop()
    }

    /**
     * Gracefully disconnect and stop reconnecting.
     * Safe to call even if not connected.
     */
    fun disconnect() {
        intentionalClose = true
        reconnectJob?.cancel()
        client.close()
    }

    // ── Session management ────────────────────────────────────────────────

    /**
     * Create a new conversation session.
     */
    suspend fun createSession(title: String): Session {
        val result = client.request(
            RpcMethods.SESSION_CREATE,
            buildJsonObject { put("title", JsonPrimitive(title)) },
        )
        return json.decodeFromJsonElement(result)
    }

    /**
     * List all active sessions.
     *
     * Handles both `[…]` and `{"sessions":[…]}` response shapes.
     */
    suspend fun listSessions(): List<Session> {
        val result = client.request(RpcMethods.SESSION_LIST)
        val array = when (result) {
            is JsonArray -> result
            is JsonObject -> result["sessions"]?.jsonArray
                ?: result["data"]?.jsonArray
                ?: throw GatewayException("Unexpected listSessions response: $result")
            else -> throw GatewayException("Unexpected listSessions response type: $result")
        }
        return json.decodeFromJsonElement(array)
    }

    /**
     * Delete a session by ID.
     */
    suspend fun deleteSession(sessionId: String) {
        client.request(
            RpcMethods.SESSION_DELETE,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /**
     * Rename a session.
     */
    suspend fun renameSession(sessionId: String, newTitle: String) {
        client.request(
            RpcMethods.SESSION_TITLE,
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("title", JsonPrimitive(newTitle))
            },
        )
    }

    // ── Prompting ─────────────────────────────────────────────────────────

    /**
     * Submit a text prompt to the given session and stream back [GatewayEvent]s.
     *
     * The returned [Flow] emits events as they arrive over the WebSocket and
     * completes when a terminal event is received ([MessageComplete] or [EventError]).
     * If the underlying RPC fails the flow is closed with the exception.
     *
     * **Thread‑safety note:** the flow emits on OkHttp's WebSocket thread.
     * Downstream operators that need a specific dispatcher should apply
     * `.flowOn(…)` themselves.
     */
    fun sendPrompt(sessionId: String, text: String): Flow<GatewayEvent> = callbackFlow {
        var ready = false

        val unsub = client.onAny { event ->
            if (event.sessionId != sessionId) return@onAny
            if (!ready) return@onAny        // don't replay stale events
            trySend(event)
            if (event is MessageComplete || event is EventError) {
                channel.close()
            }
        }

        launch {
            try {
                client.request(
                    RpcMethods.PROMPT_SUBMIT,
                    buildJsonObject {
                        put("session_id", JsonPrimitive(sessionId))
                        put("text", JsonPrimitive(text))
                    },
                )
                ready = true
            } catch (e: Exception) {
                unsub()
                close(e)
            }
        }

        awaitClose { unsub() }
    }

    // ── Interaction responses ─────────────────────────────────────────────

    /**
     * Approve or deny a tool‑execution request.
     * @param id  The `requestId` from [ApprovalRequest].
     */
    suspend fun respondApproval(id: String, approved: Boolean): JsonElement {
        return client.request(
            RpcMethods.APPROVAL_RESPOND,
            buildJsonObject {
                put("id", JsonPrimitive(id))
                put("approved", JsonPrimitive(approved))
            },
        )
    }

    /**
     * Answer a clarification question from the model.
     * @param id  The `requestId` from [ClarifyRequest].
     */
    suspend fun respondClarify(id: String, response: String): JsonElement {
        return client.request(
            RpcMethods.CLARIFY_RESPOND,
            buildJsonObject {
                put("id", JsonPrimitive(id))
                put("response", JsonPrimitive(response))
            },
        )
    }

    /**
     * Provide a sudo password when the agent requests one.
     * @param id  The `requestId` from [SudoRequest].
     */
    suspend fun respondSudo(id: String, password: String): JsonElement {
        return client.request(
            RpcMethods.SUDO_RESPOND,
            buildJsonObject {
                put("id", JsonPrimitive(id))
                put("password", JsonPrimitive(password))
            },
        )
    }

    /**
     * Provide a secret / credential when the agent requests one.
     * @param id  The `requestId` from [SecretRequest].
     */
    suspend fun respondSecret(id: String, secret: String): JsonElement {
        return client.request(
            RpcMethods.SECRET_RESPOND,
            buildJsonObject {
                put("id", JsonPrimitive(id))
                put("secret", JsonPrimitive(secret))
            },
        )
    }

    // ── Session actions ───────────────────────────────────────────────────

    /**
     * Resume a session so the daemon loads it from storage into memory.
     * Required before [getSessionHistory] returns history for a session that
     * is not already resident in the daemon.
     */
    suspend fun resumeSession(sessionId: String): JsonElement {
        return client.request(
            RpcMethods.SESSION_RESUME,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /** Load history messages for a session. Returns a JSON array of {role, content} objects. */
    suspend fun getSessionHistory(sessionId: String): JsonElement {
        return client.request(
            RpcMethods.SESSION_HISTORY,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /** Interrupt the current generation in the given session. */
    suspend fun interruptSession(sessionId: String) {
        client.request(
            RpcMethods.SESSION_INTERRUPT,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    // ── Config ────────────────────────────────────────────────────────────

    /**
     * Read a configuration value.
     * @param key  One of the legal keys: `"provider"`, `"profile"`, `"project"`,
     *             `"full"`, `"prompt"`, `"skin"`, `"indicator"`.
     * @throws GatewayRpcException with code 4002 if the key is missing/empty.
     */
    suspend fun getConfig(key: String): JsonElement {
        return client.request(
            RpcMethods.CONFIG_GET,
            buildJsonObject { put("key", JsonPrimitive(key)) },
        )
    }

    /**
     * Set a configuration value.
     */
    suspend fun setConfig(key: String, value: JsonElement): JsonElement {
        return client.request(
            RpcMethods.CONFIG_SET,
            buildJsonObject {
                put("key", JsonPrimitive(key))
                put("value", value)
            },
        )
    }

    // ── Internal: reconnect ───────────────────────────────────────────────

    private fun startReconnectLoop() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            client.connectionState.collect { state ->
                if (!intentionalClose &&
                    (state == ConnectionState.Closed || state == ConnectionState.Error)
                ) {
                    exponentialBackoffReconnect()
                }
            }
        }
    }

    /**
     * Keep trying to reconnect until success or [intentionalClose] is set.
     */
    private suspend fun exponentialBackoffReconnect() {
        var delayMs = 1_000L
        while (true) {
            if (intentionalClose) return
            delay(delayMs)
            if (intentionalClose) return

            try {
                client.connect(wsUrl)
                return // success — exit retry loop
            } catch (_: Exception) {
                delayMs = (delayMs * 2).coerceAtMost(30_000L)
            }
        }
    }
}
