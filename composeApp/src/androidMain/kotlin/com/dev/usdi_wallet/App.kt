package com.dev.usdi_wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource

import usdi_wallet.composeapp.generated.resources.Res
import usdi_wallet.composeapp.generated.resources.compose_multiplatform


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class AppPresenter {
    // The "Model" / State
    var isContentVisible by mutableStateOf(false)
        private set

    // The Logic
    fun onToggleClicked() {
        isContentVisible = !isContentVisible
    }
}

@Composable
@Preview
fun App() {
    MaterialTheme {
        // 1. Initialize the Presenter
        val presenter = remember { AppPresenter() }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding() // Ensure this extension exists or remove if error
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 2. Button calls the Presenter
            Button(onClick = { presenter.onToggleClicked() }) {
                Text("Click me!")
            }

            // 3. UI observes the Presenter's state
            AnimatedVisibility(presenter.isContentVisible) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Make sure 'Res' imports are correct for your KMP project
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
    }
}