//package com.dev.usdi_wallet.common
//
//import android.util.Log
//import java.io.IOException
//import java.net.SocketTimeoutException
//
///**
// * Standardized Error Handler for USDI Wallet
// */
//class ErrorHandler {
//
//    /**
//     * Categorizes and logs errors, returning a user-friendly message.
//     * @param throwable The caught exception
//     * @return A string resource ID or String message for the UI
//     */
//    fun handleError(throwable: Throwable): String {
//        // Log the error for debugging (use Timber or Firebase Crashlytics in production)
//        Log.e("ErrorHandler", "Error encountered: ${throwable.message}", throwable)
//
//        return when (throwable) {
//            is SocketTimeoutException -> "The server is taking too long to respond. Please try again."
//            is IOException -> "Network error. Please check your internet connection."
//            is WalletException -> throwable.customMessage
//            else -> "An unexpected error occurred. Please try again later."
//        }
//    }
//}
//
//class WalletException(
//    val errorCode: String,
//    val customMessage: String
//) : Exception(customMessage)