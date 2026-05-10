package com.dev.usdi_wallet.eudi

import android.app.Application
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.logging.Logger as SdkLogger
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Duration.Companion.milliseconds

class EudiSdk private constructor() {
    lateinit var wallet: EudiWallet private set

    // Unified typed message flow replacing the raw String flow
    private val _eudiMessageFlow = MutableSharedFlow<EudiMessage>(extraBufferCapacity = 10)
    val eudiMessageFlow = _eudiMessageFlow.asSharedFlow()

    fun start(context: Application) {
        if (this::wallet.isInitialized) return

        val storageFile = File(context.noBackupFilesDir.path, "eudi.db")
        val config = EudiWalletConfig()
            .configureDocumentManager(storageFile.absolutePath)
            .configureLogging(level = SdkLogger.LEVEL_DEBUG)
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

        startTransferEventListener()
    }

    suspend fun processInvitation(uri: String) {
        val message = parseToEudiMessage(uri)
        _eudiMessageFlow.emit(message)
    }

    private fun parseToEudiMessage(uri: String): EudiMessage {
        if (uri.contains("credential_offer=") || uri.startsWith("openid-credential-offer://")) {
            return EudiMessage.CredentialOffer(uri)
        }
        if (uri.startsWith("openid4vp") || uri.startsWith("mdoc-openid4vp") || uri.contains("response_type=vp_token")) {
            wallet.startRemotePresentation(uri.toUri())
            return EudiMessage.PresentationInvitation(uri)
        }
        throw RuntimeException("Unrecognizable URL: $uri")
    }

    private fun startTransferEventListener() {
        wallet.addTransferEventListener { event ->
            when (event) {
                is TransferEvent.QrEngagementReady -> {
                    val qrCodeBitMap = event.qrCode.asBitmap(size = 800)
                }
                TransferEvent.Connecting -> {
                    Logger.d("EudiSdk") { "Devices are connecting..." }
                }
                TransferEvent.Connected -> {
                    Logger.d("EudiSdk") { "Devices are connected." }
                }
                is TransferEvent.RequestReceived -> try {
                    val processedRequest = event.processedRequest.getOrThrow()
                    val requestedDocuments = processedRequest.requestedDocuments
                    requestedDocuments.forEach { document ->
                        // Delegate this to your UI state later
                    }
                } catch (e: Exception) {
                    Logger.e("EudiSdk") { "Error receiving request: ${e.message}" }
                }
                TransferEvent.ResponseSent -> {
                    Logger.d("EudiSdk") { "Response sent" }
                }
                is TransferEvent.Redirect -> {
                    Logger.d("EudiSdk") { "Redirect URI: ${event.redirectUri}" }
                }
                TransferEvent.Disconnected -> {
                    wallet.stopProximityPresentation()
                }
                is TransferEvent.Error -> {
                    Logger.e("EudiSdk") { "Transfer error: ${event.error.message}" }
                    wallet.stopProximityPresentation()
                }
                else -> {}
            }
        }
    }

    // Receive intent url -> call startRemotePresentation immediately.
    // once TransferEvent.RequestReceived is observed, send a message to the flow, containing the processedRequest.
    // CredentialManager picks up the message with handleInbound, call handlePresentation to push the message to the _proofRequestToProcess flow
    // The UI catches this, display UI to choose credential & disclosed items -> submitting this will call CredentialManager.preparePresentationProof
    // CredentialManager.preparePresentationProof will compose the disclosed documents, generate and send the response.

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