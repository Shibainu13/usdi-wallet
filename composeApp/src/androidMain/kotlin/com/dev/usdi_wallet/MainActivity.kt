package com.dev.usdi_wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dev.usdi_wallet.connection.ProtocolHandlerFactory
import com.dev.usdi_wallet.viewmodel.ConnectionViewModel

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<ConnectionViewModel> {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConnectionViewModel(
                    handlerFactory = ProtocolHandlerFactory(
                        context = applicationContext,
                        mediatorDID = "did:peer:2.Ez6LSghwSE437wnDE1pt3X6hVDUQzSjsHzinpX3XFvMjRAm7y.Vz6Mkhh1e5CEYYq6JBUcTZ6Cp2ranCWRrv7Yax3Le4N59R6dd.SeyJ0IjoiZG0iLCJzIjp7InVyaSI6Imh0dHA6Ly8xMC4wLjIuMjo4MDgwIiwiYSI6WyJkaWRjb21tL3YyIl19fQ.SeyJ0IjoiZG0iLCJzIjp7InVyaSI6IndzOi8vMTAuMC4yLjI6ODA4MC93cyIsImEiOlsiZGlkY29tbS92MiJdfX0"
                    )
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ConnectionTestScreen(viewModel)
                }
            }
        }
    }
}