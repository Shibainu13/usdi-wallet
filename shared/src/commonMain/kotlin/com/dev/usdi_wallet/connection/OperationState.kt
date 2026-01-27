package com.dev.usdi_wallet.connection

sealed interface OperationState {
    val logMessage: String

    data object Idle : OperationState {
        override val logMessage: String = "Ready"
    }

    data class InProgress(val status: String) : OperationState {
        override val logMessage: String = status
    }

    data class ConnectionEstablished(
        val connectionId: String,
        val peerName: String? = null,
    ) : OperationState {
        override val logMessage: String = "Connected: ${peerName ?: connectionId.take(20)}"
    }

    data class CredentialReceived(
        val credentialId: String,
        val credentialType: String,
        val issuer: String,
        val claims: Map<String, String>
    ) : OperationState {
        override val logMessage: String = "Received: $credentialType from $issuer"
    }

    data class ProofPresented(
        val presentationId: String,
        val verifier: String,
        val attributes: List<String>
    ) : OperationState {
        override val logMessage: String = "Present proof to $verifier"
    }

    data class ProofVerified(
        val verificationId: String,
        val prover: String,
        val attributes: List<String>,
        val accept: Boolean
    ) : OperationState {
        override val logMessage: String = "${if (accept) "Accept" else "Reject"} proof from $prover"
    }

    data class Error(val reason: String) : OperationState {
        override val logMessage: String = "Error: $reason"
    }
}