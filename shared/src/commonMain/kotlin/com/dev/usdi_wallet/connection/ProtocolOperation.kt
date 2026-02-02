package com.dev.usdi_wallet.connection

sealed interface ProtocolOperation {
    val input: String

    data class EstablishConnection(override val input: String) : ProtocolOperation
    data class ReceiveCredential(override val input: String) : ProtocolOperation
    data class PresentProof(override val input: String) : ProtocolOperation
    data class VerifyProof(override val input: String) : ProtocolOperation
}