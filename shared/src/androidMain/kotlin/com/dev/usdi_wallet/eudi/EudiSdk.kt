package com.dev.usdi_wallet.eudi

import android.app.Application
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class EudiSdk private constructor() {
    lateinit var wallet: EudiWallet private set
    private val _inboundUriFlow = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val inboundUriFlow = _inboundUriFlow.asSharedFlow()

    fun start(context: Application) {
        if (this::wallet.isInitialized) return

        val storageFile = File(context.noBackupFilesDir.path, "eudi.db")
        val config = EudiWalletConfig()
            .configureDocumentManager(storageFile.absolutePath)
            .configureDocumentKeyCreation(
                userAuthenticationRequired = true,
                useStrongBoxForKeys = true,
            )
            .configureOpenId4Vci {
                withAuthFlowRedirectionURI("usdi_wallet://authorize")
            }
            .configureOpenId4Vp {
                withSchemes("openid4vp", "eudi-openid4vp", "mdoc-openid4vp")
            }

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