package com.dev.usdi_wallet.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object WalletHttpClient {
    val instance: HttpClient by lazy { create() }

    private fun create(): HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true})
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }
}