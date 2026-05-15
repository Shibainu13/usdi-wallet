package com.dev.usdi_wallet.ui.main

import android.content.Intent
import co.touchlab.kermit.Logger
import com.dev.usdi_wallet.domain.contact.ContactManager
import com.dev.usdi_wallet.domain.protocol.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DeepLinkRouter(
    private val protocols: List<Protocol<*, *>>,
    private val scope: CoroutineScope,
) {
    fun handle(intent: Intent) {
        if (Intent.ACTION_VIEW != intent.action) return

        val uri = intent.data ?: return

        Logger.d(DeepLinkRouter::class.toString()) {
            "Handling deep link: $uri"
        }

        routeToContactManager(uri.toString())
    }

    private fun routeToContactManager(uri: String) {
        val protocol = protocols.firstOrNull { protocol ->
            protocol.contactManager.canHandle(uri)
        }

        if (protocol == null) {
            Logger.w(DeepLinkRouter::class.toString()) {
                "No contact protocol found for $uri"
            }
            return
        }

        Logger.d(DeepLinkRouter::class.toString()) {
            "Routing $uri to ${protocol::class.simpleName}"
        }

        scope.launch {
            protocol.contactManager.parseInvitation(uri)
        }
    }
}