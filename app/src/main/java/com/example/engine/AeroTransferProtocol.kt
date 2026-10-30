package com.example.engine

object AeroTransferProtocol {
    const val DEFAULT_PORT = 8829
    const val PC_BRIDGE_PORT = 8844
    const val SOCKET_BUFFER_SIZE = 256 * 1024 // 256 KB
    const val TURBO_CHUNK_SIZE = 64 * 1024 // 64 KB
    const val SOCKET_TIMEOUT_MS = 25000
    const val MAX_CONCURRENT_CLIENTS = 4
    const val PAIRING_CODE_LENGTH = 6
    const val PAIRING_SESSION_TTL_MS = 5L * 60L * 1000L

    const val MAGIC_HEADER: Int = 0x4145524F // "AERO"
    const val PROTOCOL_VERSION: Int = 2

    const val MSG_HANDSHAKE_INIT = 1
    const val MSG_HANDSHAKE_ACK = 2
    const val MSG_TRANSFER_PROPOSAL = 3
    const val MSG_PROPOSAL_RESPONSE = 4
    const val MSG_FILE_HEADER = 5
    const val MSG_FILE_CHUNK = 6
    const val MSG_CHUNK_ACK = 7
    const val MSG_FILE_VERIFY = 8
    const val MSG_SESSION_FINISH = 9
}

data class FileHeader(
    val fileIndex: Int,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val sha256Expected: String
)

data class ResumeAck(
    val fileIndex: Int,
    val resumeOffset: Long,
    val ready: Boolean
)

data class SecureHandshakePayload(
    val deviceName: String,
    val protocolVersion: Int,
    val publicKeyBytes: ByteArray,
    val sessionSalt: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecureHandshakePayload) return false
        return deviceName == other.deviceName &&
                protocolVersion == other.protocolVersion &&
                publicKeyBytes.contentEquals(other.publicKeyBytes) &&
                sessionSalt.contentEquals(other.sessionSalt)
    }

    override fun hashCode(): Int {
        var result = deviceName.hashCode()
        result = 31 * result + protocolVersion
        result = 31 * result + publicKeyBytes.contentHashCode()
        result = 31 * result + sessionSalt.contentHashCode()
        return result
    }
}

data class SecureHandshakeAck(
    val peerName: String,
    val accepted: Boolean,
    val peerPublicKeyBytes: ByteArray,
    val sasVerificationCode: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SecureHandshakeAck) return false
        return peerName == other.peerName &&
                accepted == other.accepted &&
                peerPublicKeyBytes.contentEquals(other.peerPublicKeyBytes) &&
                sasVerificationCode == other.sasVerificationCode
    }

    override fun hashCode(): Int {
        var result = peerName.hashCode()
        result = 31 * result + accepted.hashCode()
        result = 31 * result + peerPublicKeyBytes.contentHashCode()
        result = 31 * result + sasVerificationCode.hashCode()
        return result
    }
}
