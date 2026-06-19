package com.dev.usdi_wallet.ui.contact

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dev.usdi_wallet.ui.common.ProtocolBadge
import com.dev.usdi_wallet.ui.common.ScreenHeader
import com.dev.usdi_wallet.ui.common.SectionLabel
import com.dev.usdi_wallet.ui.common.WalletListItem
import com.dev.usdi_wallet.ui.theme.WalletColors

@Composable
fun ContactScreen(viewModel: ContactViewModel) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(WalletColors.Surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Contacts",
                subtitle = if (contacts.isEmpty()) "No contacts yet"
                else "${contacts.size} connection${if (contacts.size == 1) "" else "s"}",
            )

            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No contacts yet",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = WalletColors.TextSecondary,
                        )
                        Text(
                            text = "Contacts from verification flows will appear here",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { SectionLabel(text = "Connections") }
                    items(contacts, key = { it.holder }) { contact ->
                        WalletListItem(
                            icon = Icons.Default.Person,
                            title = contact.name.ifBlank {
                                // Truncate DID for display
                                val did = contact.holder
                                if (did.length > 32) "${did.take(16)}...${did.takeLast(8)}"
                                else did
                            },
                            subtitle = contact.holder.take(24) + "...",
                            badge = { ProtocolBadge(protocol = contact.protocol) },
                            onClick = { /* detail screen — future work */ },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }
            }
        }
    }
}