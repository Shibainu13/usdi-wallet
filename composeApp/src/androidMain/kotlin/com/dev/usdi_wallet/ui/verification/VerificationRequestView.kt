package com.dev.usdi_wallet.ui.verification

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.contact.Contact
import com.dev.usdi_wallet.domain.credential.ClaimType
import com.dev.usdi_wallet.domain.credential.Credential
import com.dev.usdi_wallet.domain.credential.PredicateOperator
import com.dev.usdi_wallet.domain.credential.VerificationResult
import com.dev.usdi_wallet.hyperledger_identus.CloudAgentCredentialDefinition
import com.dev.usdi_wallet.ui.common.QrCodeUtils.createQrBitmap

private enum class VerificationTab(val title: String) {
    FROM_CREDENTIAL("From credential"),
    MANUAL("Manual"),
    SERVER_HTTP("Server HTTP"),
}

@Composable
fun VerificationRequestScreen(viewModel: VerificationRequestViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val credentials by viewModel.credentials.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val verificationResults by viewModel.verificationResults.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(VerificationTab.FROM_CREDENTIAL) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorShown()
        }
    }

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            snackbarHostState.showSnackbar("Verification request sent")
            viewModel.onSuccessHandled()
        }
    }

    LaunchedEffect(verificationResults.firstOrNull()?.messageId) {
        verificationResults.firstOrNull()?.let { result ->
            snackbarHostState.showSnackbar(
                if (result.isValid) "Verification successful" else "Verification failed",
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (selectedTab != VerificationTab.SERVER_HTTP) {
                    item {
                        ContactDropdown(
                            contacts = contacts,
                            selectedContact = uiState.selectedContact,
                            onContactSelected = viewModel::onContactSelected,
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = uiState.domain,
                            onValueChange = viewModel::onDomainChanged,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Domain") },
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedTextField(
                                value = uiState.challenge,
                                onValueChange = viewModel::onChallengeChanged,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Challenge") },
                            )
                            Button(onClick = viewModel::regenerateChallenge) {
                                Text("Regenerate")
                            }
                        }
                    }
                }

                item {
                    TabRow(selectedTabIndex = selectedTab.ordinal) {
                        VerificationTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                text = { Text(tab.title) },
                            )
                        }
                    }
                }

                verificationResults.firstOrNull()?.let { result ->
                    item {
                        VerificationResultCard(result)
                    }
                }

                when (selectedTab) {
                    VerificationTab.FROM_CREDENTIAL -> {
                        item {
                            CredentialDropdown(
                                credentials = credentials,
                                selectedCredential = uiState.selectedCredential,
                                onCredentialSelected = viewModel::onCredentialSelected,
                            )
                        }

                        itemsIndexed(uiState.claimItems, key = { index, item -> "${item.name}-$index" }) { index, item ->
                            ClaimItemCard(
                                item = item,
                                onCheckedChange = { viewModel.onClaimChecked(index, it) },
                                onConstraintChange = { viewModel.onClaimConstraintChanged(index, it) },
                                onPredicateOperatorChange = { viewModel.onClaimPredicateOperatorChanged(index, it) },
                                onPredicateValueChange = { viewModel.onClaimPredicateValueChanged(index, it) },
                            )
                        }

                        item {
                            Button(
                                onClick = viewModel::sendFromCredential,
                                enabled = !uiState.isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Send request")
                            }
                        }
                    }

                    VerificationTab.MANUAL -> {
                        itemsIndexed(uiState.manualClaimRows, key = { _, row -> row.id }) { _, row ->
                            ManualClaimRowCard(
                                row = row,
                                canRemove = uiState.manualClaimRows.size > 1,
                                onNameChange = { viewModel.onManualRowNameChanged(row.id, it) },
                                onTypeChange = { viewModel.onManualRowTypeChanged(row.id, it) },
                                onConstraintChange = { viewModel.onManualRowConstraintChanged(row.id, it) },
                                onPredicateOperatorChange = {
                                    viewModel.onManualRowPredicateOperatorChanged(row.id, it)
                                },
                                onPredicateValueChange = {
                                    viewModel.onManualRowPredicateValueChanged(row.id, it)
                                },
                                onRemove = { viewModel.removeManualRow(row.id) },
                            )
                        }

                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = viewModel::addManualRow,
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Add row")
                                }
                                Button(
                                    onClick = viewModel::sendManual,
                                    enabled = !uiState.isLoading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Send request")
                                }
                            }
                        }
                    }

                    VerificationTab.SERVER_HTTP -> {
                        item {
                            ServerHttpConnectionCard(
                                uiState = uiState,
                                onBaseUrlChange = viewModel::onServerBaseUrlChanged,
                                onApiKeyChange = viewModel::onServerApiKeyChanged,
                                onCredentialDefinitionIdChange = viewModel::onServerCredentialDefinitionIdChanged,
                                onProofRequestNameChange = viewModel::onServerProofRequestNameChanged,
                                onLoadCredentialDefinitions = viewModel::loadServerCredentialDefinitions,
                                onCredentialDefinitionSelected = viewModel::onServerCredentialDefinitionSelected,
                            )
                        }

                        itemsIndexed(uiState.serverSchemaClaimRows, key = { _, row -> row.id }) { _, row ->
                            ServerSchemaClaimRowCard(
                                row = row,
                                onCheckedChange = {
                                    viewModel.onServerSchemaRowChecked(row.id, it)
                                },
                                onConstraintChange = {
                                    viewModel.onServerSchemaRowConstraintChanged(row.id, it)
                                },
                                onPredicateOperatorChange = {
                                    viewModel.onServerSchemaRowPredicateOperatorChanged(row.id, it)
                                },
                                onPredicateValueChange = {
                                    viewModel.onServerSchemaRowPredicateValueChanged(row.id, it)
                                },
                            )
                        }

                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Button(
                                    onClick = viewModel::sendServerProofRequest,
                                    enabled = !uiState.isLoading && uiState.selectedServerCredentialDefinition != null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Create proof invitation")
                                }

                                if (uiState.serverInvitationUrl.isNotBlank()) {
                                    ProofInvitationQrCode(invitationUrl = uiState.serverInvitationUrl)
                                }

                                if (uiState.serverResult.isNotBlank() && uiState.serverInvitationUrl.isBlank()) {
                                    Text(uiState.serverResult)
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun VerificationResultCard(result: VerificationResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (result.isValid) "Verification successful" else "Verification failed",
                style = MaterialTheme.typography.titleMedium,
            )
            if (result.attributes.isEmpty()) {
                Text(text = "Message ID: ${result.messageId}")
            } else {
                result.attributes.forEach { (name, value) ->
                    Text(text = "$name: $value")
                }
            }
        }
    }
}

