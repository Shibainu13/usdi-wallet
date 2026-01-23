package com.dev.usdi_wallet.connection

import kotlinx.coroutines.flow.MutableSharedFlow

sealed interface ConnectionState {
    val logMessage: String
    data object Idle: ConnectionState {
        override val logMessage: String = "Idling"
    }
    data class Success(val message: String): ConnectionState {
        override val logMessage: String = message
    }
    data class Error(val reason: String): ConnectionState {
        override val logMessage: String = reason
    }
    data class Pending(val status: String): ConnectionState {
        override val logMessage: String = status
    }
}