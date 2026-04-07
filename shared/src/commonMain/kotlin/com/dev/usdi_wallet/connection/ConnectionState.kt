package com.dev.usdi_wallet.connection

enum class ConnectionState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR,
}