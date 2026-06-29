package com.dev.usdi_wallet.domain.protocol

import com.dev.usdi_wallet.domain.auth.WalletAuthManager
import com.dev.usdi_wallet.domain.backup.WalletBackupManager
import com.dev.usdi_wallet.domain.connection.ConnectionManager
import com.dev.usdi_wallet.domain.contact.ContactManager
import com.dev.usdi_wallet.domain.credential.CredentialManager
import com.dev.usdi_wallet.domain.message.Message
import com.dev.usdi_wallet.domain.verification.VerificationManager
import kotlin.reflect.KClass

abstract class Protocol<CredentialType, MessageType> {
    abstract val protocolId: String
    abstract val connectionManager: ConnectionManager<MessageType>
    abstract val contactManager: ContactManager
    abstract val credentialManager: CredentialManager<CredentialType, MessageType>
    open val verificationManager: VerificationManager? = null
    open val walletAuthManager: WalletAuthManager? = null
    open val walletBackupManager: WalletBackupManager? = null
    abstract suspend fun startConnection()
    abstract fun toUiMessage(message: MessageType): Message
    open fun onActivityStart() = Unit
    open fun onActivityStop() = Unit
    companion object {
        private val instances = mutableMapOf<KClass<out Protocol<*,*>>, Protocol<*,*>>()

        fun <T : Protocol<*,*>> getInstance(type: KClass<T>): T? = instances[type] as? T

        fun <T : Protocol<*,*>> register(instance: T): T {
            val type = instance::class
            check(!instances.containsKey(type)) {
                "${type.simpleName} is already instantiated. Use getInstance() instead of creating a new one."
            }
            instances[type] = instance
            return instance
        }

        fun <T : Protocol<*,*>> unregister(type: KClass<T>) {
            instances.remove(type)
        }
    }
}