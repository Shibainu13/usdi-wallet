package com.dev.usdi_wallet.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.auth.AndroidWalletAuthManager
import com.dev.usdi_wallet.domain.protocol.Protocol
import com.dev.usdi_wallet.eudi.EudiProtocol
import com.dev.usdi_wallet.hyperledger_identus.IdentusAnonProtocol
import com.dev.usdi_wallet.preferences.AndroidWalletPreferences
import com.dev.usdi_wallet.ui.theme.WalletColors
import com.dev.usdi_wallet.ui.theme.WalletTheme

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var deepLinkRouter: DeepLinkRouter
    private lateinit var protocols: List<Protocol<*,*>>
    val walletAuthManager = AndroidWalletAuthManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        protocols = listOf(
            IdentusAnonProtocol.getInstance(application, lifecycleScope),
            EudiProtocol.getInstance(application, lifecycleScope),
        )

        AndroidWalletPreferences.getInstance(application)

        deepLinkRouter = DeepLinkRouter.getInstance(
            protocols = protocols,
            scope = lifecycleScope,
        )

        deepLinkRouter.handle(intent)

        setContent {
            WalletTheme{
                Surface(color = WalletColors.Surface) {
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

    override fun onStart() {
        super.onStart()
        protocols.forEach { protocol -> protocol.onActivityStart() }
        walletAuthManager.bind(this as FragmentActivity)
    }

    override fun onStop() {
        super.onStop()
        walletAuthManager.unbind()
        protocols.forEach { protocol -> protocol.onActivityStop() }
    }
}
