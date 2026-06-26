package com.dev.usdi_wallet.domain.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidWalletAuthManager private constructor() : WalletAuthManager {
    private var activity : FragmentActivity? = null

    fun bind(activity: FragmentActivity) {
        this.activity = activity
    }

    fun unbind() {
        this.activity = null
    }

    override suspend fun requestAuth(): Boolean {
        if (BYPASS_WALLET_AUTH) return true

        val currentActivity = activity ?:
            throw Error("BiometricAuthManager current activity is null")
        return suspendCancellableCoroutine { continuation ->
            val prompt = BiometricPrompt(
                currentActivity,
                ContextCompat.getMainExecutor(currentActivity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (continuation.isActive) continuation.resume(true)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    override fun onAuthenticationFailed() {}
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock USDI Wallet")
                .setSubtitle("Authenticate to continue")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            prompt.authenticate(promptInfo)
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        }
    }

    companion object {
        private const val BYPASS_WALLET_AUTH = true
        private val _instance: AndroidWalletAuthManager by lazy { AndroidWalletAuthManager() }
        fun getInstance() = _instance
    }
}
