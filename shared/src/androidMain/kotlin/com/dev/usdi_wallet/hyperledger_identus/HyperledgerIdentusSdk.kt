package com.dev.usdi_wallet.hyperledger_identus

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
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
import org.hyperledger.identus.walletsdk.domain.models.Seed
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgent
import org.hyperledger.identus.walletsdk.edgeagent.EdgeAgentError
import org.hyperledger.identus.walletsdk.edgeagent.mediation.BasicMediatorHandler
import org.hyperledger.identus.walletsdk.edgeagent.mediation.MediationHandler
import org.hyperledger.identus.walletsdk.mercury.MercuryImpl
import org.hyperledger.identus.walletsdk.mercury.resolvers.DIDCommWrapper
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
    private val agentStatusStream: MutableLiveData<EdgeAgent.State> = MutableLiveData()

    lateinit var handler: MediationHandler
    lateinit var agent: EdgeAgent

    @Throws(EdgeAgentError.MediationRequestFailedError::class, UnknownHostException::class)
    suspend fun startAgent(mediatorDID: String, context: Context) {
        handler = createHandler(mediatorDID)
        agent = createAgent(handler)

        CoroutineScope(Dispatchers.Default).launch {
            agent.flowState.collect {
                agentStatusStream.postValue(it)
            }
        }

        startPluto(context)
        agent.start()
        try {
            agent.startFetchingMessages()
        } catch (_: Exception) {}
    }

    suspend fun startAgentForBackup(context: Application) {
        handler = createHandler("did:prism:asldkfjalsdf")
        agent = createAgent(handler)

        CoroutineScope(Dispatchers.Default).launch {
            agent.flowState.collect {
                agentStatusStream.postValue(it)
            }
        }
        startPluto(context)
        agentStatusStream.postValue(EdgeAgent.State.RUNNING)
    }

    suspend fun startPluto(context: Context) {
        (pluto as PlutoImpl).start(context)
    }

    fun stopAgent() {
        agent.let {
            it.stopFetchingMessages()
            it.stop()
        }
    }

    fun agentStatusStream(): LiveData<EdgeAgent.State> {
        return agentStatusStream
    }

    private fun createPluto(): Pluto {
        val customDbConnection = object : DbConnection {
            override var driver: SqlDriver? = null

            override suspend fun connectDb(context: Any?): SqlDriver {
                val androidContext = (context as? Context)
                    ?: throw IllegalStateException("Context required")

                val driver = AndroidSqliteDriver(
                    schema = SdkPlutoDb.Companion.Schema,
                    context = androidContext,
                    name = "hyperledger_identus.db"
                )
                this.driver = driver
                return driver
            }
        }
        return PlutoImpl(customDbConnection)
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

        return MercuryImpl(castor, DIDCommWrapper(castor, pluto, apollo), ApiImpl(customHttpClient))
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

    companion object {
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