package com.luka.hermes.gateway

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebSocket connection state mirroring the TypeScript client.
 */
enum class ConnectionState {
    Idle,
    Connecting,
    Open,
    Closed,
    Error,
}

// ── Public type aliases ─────────────────────────────────────────────────────

typealias EventHandler = (GatewayEvent) -> Unit
typealias StateHandler = (ConnectionState) -> Unit

/**
 * Low-level Hermes JSON-RPC 2.0 WebSocket gateway client.
 *
 * Translated faithfully from the TypeScript reference at
 * `hermes-agent/apps/shared/src/json-rpc-gateway.ts`.
 *
 * Thread‑safe and coroutine‑friendly — no external scope needed because
 * all async work is done inside the caller's coroutine context.
 */
class GatewayClient(
    /** Shared [Json] instance used for frame serialisation / deserialisation. */
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    },
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // WebSocket: no read timeout
        .build(),
    /** Max time (ms) to wait for the WebSocket to open. */
    private val connectTimeoutMs: Long = 15_000L,
    /** Default max time (ms) to wait for an RPC response. */
    private val requestTimeoutMs: Long = 120_000L,
    private val notConnectedMessage: String = "gateway not connected",
    private val closedMessage: String = "WebSocket closed",
    private val connectErrorMessage: String = "WebSocket connection failed",
) {
    // ── Internal state ────────────────────────────────────────────────────

    // Guard against stale WebSocket callbacks — any listener whose socket is
    // not the current [socket] reference is silently ignored.
    @Volatile
    private var socket: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()
    private val nextId = AtomicInteger(0)

    // Handler registries (thread‑safe, read‑optimised via CopyOnWriteArrayList).
    private val eventHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<EventHandler>>()
    private val anyHandlers = CopyOnWriteArrayList<EventHandler>()
    private val stateHandlers = CopyOnWriteArrayList<StateHandler>()

    // ── Connection management ─────────────────────────────────────────────

    /**
     * Open a WebSocket to the Hermes gateway.
     *
     * Returns once the socket reaches [ConnectionState.Open], or throws if
     * the connection fails or [connectTimeoutMs] elapses.
     *
     * If already connected or connecting the call is a no-op.
     */
    suspend fun connect(wsUrl: String) {
        // ── fast path: already open or in-flight ──────────────────────────
        val currentState = _connectionState.value
        if (currentState == ConnectionState.Open) return
        if (currentState == ConnectionState.Connecting) return

        setState(ConnectionState.Connecting)

        val connectResult = CompletableDeferred<Result<Unit>>()
        val httpRequest = Request.Builder().url(wsUrl).build()

        val ws = httpClient.newWebSocket(httpRequest, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket != this@GatewayClient.socket) return
                setState(ConnectionState.Open)
                connectResult.complete(Result.success(Unit))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket != this@GatewayClient.socket) return
                this@GatewayClient.socket = null
                setState(ConnectionState.Error)
                connectResult.complete(Result.failure(GatewayException(connectErrorMessage, t)))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket != this@GatewayClient.socket) return
                handleMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket != this@GatewayClient.socket) return
                this@GatewayClient.socket = null
                setState(ConnectionState.Closed)
                rejectAllPending(GatewayException("$closedMessage: $reason"))
            }
        })

        this@GatewayClient.socket = ws

        try {
            withTimeout(connectTimeoutMs) {
                connectResult.await().getOrThrow()
            }
        } catch (e: TimeoutCancellationException) {
            // Timed out waiting for onOpen — tear down the half-open socket.
            ws.close(1001, "Connect timeout")
            if (this@GatewayClient.socket === ws) {
                this@GatewayClient.socket = null
            }
            setState(ConnectionState.Error)
            throw GatewayException(connectErrorMessage, e)
        }
    }

    /**
     * Gracefully close the WebSocket and reject all in-flight requests.
     */
    fun close() {
        val ws = socket ?: return
        socket = null
        setState(ConnectionState.Closed)
        try {
            ws.close(1000, "Client close")
        } catch (_: Exception) {
            // best-effort
        }
        rejectAllPending(GatewayException(closedMessage))
    }

    // ── Subscriptions ─────────────────────────────────────────────────────

    /**
     * Register a handler for a specific event [type] (e.g. `"message.delta"`).
     *
     * @return An unsubscribe function — call it to remove the handler.
     */
    fun on(type: String, handler: EventHandler): () -> Unit {
        eventHandlers.getOrPut(type) { CopyOnWriteArrayList() }.add(handler)
        return { eventHandlers[type]?.remove(handler) }
    }

    /**
     * Register a handler that receives **every** event regardless of type.
     *
     * @return An unsubscribe function.
     */
    fun onAny(handler: EventHandler): () -> Unit {
        anyHandlers.add(handler)
        return { anyHandlers.remove(handler) }
    }

    /**
     * Register a handler that fires on every [ConnectionState] transition.
     *
     * The handler is invoked immediately with the current state.
     *
     * @return An unsubscribe function.
     */
    fun onState(handler: StateHandler): () -> Unit {
        stateHandlers.add(handler)
        handler(_connectionState.value)
        return { stateHandlers.remove(handler) }
    }

    // ── RPC requests ──────────────────────────────────────────────────────

    /**
     * Send a JSON-RPC request and await the typed response.
     *
     * @param method  RPC method name (use constants from [RpcMethods]).
     * @param params  JSON‑RPC params object (default empty).
     * @param timeoutMs  Override the default request timeout.
     * @return The decoded result of type [T].
     * @throws GatewayNotConnectedException if the socket is not open.
     * @throws GatewayTimeoutException if [timeoutMs] elapses.
     * @throws GatewayRpcException if the server returns an error frame.
     */
    suspend inline fun <reified T> request(
        method: String,
        params: JsonObject = buildJsonObject { },
        timeoutMs: Long = requestTimeoutMs,
    ): T {
        val ws = socket ?: throw GatewayNotConnectedException(notConnectedMessage)

        val id = nextId.incrementAndGet()
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred

        try {
            val frame = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(id))
                put("method", JsonPrimitive(method))
                put("params", params)
            }

            val sent = ws.send(json.encodeToString(frame))
            if (!sent) {
                pending.remove(id)
                throw GatewayNotConnectedException(notConnectedMessage)
            }

            val resultElement: JsonElement = withTimeout(timeoutMs) {
                deferred.await()
            }

            @Suppress("UNCHECKED_CAST")
            return when {
                T::class == JsonElement::class -> resultElement as T
                T::class == Unit::class -> Unit as T
                else -> json.decodeFromJsonElement<T>(resultElement)
            }
        } catch (e: TimeoutCancellationException) {
            pending.remove(id)
            if (!deferred.isCompleted) {
                deferred.completeExceptionally(GatewayTimeoutException(method))
            }
            throw GatewayTimeoutException(method)
        } catch (e: CancellationException) {
            // True coroutine cancellation (e.g. parent job cancelled) —
            // clean up and re-throw.
            pending.remove(id)
            throw e
        } catch (e: GatewayNotConnectedException) {
            // Already cleaned up pending; re-throw as-is.
            throw e
        } catch (e: Exception) {
            pending.remove(id)
            throw e
        }
    }

    // ── Message handling ──────────────────────────────────────────────────

    private fun handleMessage(text: String) {
        val frame = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return // Malformed JSON — silently drop (matching TS client).
        }

        // ── RPC response (has a numeric id matching a pending request) ────────
        val idEl = frame["id"]
        val id: Int? = when (idEl) {
            is JsonPrimitive -> idEl.intOrNull       // null for string IDs (TS client)
            else -> null                              // absent key, JsonNull, object, array
        }
        if (id != null) {
            val deferred = pending.remove(id) ?: return
            val error = frame["error"]?.jsonObject
            if (error != null) {
                val msg = error["message"]?.jsonPrimitive?.contentOrNull ?: "RPC failed"
                val code = error["code"]?.jsonPrimitive?.intOrNull
                deferred.completeExceptionally(GatewayRpcException(msg, code))
            } else {
                deferred.complete(frame["result"] ?: JsonNull)
            }
            return
        }

        // ── Server event (method == "event") ──────────────────────────────
        val method = frame["method"]?.jsonPrimitive?.contentOrNull
        val params = frame["params"]?.jsonObject
        if (method == "event" && params != null && params.containsKey("type")) {
            dispatchEvent(params)
        }
    }

    private fun dispatchEvent(params: JsonObject) {
        val event = parseGatewayEvent(params)

        // Type‑specific handlers.
        eventHandlers[event.type]?.forEach { it(event) }

        // Catch‑all handlers.
        anyHandlers.forEach { it(event) }
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun setState(state: ConnectionState) {
        _connectionState.value = state
        stateHandlers.forEach { it(state) }
    }

    private fun rejectAllPending(error: GatewayException) {
        val ids = pending.keys.toList() // snapshot
        for (id in ids) {
            pending.remove(id)?.completeExceptionally(error)
        }
    }
}
