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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Thrown for non-2xx REST responses (including 401 for an invalid/expired token). */
class HermesRestException(message: String) : Exception(message)

/**
 * Minimal REST client for the Hermes daemon HTTP API.
 *
 * The daemon exposes ~223 REST endpoints at `http://127.0.0.1:9119/api/*`
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
        val urlBuilder = toHttpUrlOrNull("$BASE_URL/$path")
            ?: throw HermesRestException("Invalid URL for /api/$path")
        queryParams.forEach { (key, value) -> urlBuilder.addQueryParameter(key, value) }

        val request = Request.Builder()
            .url(urlBuilder.build())
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
}
