package com.dev.usdi_wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.usdi_wallet.connection.ConnectionState
import com.dev.usdi_wallet.viewmodel.ConnectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionTestScreen(viewModel: ConnectionViewModel) {
    var invitationInput by remember { mutableStateOf("") }
    val connectionState by viewModel.currentState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "DIDComm Connection Test",
            style = MaterialTheme.typography.headlineMedium
        )

        // Connection State Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionState) {
                    is ConnectionState.Success -> MaterialTheme.colorScheme.primaryContainer
                    is ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
                    is ConnectionState.Pending -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Connection Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (val state = connectionState) {
                        is ConnectionState.Idle -> "Ready to connect"
                        is ConnectionState.Success -> "✓ ${state.message}"
                        is ConnectionState.Error -> "✗ ${state.reason}"
                        is ConnectionState.Pending -> "⋯ ${state.status}"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input field
        OutlinedTextField(
            value = invitationInput,
            onValueChange = { invitationInput = it },
            label = { Text("Paste DIDComm Invitation") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            placeholder = { Text("Paste your invitation URL or JSON here") }
        )

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    viewModel.processInput(invitationInput)
                },
                modifier = Modifier.weight(1f),
                enabled = invitationInput.isNotBlank() &&
                        connectionState !is ConnectionState.Pending
            ) {
                Text("Connect")
            }

            OutlinedButton(
                onClick = {
                    invitationInput = ""
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
            }
        }

        // Sample invitation button
        OutlinedButton(
            onClick = {
                invitationInput = """
                    {
                      "type": "https://didcomm.org/out-of-band/2.0/invitation",
                      "id": "12345",
                      "from": "did:peer:example",
                      "body": {}
                    }
                """.trimIndent()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Load Sample Invitation")
        }

        // Connection history hint
        if (connectionState is ConnectionState.Success) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "You can now exchange credentials with this connection",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}