package com.luka.hermes.ui

import com.luka.hermes.gateway.DirectApiClient
import com.luka.hermes.gateway.GatewayClient
import com.luka.hermes.gateway.HermesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Singleton shared Hermes repository and DirectApiClient instances.
 * Initialized lazily — call [init] once at app startup (e.g. from MainActivity).
 */
object HermesClient {
    private val client = GatewayClient()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val repository: HermesRepository by lazy {
        HermesRepository(client, scope)
    }

    val directApi: DirectApiClient by lazy {
        DirectApiClient()
    }
}
