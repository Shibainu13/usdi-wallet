package com.dev.usdi_wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import com.dev.usdi_wallet.connection.ConnectionManager
import com.dev.usdi_wallet.connection.hyperledger_identus.IdentusDIDCommHandler

class MainActivity : ComponentActivity() {
    private lateinit var connectionManager: ConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionManager = ConnectionManager(
            handlers = listOf(
                IdentusDIDCommHandler(
                    context = application,
                    // Demo mediatorDID
                    mediatorDID = "did:peer:2.Ez6LSghwSE437wnDE1pt3X6hVDUQzSjsHzinpX3XFvMjRAm7y.Vz6Mkhh1e5CEYYq6JBUcTZ6Cp2ranCWRrv7Yax3Le4N59R6dd.SeyJ0IjoiZG0iLCJzIjp7InVyaSI6Imh0dHA6Ly8xMC4wLjIuMjo4MDgwIiwiYSI6WyJkaWRjb21tL3YyIl19fQ.SeyJ0IjoiZG0iLCJzIjp7InVyaSI6IndzOi8vMTAuMC4yLjI6ODA4MC93cyIsImEiOlsiZGlkY29tbS92MiJdfX0"
                )
            ),
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectionTestScreen(connectionManager)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionManager.cleanUp()
    }
}