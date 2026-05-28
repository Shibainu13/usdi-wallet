package com.dev.usdi_wallet.eudi

import android.app.Application
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import eu.europa.ec.eudi.iso18013.transfer.TransferEvent
import eu.europa.ec.eudi.openid4vci.Nonce
import eu.europa.ec.eudi.wallet.EudiWallet
import eu.europa.ec.eudi.wallet.EudiWalletConfig
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager
import eu.europa.ec.eudi.wallet.provider.WalletAttestationsProvider
import eu.europa.ec.eudi.wallet.logging.Logger as SdkLogger
import eu.europa.ec.eudi.wallet.transfer.openId4vp.ClientIdScheme
import eu.europa.ec.eudi.wallet.transfer.openId4vp.Format
import eu.europa.ec.eudi.wallet.transfer.openId4vp.PreregisteredVerifier
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.milliseconds
import org.multipaz.securearea.KeyInfo
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class EudiSdk private constructor() {
    lateinit var wallet: EudiWallet private set
    lateinit var openId4VciManager: OpenId4VciManager
    private val _eudiMessageFlow = MutableStateFlow<List<EudiMessage>>(emptyList())
    val eudiMessageFlow = _eudiMessageFlow.asStateFlow()
    val walletAttestationsProvider = object : WalletAttestationsProvider {
        /**
         * WIA (Wallet Instance Attestation)
         * Used for Client Authentication (OAuth 2.0).
         */
        override suspend fun getWalletAttestation(keyInfo: KeyInfo): Result<String> {
            //  Make a network call to your Wallet Provider Service.
            //  Send the public key from 'keyInfo' (PoP key).
            //  Prove app integrity
            // Return the "Client Attestation JWT" signed by your Provider.
            return Result.success("ey...<The_WIA_JWT>")
        }

        /**
         * WUA (Wallet Unit Attestation)
         * Used to authorize Credential Issuance.
         */
        override suspend fun getKeyAttestation(keys: List<KeyInfo>, nonce: Nonce?): Result<String> {
            // Make a network call to your Wallet Provider Service.
            // Send the public keys (from 'keys') intended for the new Credential.
            // Provide the 'nonce' if required by the Issuer.
            // Return the "Wallet Unit Attestation" (or Key Attestation) JWT.
            // This certifies that these specific keys are hardware-bound and trusted.
            return Result.success("ey...<The_WUA_JWT>")
        }
    }

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
                withIssuerUrl("https://usdi-wallet.duckdns.org/pid-issuer")
                withClientAuthenticationType(
                    OpenId4VciManager.ClientAuthenticationType.None("wallet-dev")
                )
                withAuthFlowRedirectionURI("eudi-openid4ci://authorize")
                withParUsage(OpenId4VciManager.Config.ParUsage.IF_SUPPORTED)
            }
            .configureProximityPresentation(
                enableBlePeripheralMode = true,
                enableBleCentralMode = true,
                clearBleCache = true,
            )
            .configureOpenId4Vp {
                withClientIdSchemes(
                    ClientIdScheme.X509SanDns,
                    ClientIdScheme.Preregistered(
                        preregisteredVerifiers = listOf(
                            PreregisteredVerifier(
                                clientId = "Verifier",
                                legalName = "EUDI wallet verifier",
                                verifierApi = "https://usdi-wallet.duckdns.org",
                            )
                        )
                    )
                )
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

        wallet = EudiWallet(context, config,walletAttestationsProvider)
        openId4VciManager = wallet.createOpenId4VciManager(
            ktorHttpClientFactory = { buildTrustAllKtorClient() }
        )

        startTransferEventListener()
    }

    fun processInvitation(uri: String) {
        if (uri.contains("credential_offer=") || uri.startsWith("openid-credential-offer://")) {
            val message = EudiMessage.CredentialOffer(uri)
            _eudiMessageFlow.value = _eudiMessageFlow.value.plus(message)
        }
        if (uri.startsWith("openid4vp") || uri.startsWith("mdoc-openid4vp") || uri.contains("response_type=vp_token")) {
            wallet.startRemotePresentation(uri.toUri())
        }
        if (uri.startsWith("eudi-openid4ci://authorize")) {
            openId4VciManager.resumeWithAuthorization(uri.toUri())
        }
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
                    val message = EudiMessage.PresentationRequest(processedRequest)
                    _eudiMessageFlow.value = _eudiMessageFlow.value.plus(message)
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

    private fun buildTrustAllKtorClient(): HttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate?>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate?>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .build()

        return HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
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