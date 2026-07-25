package com.dev.usdi_wallet.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.common.BluetoothPermissionHelper
import com.dev.usdi_wallet.db.AppDatabase
import com.dev.usdi_wallet.domain.auth.AndroidWalletAuthManager
import com.dev.usdi_wallet.domain.backup.UnifiedBackupService
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
    private var isWalletUiInitialized = false
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val deniedPermissions = results.filterValues { granted -> !granted }.keys
        if (deniedPermissions.isNotEmpty()) {
            Logger.w(MainActivity::class.toString()) {
                "Bluetooth permissions denied: $deniedPermissions"
            }
        }
        initializeWalletUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installMercuryPostRestoreCrashHandler()

        if (!requestBluetoothPermissionsIfNeeded()) {
            initializeWalletUi()
        }
    }

    private fun installMercuryPostRestoreCrashHandler() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isMercuryPostRestoreCrash =
                throwable is org.didcommx.didcomm.exceptions.MalformedMessageException ||
                        throwable.cause?.javaClass?.name?.contains("MalformedMessage") == true

            if (isMercuryPostRestoreCrash) {
                Logger.w(MainActivity::class.toString()) {
                    "Caught Mercury post-restore crash; restarting..."
                }
                val intent = applicationContext
                    .packageManager
                    .getLaunchIntentForPackage(applicationContext.packageName)
                    ?.apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    }

                if (intent != null) {
                    applicationContext.startActivity(intent)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
            } else {
                defaultExceptionHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun initializeWalletUi() {
        if (isWalletUiInitialized) return
        isWalletUiInitialized = true

        protocols = listOf(
            IdentusAnonProtocol.getInstance(application, lifecycleScope),
            EudiProtocol.getInstance(application, lifecycleScope),
        )

        val preferences = AndroidWalletPreferences.getInstance(application)

        UnifiedBackupService.getInstance(
            protocols = protocols,
            onPreRestored = {
                AppDatabase.getInstance(application).clearAllTables()
            },
            onRestoreCompleted = {
                preferences.setOnboardingComplete()
            }
        )

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
        if (this::deepLinkRouter.isInitialized) {
            deepLinkRouter.handle(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        if (this::protocols.isInitialized) {
            protocols.forEach { protocol -> protocol.onActivityStart() }
        }
        walletAuthManager.bind(this as FragmentActivity)
    }

    override fun onStop() {
        super.onStop()
        walletAuthManager.unbind()
        if (this::protocols.isInitialized) {
            protocols.forEach { protocol -> protocol.onActivityStop() }
        }
    }

    private fun requestBluetoothPermissionsIfNeeded(): Boolean {
        val missingPermissions = BluetoothPermissionHelper.missingRuntimePermissions(this)
        if (missingPermissions.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(missingPermissions)
            return true
        }
        return false
    }
}
