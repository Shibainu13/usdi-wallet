package com.dev.usdi_wallet.domain.connection

enum class ConnectionState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR,
}