@Composable
private fun ServerHttpConnectionCard(
    uiState: VerificationRequestUiState,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onCredentialDefinitionIdChange: (String) -> Unit,
    onProofRequestNameChange: (String) -> Unit,
    onLoadCredentialDefinitions: () -> Unit,
    onCredentialDefinitionSelected: (CloudAgentCredentialDefinition) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = uiState.serverBaseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloud agent URL") },
                placeholder = { Text("http://10.0.2.2:8085") },
            )

            OutlinedTextField(
                value = uiState.serverApiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
            )

            OutlinedTextField(
                value = uiState.serverCredentialDefinitionId,
                onValueChange = onCredentialDefinitionIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Credential definition ID override") },
                placeholder = { Text("Auto: {cloud-agent}/credential-definition-registry/definitions/{guid}/definition") },
            )

            Button(
                onClick = onLoadCredentialDefinitions,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Load credential definitions")
            }

            ServerCredentialDefinitionDropdown(
                credentialDefinitions = uiState.serverCredentialDefinitions,
                selectedCredentialDefinition = uiState.selectedServerCredentialDefinition,
                onCredentialDefinitionSelected = onCredentialDefinitionSelected,
            )

            OutlinedTextField(
                value = uiState.serverProofRequestName,
                onValueChange = onProofRequestNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Proof request name") },
            )
        }
    }
}

