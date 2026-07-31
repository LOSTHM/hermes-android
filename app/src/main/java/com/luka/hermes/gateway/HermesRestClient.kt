package com.luka.hermes.gateway

import android.content.Context
import com.luka.hermes.ui.SettingsKeys
import com.luka.hermes.ui.settingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Thrown for non-2xx REST responses (including 401 for an invalid/expired token). */
class HermesRestException(message: String) : Exception(message)

/**
 * Minimal REST client for the Hermes daemon HTTP API.
 *
 * The daemon exposes ~223 REST endpoints at `http://127.0.0.1:9119/api/`
 * authenticated with an `X-Hermes-Session-Token` header.  This client only
 * covers the GET subset used by the Git panel today, but is easy to extend.
 *
 * Call [init] once with a context before first use.  The token is read fresh
 * from DataStore on every request, so settings changes take effect without
 * reinitialising.
 */
object HermesRestClient {

    private const val BASE_URL = "http://127.0.0.1:9119/api"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var appContext: Context? = null

    /** Remember the (application) context needed to read the session token. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private suspend fun sessionToken(): String {
        val context = appContext ?: return ""
        return context.settingsDataStore.data.first()[SettingsKeys.TOKEN] ?: ""
    }

    /**
     * Perform a GET against `http://127.0.0.1:9119/api/{path}`.
     *
     * Query parameters are appended URL-encoded (e.g. `get("fs/list", mapOf("path" to "/"))`).
     *
     * @throws HermesRestException on non-2xx responses (e.g. 401) or transport failures.
     */
    suspend fun get(path: String, queryParams: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val query = if (queryParams.isEmpty()) "" else
            "?" + queryParams.entries.joinToString("&") { (k, v) ->
                "${k}=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }

        val request = Request.Builder()
            .url("$BASE_URL/$path$query")
            .header("X-Hermes-Session-Token", sessionToken())
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HermesRestException("HTTP ${response.code} for /api/$path: $body")
            }
            body
        }
    }

    /**
     * Like [get] but parsed into a [JsonElement].
     *
     * Non-JSON bodies are wrapped in a [JsonPrimitive] and blank bodies become
     * [JsonNull] rather than throwing.
     */
    suspend fun getJson(path: String, queryParams: Map<String, String> = emptyMap()): JsonElement {
        val raw = get(path, queryParams)
        if (raw.isBlank()) return JsonNull
        return try {
            json.parseToJsonElement(raw)
        } catch (_: Exception) {
            JsonPrimitive(raw)
        }
    }

    /**
     * Perform a POST against `http://127.0.0.1:9119/api/{path}` with a JSON body.
     *
     * @param body raw request body; sent as `application/json` (may be blank).
     * @throws HermesRestException on non-2xx responses or transport failures.
     */
    suspend fun post(path: String, body: String = ""): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/$path")
            .header("X-Hermes-Session-Token", sessionToken())
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HermesRestException("HTTP ${response.code} for /api/$path: $responseBody")
            }
            responseBody
        }
    }

    /**
     * Perform a DELETE against `http://127.0.0.1:9119/api/{path}`.
     *
     * Query parameters are appended URL-encoded (e.g. `delete("webhooks/foo")`).
     *
     * @throws HermesRestException on non-2xx responses or transport failures.
     */
    suspend fun delete(path: String, queryParams: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val query = if (queryParams.isEmpty()) "" else
            "?" + queryParams.entries.joinToString("&") { (k, v) ->
                "${k}=${java.net.URLEncoder.encode(v, "UTF-8")}"
            }

        val request = Request.Builder()
            .url("$BASE_URL/$path$query")
            .header("X-Hermes-Session-Token", sessionToken())
            .delete()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw HermesRestException("HTTP ${response.code} for /api/$path: $responseBody")
            }
            responseBody
        }
    }
}
