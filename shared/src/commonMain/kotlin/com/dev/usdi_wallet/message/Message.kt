package com.dev.usdi_wallet.message

data class Message(
    val id: String,
    val type: String,
    val from: String? = null,
    val to: String? = null,
    val status: String? = null,
    val raw: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Message

        if (id != other.id) return false
        if (type != other.type) return false
        if (from != other.from) return false
        if (to != other.to) return false
        if (status != other.status) return false
        if (raw != other.raw) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (from?.hashCode() ?: 0)
        result = 31 * result + (to?.hashCode() ?: 0)
        result = 31 * result + (status?.hashCode() ?: 0)
        result = 31 * result + raw.hashCode()
        return result
    }
}