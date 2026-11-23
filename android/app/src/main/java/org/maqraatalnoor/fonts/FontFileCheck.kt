package org.maqraatalnoor.fonts

/**
 * Pure font-file header check (no Android types, unit-tested).
 * Used before accepting a user-imported font file. It only recognises the
 * OTF/TTF signatures; it never downloads or bundles any font.
 */
object FontFileCheck {

    fun looksLikeFontFile(header: ByteArray): Boolean {
        if (header.size < 4) return false
        val otf = header[0] == 'O'.code.toByte() && header[1] == 'T'.code.toByte() &&
            header[2] == 'T'.code.toByte() && header[3] == 'O'.code.toByte()
        val ttf = header[0] == 0x00.toByte() && header[1] == 0x01.toByte() &&
            header[2] == 0x00.toByte() && header[3] == 0x00.toByte()
        val trueType = header.copyOf(4).contentEquals("true".toByteArray(Charsets.US_ASCII))
        return otf || ttf || trueType
    }
}
