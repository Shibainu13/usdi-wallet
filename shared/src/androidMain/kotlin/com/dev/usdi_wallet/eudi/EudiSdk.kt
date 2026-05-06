package com.dev.usdi_wallet.eudi

import android.app.Application
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.logging.Logger
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class EudiSdk private constructor() {
    lateinit var wallet: EudiWallet private set
    private val _inboundUriFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val inboundUriFlow = _inboundUriFlow.asSharedFlow()

    fun start(context: Application) {
        if (this::wallet.isInitialized) return

        val storageFile = File(context.noBackupFilesDir.path, "eudi.db")
        val config = EudiWalletConfig()
            .configureDocumentManager(storageFile.absolutePath)
            .configureLogging(level = Logger.LEVEL_DEBUG)
            .configureDocumentKeyCreation(
                userAuthenticationRequired = true,
                userAuthenticationTimeout = 30_000.milliseconds,
                useStrongBoxForKeys = true,
            )
            .configureOpenId4Vci {
                withIssuerUrl("https://13.90.44.25/pid-issuer")
                withAuthFlowRedirectionURI("eudi-openid4ci://authorize")
                withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
            }
            .configureProximityPresentation(
                enableBlePeripheralMode = true,
                enableBleCentralMode = true,
                clearBleCache = true,
            )
            .configureOpenId4Vp {
                withClientIdSchemes(ClientIdScheme.X509SanDns)
                withSchemes(
                    "openid4vp",
                    "eudi-openid4vp",
                    "mdoc-openid4vp",
                )
                withFormats(
                    Format.MsoMdoc.ES256,
                    Format.SdJwtVc.ES256,
                )
            }
            .configureDocumentStatusResolver(clockSkewInMinutes = 5)

        wallet = EudiWallet(context, config)
    }

    suspend fun processInvitation(uri: String) {
        _inboundUriFlow.emit(uri)
    }

    companion object {
        private lateinit var instance: EudiSdk

        @JvmStatic
        fun getInstance(): EudiSdk {
            if (!this::instance.isInitialized) {
                instance = EudiSdk()
            }
            return instance
        }
    }
}