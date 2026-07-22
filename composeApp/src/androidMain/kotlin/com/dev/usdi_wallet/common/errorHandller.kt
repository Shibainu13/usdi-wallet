package com.dev.usdi_wallet.common

import android.util.Log
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Standardized Error Handler for USDI Wallet
 */
object ErrorHandler {

    /**
     * Categorizes and logs errors, returning a user-friendly message for the UI.
     */
    fun handleError(throwable: Throwable): String {
        Log.e("ErrorHandler", "Error encountered: ${throwable.message}", throwable)

        return when (throwable) {
            is SocketTimeoutException -> "The server is taking too long to respond. Please try again."
            is UnknownHostException -> "Server unavailable. Check the server URL or continue using local wallet features."
            is ConnectException,
            is NoRouteToHostException -> "Could not connect to the server. Check that it is running and reachable."
            is SecurityException -> "Permission denied. Enable the required permission and try again."
            is IOException -> "Network error. Please check your internet connection."
            is WalletException -> throwable.customMessage
            else -> throwable.message?.takeIf { it.isNotBlank() }
                ?: "An unexpected error occurred. Please try again later."
        }
    }

    fun handleError(action: String, throwable: Throwable): String {
        return "$action: ${handleError(throwable)}"
    }
}

class WalletException(
    val errorCode: String,
    val customMessage: String
) : Exception(customMessage)
