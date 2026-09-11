package com.example.flipperdroid.redteam

data class FuzzPayload(
    val name: String,
    val description: String,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FuzzPayload

        if (name != other.name) return false
        if (description != other.description) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

object FuzzPayloadLibrary {
    fun getPayloads(): List<FuzzPayload> = listOf(
        FuzzPayload("Empty", "Null byte array", byteArrayOf()),
        FuzzPayload("Max Int16", "0xFFFF", byteArrayOf(0xFF.toByte(), 0xFF.toByte())),
        FuzzPayload("Max Int32", "0xFFFFFFFF", byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())),
        FuzzPayload("Buffer Overflow 64", "64 bytes of 0x41", ByteArray(64) { 0x41 }),
        FuzzPayload("Buffer Overflow 256", "256 bytes of 0x41", ByteArray(256) { 0x41 }),
        FuzzPayload("Buffer Overflow 512", "512 bytes of 0x42", ByteArray(512) { 0x42 }),
        FuzzPayload("Format String", "%x%x%x%x%x", "%x%x%x%x%x".toByteArray()),
        FuzzPayload("SQL Injection", "' OR '1'='1", "' OR '1'='1".toByteArray()),
        FuzzPayload("Command Injection", "; cat /etc/passwd", "; cat /etc/passwd".toByteArray()),
        FuzzPayload("Unicode BOM", "UTF-8 BOM + test", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "test".toByteArray()),
        FuzzPayload("Long String", "1000 'A' chars", ByteArray(1000) { 0x41 }),
        FuzzPayload("Null Byte Injection", "test\\x00hidden", byteArrayOf(0x74, 0x65, 0x73, 0x74, 0x00, 0x68, 0x69, 0x64, 0x64, 0x65, 0x6E)),
        FuzzPayload("XML Entity", "<!ENTITY xxe SYSTEM \"file:///etc/passwd\">", "<!ENTITY xxe SYSTEM \"file:///etc/passwd\">".toByteArray()),
        FuzzPayload("LDAP Injection", "*", "*".toByteArray()),
        FuzzPayload("Path Traversal", "../../etc/passwd", "../../etc/passwd".toByteArray()),
        FuzzPayload("High Frequency", "Repeated 0xFF", ByteArray(32) { 0xFF.toByte() }),
    )
}
