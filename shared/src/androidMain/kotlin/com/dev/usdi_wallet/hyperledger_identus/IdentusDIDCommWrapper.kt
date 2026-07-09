package com.dev.usdi_wallet.hyperledger_identus

import co.touchlab.kermit.Logger
import didcomm.shaded.nimbusds.jose.shaded.json.JSONObject
import org.didcommx.didcomm.DIDComm
import org.didcommx.didcomm.message.Attachment
import org.didcommx.didcomm.model.UnpackParams
import org.hyperledger.identus.walletsdk.domain.buildingblocks.Apollo
import org.hyperledger.identus.walletsdk.domain.buildingblocks.Castor
import org.hyperledger.identus.walletsdk.domain.buildingblocks.Pluto
import org.hyperledger.identus.walletsdk.domain.models.AttachmentData
import org.hyperledger.identus.walletsdk.domain.models.AttachmentDescriptor
import org.hyperledger.identus.walletsdk.domain.models.DID
import org.hyperledger.identus.walletsdk.domain.models.Message
import org.hyperledger.identus.walletsdk.mercury.ATTACHMENT_SEPARATOR
import org.hyperledger.identus.walletsdk.mercury.BASE64
import org.hyperledger.identus.walletsdk.mercury.DIDCommProtocol
import org.hyperledger.identus.walletsdk.mercury.HASH
import org.hyperledger.identus.walletsdk.mercury.JSON
import org.hyperledger.identus.walletsdk.mercury.LINKS
import org.hyperledger.identus.walletsdk.mercury.resolvers.DIDCommDIDResolver
import org.hyperledger.identus.walletsdk.mercury.resolvers.DIDCommSecretsResolver
import org.hyperledger.identus.walletsdk.mercury.resolvers.DIDCommWrapper
import java.time.Instant

class IdentusDIDCommWrapper(
    castor: Castor,
    pluto: Pluto,
    apollo: Apollo,
) : DIDCommProtocol {
    private val delegate = DIDCommWrapper(castor, pluto, apollo)
    private val didDocResolver = DIDCommDIDResolver(castor)
    private val secretsResolver = DIDCommSecretsResolver(pluto, apollo)
    private val didComm = DIDComm(didDocResolver, secretsResolver)

    override fun packEncrypted(message: Message): String = delegate.packEncrypted(message)

    override fun unpack(message: String): Message {
        val result = didComm.unpack(
            UnpackParams(
                packedMessage = message,
                didDocResolver = didDocResolver,
                secretResolver = secretsResolver,
                expectDecryptByAllKeys = false,
                unwrapReWrappingForward = false,
            )
        )
        val attachments = result.message.attachments
        Logger.i(IdentusDIDCommWrapper::class.toString()) {
            "DIDComm unpacked message type=${result.message.type}, id=${result.message.id}, " +
                "attachment_count=${attachments?.size ?: 0}, attachment_formats=" +
                attachments.orEmpty().mapIndexed { index, attachment ->
                    "#$index id=${attachment.id}, format=${attachment.format}, media_type=${attachment.mediaType}, " +
                        "data_keys=${attachment.data.toJSONObject().keys}"
                }
        }

        return Message(
            id = result.message.id,
            piuri = result.message.type,
            from = result.message.from?.let { DID(it) },
            to = result.message.to?.firstOrNull()?.let { DID(it) },
            fromPrior = result.message.fromPrior?.toString(),
            body = result.message.body.toString(),
            thid = result.message.thid,
            pthid = result.message.pthid,
            ack = result.message.ack?.let { arrayOf(it) } ?: emptyArray(),
            createdTime = result.message.createdTime?.toString() ?: Instant.now().toEpochMilli().toString(),
            attachments = parseAttachmentsToDomain(attachments),
        )
    }

    private fun parseAttachmentsToDomain(attachments: List<Attachment>?): Array<AttachmentDescriptor> {
        return attachments.orEmpty().mapNotNull { attachment ->
            runCatching {
                AttachmentDescriptor(
                    id = attachment.id,
                    data = parseAttachmentDataToDomain(attachment.data),
                    byteCount = attachment.byteCount?.toInt(),
                    description = attachment.description,
                    filename = attachment.filename?.split(ATTACHMENT_SEPARATOR)?.toTypedArray(),
                    format = attachment.format,
                    lastModTime = attachment.lastModTime?.toString(),
                    mediaType = attachment.mediaType,
                )
            }.getOrElse {
                Logger.w(IdentusDIDCommWrapper::class.toString()) {
                    "Dropped DIDComm attachment id=${attachment.id}, format=${attachment.format}, " +
                        "media_type=${attachment.mediaType}: ${it.message}"
                }
                null
            }
        }.toTypedArray()
    }

    private fun parseAttachmentDataToDomain(data: Attachment.Data): AttachmentData {
        val jsonObj = data.toJSONObject()
        val base64 = jsonObj[BASE64]
        if (base64 is String) {
            return AttachmentData.AttachmentBase64(base64)
        }

        val json = jsonObj[JSON]
        if (json != null) {
            return AttachmentData.AttachmentJsonData(jsonValueToString(json))
        }

        val links = jsonObj[LINKS]
        val hash = jsonObj[HASH]
        val linkArray = when (links) {
            is Array<*> -> links.filterIsInstance<String>().toTypedArray()
            is List<*> -> links.filterIsInstance<String>().toTypedArray()
            else -> emptyArray()
        }
        if (linkArray.isNotEmpty() && hash is String) {
            return AttachmentData.AttachmentLinkData(linkArray, hash)
        }

        throw IllegalArgumentException("Unknown DIDComm attachment data shape: ${jsonObj.keys}")
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonValueToString(value: Any): String {
        return when (value) {
            is JSONObject -> JSONObject.toJSONString(value as Map<String, *>)
            is Map<*, *> -> JSONObject.toJSONString(
                value.entries.associate { it.key.toString() to it.value }
            )
            is String -> value
            else -> value.toString()
        }
    }
}
