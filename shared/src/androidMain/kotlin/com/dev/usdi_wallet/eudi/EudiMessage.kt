package com.dev.usdi_wallet.eudi

import eu.europa.ec.eudi.iso18013.transfer.response.RequestProcessor
import java.util.UUID

sealed class EudiMessage {
    val id: String = UUID.randomUUID().toString()
    val timestamp: Long = System.currentTimeMillis()

    data class CredentialOffer(
        val rawUri: String,
    ) : EudiMessage()

    data class PresentationRequest(
        val processedRequest: RequestProcessor.ProcessedRequest.Success,
    ) : EudiMessage()
}