@Composable
private fun ProofInvitationQrCode(invitationUrl: String) {
    val qrBitmap = remember(invitationUrl) {
        Logger.d("QrCodeCreate") { "QR code created" }
        createQrBitmap(invitationUrl)
    }


    Image(
        bitmap = qrBitmap.asImageBitmap(),
        contentDescription = "Proof invitation QR code",
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun ServerCredentialDefinitionDropdown(
    credentialDefinitions: List<CloudAgentCredentialDefinition>,
    selectedCredentialDefinition: CloudAgentCredentialDefinition?,
    onCredentialDefinitionSelected: (CloudAgentCredentialDefinition) -> Unit,
) {
    SelectorField(
        label = "Credential definition",
        value = selectedCredentialDefinition?.let { credentialDefinition ->
            listOf(credentialDefinition.name, credentialDefinition.version, credentialDefinition.tag)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { credentialDefinition.guid.ifBlank { credentialDefinition.id } }
        }.orEmpty(),
        options = credentialDefinitions,
        optionLabel = { credentialDefinition ->
            listOf(credentialDefinition.name, credentialDefinition.version, credentialDefinition.tag)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { credentialDefinition.guid.ifBlank { credentialDefinition.id } }
        },
        onOptionSelected = onCredentialDefinitionSelected,
    )
}

@Composable
private fun ContactDropdown(
    contacts: List<Contact>,
    selectedContact: Contact?,
    onContactSelected: (Contact) -> Unit,
) {
    SelectorField(
        label = "Contact",
        value = selectedContact?.holder.orEmpty(),
        options = contacts,
        optionLabel = { contact -> contact.holder },
        onOptionSelected = onContactSelected,
    )
}

@Composable
private fun CredentialDropdown(
    credentials: List<Credential>,
    selectedCredential: Credential?,
    onCredentialSelected: (Credential) -> Unit,
) {
    SelectorField(
        label = "Credential",
        value = selectedCredential?.subject ?: "",
        options = credentials,
        optionLabel = { credential -> credential.subject ?: credential.id },
        onOptionSelected = onCredentialSelected,
    )
}

@Composable
private fun ClaimItemCard(
    item: ClaimCheckItem,
    onCheckedChange: (Boolean) -> Unit,
    onConstraintChange: (String) -> Unit,
    onPredicateOperatorChange: (PredicateOperator?) -> Unit,
    onPredicateValueChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                FilterChip(
                    selected = item.checked,
                    onClick = { onCheckedChange(!item.checked) },
                    label = { Text(if (item.checked) "Included" else "Excluded") },
                )
            }

            Text(text = "Type: ${item.type}")

            if (item.checked) {
                if (item.type == ClaimType.NUMBER) {
                    PredicateEditor(
                        selectedOperator = item.predicateOperator,
                        predicateValue = item.predicateValue,
                        onOperatorSelected = onPredicateOperatorChange,
                        onValueChanged = onPredicateValueChange,
                    )
                } else {
                    OutlinedTextField(
                        value = item.constraint.orEmpty(),
                        onValueChange = onConstraintChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Constraint") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualClaimRowCard(
    row: ManualClaimRow,
    canRemove: Boolean,
    onNameChange: (String) -> Unit,
    onTypeChange: (ClaimType) -> Unit,
    onConstraintChange: (String) -> Unit,
    onPredicateOperatorChange: (PredicateOperator?) -> Unit,
    onPredicateValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = row.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Claim name") },
            )

            ClaimTypeDropdown(
                selectedType = row.type,
                onTypeSelected = onTypeChange,
            )

            if (row.type == ClaimType.NUMBER) {
                PredicateEditor(
                    selectedOperator = row.predicateOperator,
                    predicateValue = row.predicateValue,
                    onOperatorSelected = onPredicateOperatorChange,
                    onValueChanged = onPredicateValueChange,
                )
            } else {
                OutlinedTextField(
                    value = row.constraint,
                    onValueChange = onConstraintChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Constraint") },
                )
            }

            if (canRemove) {
                Button(onClick = onRemove) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
private fun ServerSchemaClaimRowCard(
    row: ServerSchemaClaimRow,
    onCheckedChange: (Boolean) -> Unit,
    onConstraintChange: (String) -> Unit,
    onPredicateOperatorChange: (PredicateOperator?) -> Unit,
    onPredicateValueChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = row.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(text = "Schema attribute: ${row.attrName}")
                    Text(text = "Expected type: ${row.valueType.label}")
                }
                Checkbox(
                    checked = row.checked,
                    onCheckedChange = onCheckedChange,
                )
            }

            if (row.checked) {
                if (row.valueType == ServerSchemaClaimValueType.NUMBER || row.valueType == ServerSchemaClaimValueType.DATE) {
                    PredicateEditor(
                        selectedOperator = row.predicateOperator,
                        predicateValue = row.predicateValue,
                        valueLabel = if (row.valueType == ServerSchemaClaimValueType.DATE) {
                            "Predicate value (yyyy-MM-dd)"
                        } else {
                            "Predicate value"
                        },
                        onOperatorSelected = onPredicateOperatorChange,
                        onValueChanged = onPredicateValueChange,
                    )
                    OutlinedTextField(
                        value = row.constraint,
                        onValueChange = onConstraintChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (row.valueType == ServerSchemaClaimValueType.DATE) {
                                    "Reveal constraint (yyyy-MM-dd)"
                                } else {
                                    "Reveal constraint (number)"
                                },
                            )
                        },
                    )
                } else {
                    OutlinedTextField(
                        value = row.constraint,
                        onValueChange = onConstraintChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                when (row.valueType) {
                                    ServerSchemaClaimValueType.STRING -> "Constraint"
                                    ServerSchemaClaimValueType.BOOLEAN -> "Constraint (true/false)"
                                    ServerSchemaClaimValueType.DATE -> "Constraint (yyyy-MM-dd)"
                                    ServerSchemaClaimValueType.NUMBER -> "Constraint"
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaimTypeDropdown(
    selectedType: ClaimType,
    onTypeSelected: (ClaimType) -> Unit,
) {
    SelectorField(
        label = "Claim type",
        value = selectedType.name,
        options = ClaimType.entries,
        optionLabel = { claimType -> claimType.name },
        onOptionSelected = onTypeSelected,
    )
}

@Composable
private fun PredicateEditor(
    selectedOperator: PredicateOperator?,
    predicateValue: String,
    valueLabel: String = "Value",
    onOperatorSelected: (PredicateOperator?) -> Unit,
    onValueChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SelectorField(
            label = "Operator",
            value = selectedOperator?.symbol.orEmpty(),
            options = PredicateOperator.entries,
            optionLabel = { operator -> operator.symbol },
            onOptionSelected = { operator -> onOperatorSelected(operator) },
        )

        OutlinedTextField(
            value = predicateValue,
            onValueChange = onValueChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(valueLabel) },
        )
    }
}

@Composable
private fun <T> SelectorField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onOptionSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            label = { Text(label) },
        )

        Button(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Select")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
