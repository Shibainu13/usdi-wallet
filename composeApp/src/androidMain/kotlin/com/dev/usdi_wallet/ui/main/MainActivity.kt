package com.dev.usdi_wallet.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.auth.AndroidWalletAuthManager
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.HyperledgerIdentusSdk
import com.dev.usdi_wallet.hyperledger_identus.IdentusJWTProtocol
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deepLinkRouter: DeepLinkRouter
    val walletAuthManager = AndroidWalletAuthManager.getInstance()

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
        Logger.d(MainActivity::class.toString()) { "MainActivity.kt.onNewIntent: Received new intent: $intent" }
        setIntent(intent)
        deepLinkRouter.handle(intent)
    }

    override fun onStart() {
        super.onStart()
        HyperledgerIdentusSdk.getInstance().resumeAgent()
        walletAuthManager.bind(this as FragmentActivity)
    }

    override fun onStop() {
        super.onStop()
        walletAuthManager.unbind()
        lifecycleScope.launch {
            HyperledgerIdentusSdk.getInstance().pauseAgent()
        }
    }
}
