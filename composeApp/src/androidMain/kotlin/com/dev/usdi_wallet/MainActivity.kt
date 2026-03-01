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
                    handlerFactory = ProtocolHandlerFactory(applicationContext)
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