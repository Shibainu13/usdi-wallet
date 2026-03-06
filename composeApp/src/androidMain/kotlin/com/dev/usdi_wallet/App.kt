package com.dev.usdi_wallet

import android.app.Application
import com.dev.usdi_wallet.connection.hyperledger_identus.HyperledgerIdentusSdk

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        HyperledgerIdentusSdk.Companion.getInstance()
    }
}