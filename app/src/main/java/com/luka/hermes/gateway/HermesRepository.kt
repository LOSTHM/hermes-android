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

    /**
     * Answer a `terminal.read.request` event with the serialized terminal
     * buffer. The gateway blocks the agent's `read_terminal` tool until this
     * is answered.
     * @param requestId  The `request_id` from [TerminalReadRequest].
     * @param response   The serialized terminal buffer + line metadata.
     */
    suspend fun respondTerminalRead(requestId: String, response: String): JsonElement {
        return client.request(
            RpcMethods.TERMINAL_READ_RESPOND,
            buildJsonObject {
                put("request_id", JsonPrimitive(requestId))
                put("text", JsonPrimitive(response))
            },
        )
    }

    // ── Voice ───────────────────────────────────────────────────────────

    /**
     * Ask the gateway to speak [text] aloud via the server-side TTS pipeline.
     * Returns `{"status":"speaking"}`.
     */
    suspend fun voiceTts(text: String): JsonElement {
        return client.request(
            RpcMethods.VOICE_TTS,
            buildJsonObject { put("text", JsonPrimitive(text)) },
        )
    }

    /**
     * Query or flip the daemon's voice mode via `voice.toggle`.
     *
     * The daemon accepts one of `"status"` (default), `"on"`, `"off"`, or
     * `"tts"`. A status/on/off reply carries `{enabled, record_key, tts}` plus
     * (for status) `available`/`stt_available`/`audio_available` probes.
     */
    suspend fun toggleVoice(action: String = "status"): JsonElement {
        return client.request(
            RpcMethods.VOICE_TOGGLE,
            buildJsonObject { put("action", JsonPrimitive(action)) },
        )
    }

    /**
     * Begin one VAD-bounded push-to-talk capture via `voice.record`.
     *
     * The daemon transcribes and emits `voice.transcript` once silence stops
     * the recorder. Returns `{"status":"recording"}` or `{"status":"busy"}`
     * if a capture is already running; fails with a 4015 error when voice
     * mode is off.
     */
    suspend fun startVoiceRecord(): JsonElement {
        return client.request(
            RpcMethods.VOICE_RECORD,
            buildJsonObject { put("action", JsonPrimitive("start")) },
        )
    }

    /**
     * Force-transcribe and stop the active capture via `voice.record`.
     *
     * Returns `{"status":"stopped"}`; the transcript arrives as a
     * `voice.transcript` event shortly after.
     */
    suspend fun stopVoiceRecord(): JsonElement {
        return client.request(
            RpcMethods.VOICE_RECORD,
            buildJsonObject { put("action", JsonPrimitive("stop")) },
        )
    }

    // ── Image attachment ────────────────────────────────────────────────

    /**
     * Attach an image to the session from raw base64 bytes (remote-client
     * path). The gateway writes the bytes into its own images dir and queues
     * them so the next `prompt.submit` picks them up.
     *
     * @param base64Data  base64 image bytes. A `data:image/...;base64,` prefix
     *                    and embedded whitespace are accepted.
     */
    suspend fun attachImageBytes(base64Data: String): JsonElement {
        return client.request(
            RpcMethods.IMAGE_ATTACH_BYTES,
            buildJsonObject { put("content_base64", JsonPrimitive(base64Data)) },
        )
    }

    // ── Session actions ───────────────────────────────────────────────────

    /**
     * Resume a session so the daemon loads it from storage into memory.
     * Primary source of chat history — the response carries the full message
     * list under `result.messages` (entries: `{role, text, reasoning?, context?, name?}`).
     */
    suspend fun resumeSession(sessionId: String): JsonElement {
        return client.request(
            RpcMethods.SESSION_RESUME,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /**
     * Load history messages for a session. Returns a JSON array of
     * `{role, content}` objects.
     *
     * **Not reliable on `serve`** — `session.history` replies with error
     * 4001 "session not found" even for resident sessions. Kept only as a
     * fallback; [resumeSession] is the primary path for history.
     */
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

    /**
     * Attach the frontend to an already-live session.
     */
    suspend fun activateSession(sessionId: String) {
        client.request(
            RpcMethods.SESSION_ACTIVATE,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /**
     * Close a session, tearing it down and unloading it from the daemon.
     */
    suspend fun closeSession(sessionId: String) {
        client.request(
            RpcMethods.SESSION_CLOSE,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /**
     * Undo the last turn in a session. Returns `{"removed": N}`.
     */
    suspend fun undoSession(sessionId: String): JsonElement {
        return client.request(
            RpcMethods.SESSION_UNDO,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /**
     * Fetch the token usage snapshot for a session.
     */
    suspend fun getSessionUsage(sessionId: String): JsonElement {
        return client.request(
            RpcMethods.SESSION_USAGE,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /**
     * Fetch the status lines for a session.
     */
    suspend fun getSessionStatus(sessionId: String): JsonElement {
        return client.request(
            RpcMethods.SESSION_STATUS,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /**
     * Fetch the context-window breakdown for a session.
     */
    suspend fun getSessionContextBreakdown(sessionId: String): JsonElement {
        return client.request(
            RpcMethods.SESSION_CONTEXT_BREAKDOWN,
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    // ── Tools management ─────────────────────────────────────────────────

    suspend fun listCronJobs(): JsonElement {
        return client.request(
            RpcMethods.CRON_MANAGE,
            buildJsonObject { put("action", JsonPrimitive("list")) },
        )
    }

    suspend fun listSkills(): JsonElement {
        return client.request(
            RpcMethods.SKILLS_MANAGE,
            buildJsonObject { put("action", JsonPrimitive("list")) },
        )
    }

    suspend fun listPlugins(): JsonElement {
        return client.request(RpcMethods.PLUGINS_LIST)
    }

    suspend fun listAgents(): JsonElement {
        return client.request(RpcMethods.AGENTS_LIST)
    }

    // ── Spawn trees (delegated subagents) ────────────────────────────────

    /**
     * List recorded subagent spawn trees via `spawn_tree.list`.
     *
     * Cross-session scanning is enabled so trees from every session are
     * returned; the server default (params `{}`) only reads the `default`
     * session dir, which is typically empty.  Returns
     * `{"entries": [{path, session_id, started_at, finished_at, label, count}]}`
     * sorted newest-first.
     */
    suspend fun listSpawnTree(): JsonElement {
        return client.request(
            RpcMethods.SPAWN_TREE_LIST,
            buildJsonObject { put("cross_session", JsonPrimitive(true)) },
        )
    }

    /**
     * Load a full spawn-tree snapshot via `spawn_tree.load`.
     *
     * @param path  The snapshot `path` from a [listSpawnTree] entry.
     * @return The snapshot object `{session_id, started_at, finished_at, label, subagents: [...]}`.
     */
    suspend fun loadSpawnTree(path: String): JsonElement {
        return client.request(
            RpcMethods.SPAWN_TREE_LOAD,
            buildJsonObject { put("path", JsonPrimitive(path)) },
        )
    }

    suspend fun listTools(): JsonElement {
        return client.request(RpcMethods.TOOLS_LIST)
    }

    suspend fun listToolsets(): JsonElement {
        return client.request(RpcMethods.TOOLSETS_LIST)
    }

    // ── System ───────────────────────────────────────────────────────────

    /** List running processes. Returns `process.list`. */
    suspend fun listProcesses(): JsonElement {
        return client.request(RpcMethods.PROCESS_LIST)
    }

    /** Kill a process by PID. Returns `process.kill`. */
    suspend fun killProcess(pid: Int): JsonElement {
        return client.request(
            RpcMethods.PROCESS_KILL,
            buildJsonObject { put("pid", JsonPrimitive(pid)) },
        )
    }

    /** Battery / power status. Returns `system.battery`. */
    suspend fun getSystemBattery(): JsonElement {
        return client.request(RpcMethods.SYSTEM_BATTERY)
    }

    /** Full config dump. Returns `config.show`. */
    suspend fun getConfigShow(): JsonElement {
        return client.request(RpcMethods.CONFIG_SHOW)
    }

    /** Available model options. Returns `model.options`. */
    suspend fun getModelOptions(): JsonElement {
        return client.request(RpcMethods.MODEL_OPTIONS)
    }

    /** Usage bar data. Returns `usage.bars`. */
    suspend fun getUsageBars(): JsonElement {
        return client.request(RpcMethods.USAGE_BARS)
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

    // ── Projects ─────────────────────────────────────────────────────────

    /**
     * Discover git repositories for the projects overview.
     *
     * Calls the `projects.discover_repos` RPC.  Returns
     * `{"repos": [...], "discovery_policy": {...}}` where each repo is
     * `{root, label, sessions, last_active}`.
     */
    suspend fun discoverRepos(): JsonElement {
        return client.request(RpcMethods.PROJECTS_DISCOVER_REPOS)
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
