package com.dev.usdi_wallet.protocol

import com.dev.usdi_wallet.connection.ConnectionManager
import com.dev.usdi_wallet.contact.Contact
import com.dev.usdi_wallet.contact.ContactManager
import com.dev.usdi_wallet.credential.Credential
import com.dev.usdi_wallet.credential.CredentialManager
import com.dev.usdi_wallet.message.Message
import kotlin.reflect.KClass

abstract class Protocol<MessageType> {
    abstract val protocolId: String
    abstract val connectionManager: ConnectionManager<MessageType>
    abstract val contactManager: ContactManager
    abstract val credentialManager: CredentialManager
    abstract suspend fun start()
    abstract suspend fun handleInbound(message: MessageType)
    abstract suspend fun handleOutbound(message: MessageType)
    abstract fun toUiMessage(message: MessageType): Message

    companion object {
        private val instances = mutableMapOf<KClass<out Protocol<*>>, Protocol<*>>()

        fun <T : Protocol<*>> getInstance(type: KClass<T>): T? = instances[type] as? T

        fun <T : Protocol<*>> register(instance: T): T {
            val type = instance::class
            check(!instances.containsKey(type)) {
                "${type.simpleName} is already instantiated. Use getInstance() instead of creating a new one."
            }
            instances[type] = instance
            return instance
        }

        fun <T : Protocol<*>> unregister(type: KClass<T>) {
            instances.remove(type)
        }
    }
}