package com.dev.usdi_wallet.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import co.touchlab.kermit.Logger

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleAuthIntent(intent)

        setContent {
            MaterialTheme {
                Surface {
                    MainRoute(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent) {
        if (Intent.ACTION_VIEW == intent.action) {
            val uri = intent.data
            if (uri != null && uri.scheme == "usdi-wallet" && uri.host == "auth-callback") {
                val authCode = uri.getQueryParameter("code")
                if (authCode != null) {
                    Logger.d(MainActivity::class.toString()) {
                        "Received AuthCode: $authCode"
                    }
                } else {
                    val error = uri.getQueryParameter("error")
                    Logger.e(MainActivity::class.toString()) {
                        "Failed to receive auth code: $error"
                    }
                }
            }
        }
    }
}
