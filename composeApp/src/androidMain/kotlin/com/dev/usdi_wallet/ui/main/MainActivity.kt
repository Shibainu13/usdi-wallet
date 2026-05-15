package com.dev.usdi_wallet.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var deepLinkRouter: DeepLinkRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deepLinkRouter = DeepLinkRouter(
            protocols = listOf(
                IdentusJWTProtocol.getInstance(application, lifecycleScope),
                EudiProtocol.getInstance(application, lifecycleScope),
            ),
            scope = lifecycleScope,
        )

        deepLinkRouter.handle(intent)

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
        Logger.d(MainActivity::class.toString()) { "Received new intent: $intent" }
        setIntent(intent)
        deepLinkRouter.handle(intent)
    }
}
