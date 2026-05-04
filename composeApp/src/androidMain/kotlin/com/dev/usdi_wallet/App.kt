package com.dev.usdi_wallet

import android.app.Application
import com.dev.usdi_wallet.eudi.EudiSdk
import com.dev.usdi_wallet.hyperledger_identus.HyperledgerIdentusSdk

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        HyperledgerIdentusSdk.getInstance()
        EudiSdk.getInstance()
    }
}