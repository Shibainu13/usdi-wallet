package com.dev.usdi_wallet.hyperledger_identus

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hyperledger.identus.walletsdk.SdkPlutoDb
import org.hyperledger.identus.walletsdk.apollo.ApolloImpl
import org.hyperledger.identus.walletsdk.castor.CastorImpl
import org.hyperledger.identus.walletsdk.domain.buildingblocks.Apollo
import org.hyperledger.identus.walletsdk.domain.buildingblocks.Castor
import org.hyperledger.identus.walletsdk.domain.buildingblocks.Mercury
import org.hyperledger.identus.walletsdk.domain.buildingblocks.Pluto
import org.hyperledger.identus.walletsdk.domain.buildingblocks.Pollux
import org.hyperledger.identus.walletsdk.domain.models.ApiImpl
import org.hyperledger.identus.walletsdk.domain.models.DID
import org.hyperledger.identus.walletsdk.domain.models.Message as SdkMessage
import org.hyperledger.identus.walletsdk.domain.models.Seed
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgent
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgentError
import org.hyperledger.identus.walletsdk.edgeagent.mediation.BasicMediatorHandler
import org.hyperledger.identus.walletsdk.edgeagent.mediation.MediationHandler
import org.hyperledger.identus.walletsdk.edgeagent.protocols.mediation.MediationGrant
import org.hyperledger.identus.walletsdk.edgeagent.protocols.mediation.MediationRequest
import org.hyperledger.identus.walletsdk.mercury.MercuryImpl
import org.hyperledger.identus.walletsdk.pluto.PlutoImpl
import org.hyperledger.identus.walletsdk.pluto.data.DbConnection
import org.hyperledger.identus.walletsdk.pollux.PolluxImpl
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.TimeUnit

class HyperledgerIdentusSdk private constructor() {
    private val apollo: Apollo = createApollo()
    private val castor: Castor = createCastor()
    private val pollux: Pollux = createPollux()
    val pluto: Pluto = createPluto()
    val mercury: Mercury = createMercury()

    private val seed: Seed = createSeed()
    private val agentStatusStream: MutableLiveData<EdgeAgent.State> = MutableLiveData(EdgeAgent.State.STOPPED)

    lateinit var handler: MediationHandler
    lateinit var agent: EdgeAgent
    private var plutoDriver: SqlDriver? = null
    private var isAgentReady = false
    private var isMediatorAvailable = false
    private var canFetchMessages = false

    @Throws(EdgeAgentError.MediationRequestFailedError::class, UnknownHostException::class)
    suspend fun startAgent(mediatorDID: String, context: Context) {
        if (isAgentReady) return

        agentStatusStream.postValue(EdgeAgent.State.STARTING)
        val configuredMediatorDID = IdentusDIDCommConfig.activeMediatorDID(mediatorDID)
        handler = createHandler(configuredMediatorDID ?: OFFLINE_MEDIATOR_DID)
        agent = createAgent(handler)

        CoroutineScope(Dispatchers.Default).launch {
            agent.flowState.collect {
                agentStatusStream.postValue(it)
            }
        }

        startPluto(context)

        if (configuredMediatorDID == null) {
            val reason =
                if (IdentusDIDCommConfig.isMediatorDisabledForSession()) {
                    "DIDComm mediator was disabled after a previous failure"
                } else {
                    "DIDComm mediator is not configured"
                }
            runLocalWalletMode(reason)
            return
        }

        try {
            val hadRegisteredMediator = handler.bootRegisteredMediator() != null
            agent.start()
            enrollMediatorBeforePickup(hadRegisteredMediator)
            isMediatorAvailable = true
            startFetchingMessages()
            isAgentReady = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!IdentusDIDCommConfig.RUN_WITHOUT_MEDIATOR) throw e

            disableMediatorForSession()
            runLocalWalletMode("DIDComm mediator is unavailable: ${e.message ?: e::class.simpleName}")
        }
    }

    suspend fun startAgentForBackup(context: Application) {
        if (isAgentReady) return

        agentStatusStream.postValue(EdgeAgent.State.STARTING)
        handler = createHandler("did:prism:asldkfjalsdf")
        agent = createAgent(handler)

        CoroutineScope(Dispatchers.Default).launch {
            agent.flowState.collect {
                agentStatusStream.postValue(it)
            }
        }
        startPluto(context)
        isAgentReady = true
        isMediatorAvailable = false
        canFetchMessages = false
        agentStatusStream.postValue(EdgeAgent.State.RUNNING)
    }

    suspend fun startPluto(context: Context) {
        (pluto as PlutoImpl).start(context)
    }

    suspend fun stopAgent() {
        if (!this::agent.isInitialized) return

        if (canFetchMessages) {
            runCatching { agent.stopFetchingMessages() }
        }
        runCatching { agent.stop() }
        canFetchMessages = false
        isMediatorAvailable = false
        isAgentReady = false
        agentStatusStream.postValue(EdgeAgent.State.STOPPED)
    }

    suspend fun pauseAgent() {
        if (this::agent.isInitialized && canFetchMessages) {
            runCatching { agent.stopFetchingMessages() }
        }
    }

    fun resumeAgent() {
        if (this::agent.isInitialized && canFetchMessages) {
            runCatching { agent.startFetchingMessages() }
                .onFailure { error ->
                    IdentusDIDCommConfig.disableMediatorForSession()
                    canFetchMessages = false
                    isMediatorAvailable = false
                    Logger.w(HyperledgerIdentusSdk::class.toString()) {
                        "Failed to resume DIDComm message pickup: ${error.message}. Continuing in local wallet mode."
                    }
                }
        }
    }

    fun agentStatusStream(): LiveData<EdgeAgent.State> {
        return agentStatusStream
    }

    fun isAgentInitialized(): Boolean {
        return this::agent.isInitialized
    }

    fun canUseLocalAgent(): Boolean {
        return this::agent.isInitialized && isAgentReady
    }

    fun canUseMediator(): Boolean {
        return isMediatorAvailable && !IdentusDIDCommConfig.isMediatorDisabledForSession()
    }

    fun canReceiveMessages(): Boolean {
        return canUseMediator() && canFetchMessages
    }

    suspend fun sendMessage(message: SdkMessage): Any? {
        if (!canUseMediator()) {
            Logger.w(HyperledgerIdentusSdk::class.toString()) {
                "DIDComm mediator is unavailable; skipping outbound message ${message.id}"
            }
            return null
        }

        return try {
            agent.sendMessage(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!IdentusDIDCommConfig.RUN_WITHOUT_MEDIATOR) throw e

            disableMediatorForSession()
            Logger.w(HyperledgerIdentusSdk::class.toString()) {
                "DIDComm send failed: ${e.message}. Disabling mediator calls for this session."
            }
            null
        }
    }

    private fun createPluto(): Pluto {
        val customDbConnection = object : DbConnection {
            override var driver: SqlDriver? = null

            override suspend fun connectDb(context: Any?): SqlDriver {
                val androidContext = (context as? Context)
                    ?: throw IllegalStateException("Context required")

                val driver = AndroidSqliteDriver(
                    schema = SdkPlutoDb.Schema,
                    context = androidContext,
                    name = "hyperledger_identus.db"
                )
                this.driver = driver
                plutoDriver = driver
                return driver
            }
        }
        return PlutoImpl(customDbConnection)
    }

    fun upsertCredentialMetadata(id: String, linkSecretName: String, json: String) {
        val driver = plutoDriver ?: throw IllegalStateException("Pluto database is not started")
        driver.execute(
            identifier = null,
            sql = """
                INSERT OR REPLACE INTO CredentialMetadata(id, linkSecretName, json)
                VALUES (?, ?, ?)
            """.trimIndent(),
            parameters = 3,
        ) {
            bindString(0, id)
            bindString(1, linkSecretName)
            bindString(2, json)
        }
    }

    fun deleteCredentialMetadata(id: String) {
        plutoDriver?.execute(
            identifier = null,
            sql = "DELETE FROM CredentialMetadata WHERE id = ?",
            parameters = 1,
        ) {
            bindString(0, id)
        }
    }

    private fun createApollo(): Apollo {
        return ApolloImpl()
    }

    private fun createCastor(): Castor {
        return CastorImpl(apollo)
    }

    private fun createMercury(): Mercury {
        val customHttpClient = HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                }
            }
        }

        return MercuryImpl(castor, IdentusDIDCommWrapper(castor, pluto, apollo), ApiImpl(customHttpClient))
    }

    private fun createPollux(): Pollux {
        return PolluxImpl(apollo, castor)
    }

    private fun createSeed(): Seed {
        return Seed(
            Base64.getUrlDecoder()
                .decode("Rb8j6NVmA120auCQT6tP35rZ6-hgHvhcZCYmKmU1Avc4b5Tc7XoPeDdSWZYjLXuHn4w0f--Ulm1WkU1tLzwUEA")
        )
    }

    private fun createHandler(mediatorDID: String): MediationHandler {
        return BasicMediatorHandler(
            mediatorDID = DID(mediatorDID),
            mercury = mercury,
            store = BasicMediatorHandler.PlutoMediatorRepositoryImpl(pluto)
        )
    }

    private suspend fun enrollMediatorBeforePickup(hadRegisteredMediator: Boolean) {
        val mediator = handler.mediator ?: return

        if (hadRegisteredMediator) {
            val mediateRequest = MediationRequest(
                from = mediator.hostDID,
                to = mediator.mediatorDID
            ).makeMessage()
            val response = mercury.sendMessageParseResponse(mediateRequest)
                ?: throw EdgeAgentError.MediationRequestFailedError()

            val grant = MediationGrant(response)
            val routingDID = DID(grant.body.routingDid)
            if (routingDID != mediator.routingDID) {
                pluto.storeMediator(mediator.mediatorDID, mediator.hostDID, routingDID)
            }
        }

        handler.updateKeyListWithDIDs(arrayOf(mediator.hostDID))
    }

    private fun createAgent(handler: MediationHandler): EdgeAgent {
        return EdgeAgent(
            apollo = apollo,
            castor = castor,
            pluto = pluto,
            mercury = mercury,
            pollux = pollux,
            seed = seed,
            mediatorHandler = handler,
        )
    }

    private fun startFetchingMessages() {
        runCatching {
            agent.startFetchingMessages()
            canFetchMessages = true
        }.onFailure { error ->
            IdentusDIDCommConfig.disableMediatorForSession()
            canFetchMessages = false
            isMediatorAvailable = false
            Logger.w(HyperledgerIdentusSdk::class.toString()) {
                "DIDComm message pickup is unavailable: ${error.message}. Continuing in local wallet mode."
            }
        }
    }

    private fun runLocalWalletMode(reason: String) {
        Logger.w(HyperledgerIdentusSdk::class.toString()) {
            "$reason. Continuing in local wallet mode."
        }
        isMediatorAvailable = false
        canFetchMessages = false
        isAgentReady = true
        agentStatusStream.postValue(EdgeAgent.State.RUNNING)
    }

    suspend fun disableMediatorForSession() {
        IdentusDIDCommConfig.disableMediatorForSession()
        if (this::agent.isInitialized && canFetchMessages) {
            runCatching { agent.stopFetchingMessages() }
        }
        canFetchMessages = false
        isMediatorAvailable = false
    }

    companion object {
        private const val OFFLINE_MEDIATOR_DID = "did:prism:asldkfjalsdf"
        private lateinit var instance: HyperledgerIdentusSdk

        @JvmStatic
        fun getInstance(): HyperledgerIdentusSdk {
            if (!this::instance.isInitialized) {
                instance = HyperledgerIdentusSdk()
            }
            return instance
        }
    }
}
