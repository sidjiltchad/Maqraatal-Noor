package org.maqraatalnoor.fonts

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import androidx.compose.ui.text.font.FontFamily
import java.nio.ByteBuffer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Bundled default interface family: Thmanyah Sans.
 *
 * The three official OTF files are kept only as AES-256-GCM encrypted assets
 * under assets/font_payload. At runtime they are decrypted in memory, copied
 * into direct [ByteBuffer]s, and turned into a native [PlatformFontFamily] so
 * each face keeps its own weight metadata (regular, medium, bold). Each
 * plaintext byte array is zeroed as soon as it has been copied into its direct
 * buffer; nothing is written back to disk. The key and the font bytes are
 * never logged or printed.
 *
 * Callers must fall back to the bundled IBM Plex family when [load] returns
 * null. A user-imported interface font still wins over this default.
 */
object ThmanyahSans {

    private const val ASSET_DIR = "font_payload"
    private const val ASSET_REGULAR = "thmanyah-sans-regular.bin"
    private const val ASSET_MEDIUM = "thmanyah-sans-medium.bin"
    private const val ASSET_BOLD = "thmanyah-sans-bold.bin"

    private val ASSET_NAMES = listOf(ASSET_REGULAR, ASSET_MEDIUM, ASSET_BOLD)

    // Envelope: 4-byte magic "MNF1", 12-byte nonce, 16-byte GCM tag, ciphertext.
    private const val MAGIC = "MNF1"
    private const val NONCE_OFFSET = 4
    private const val NONCE_SIZE = 12
    private const val TAG_OFFSET = NONCE_OFFSET + NONCE_SIZE
    private const val TAG_SIZE = 16
    private const val HEADER_SIZE = TAG_OFFSET + TAG_SIZE
    private const val GCM_TAG_BITS = TAG_SIZE * 8

    // AES-256 key for the bundled encrypted payload. Never log or print it.
    private const val PAYLOAD_KEY_BASE64 =
        "fo9G7T6RqVN1wGbtJSMe9mOWoq6ikGBax28Rk2AetV8="

    @Volatile
    private var cached: FontFamily? = null

    // Keeps the Java FontFamily -> Font -> direct ByteBuffer chain reachable
    // for as long as the cached Compose family can be used.
    @Volatile
    private var retainedFamily: PlatformFontFamily? = null

    /**
     * Returns the bundled Thmanyah Sans family, or null when a payload is
     * missing/corrupt or the platform cannot construct the family. The result
     * is cached for the process lifetime. The direct buffers stay alive with
     * the native fonts because the Typeface reads from them.
     */
    fun load(context: Context): FontFamily? {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                build(context.applicationContext)
            }.getOrNull()?.also { cached = it }
        }
    }

    private fun build(context: Context): FontFamily {
        val platformFonts = ASSET_NAMES.map { assetName ->
            buildNativeFont(context, assetName)
        }
        val nativeFamily = PlatformFontFamily.Builder(platformFonts[0])
            .addFont(platformFonts[1])
            .addFont(platformFonts[2])
            .build()
        val typeface = Typeface.CustomFallbackBuilder(nativeFamily)
            .setSystemFallback("sans-serif")
            .build()
        retainedFamily = nativeFamily
        return FontFamily(typeface)
    }

    private fun buildNativeFont(context: Context, assetName: String): PlatformFont {
        val plaintext = decryptAsset(context, assetName)
        val buffer = ByteBuffer.allocateDirect(plaintext.size)
        try {
            buffer.put(plaintext)
            buffer.flip()
        } finally {
            plaintext.fill(0)
        }
        try {
            return PlatformFont.Builder(buffer).build()
        } catch (t: Throwable) {
            // The Typeface does not own this buffer, so do not leave plaintext in it.
            buffer.clear()
            while (buffer.hasRemaining()) buffer.put(0)
            throw t
        }
    }

    private fun decryptAsset(context: Context, assetName: String): ByteArray {
        val envelope = context.assets.open("$ASSET_DIR/$assetName").use { it.readBytes() }
        try {
            require(envelope.size > HEADER_SIZE) { "Font payload is truncated" }
            require(
                envelope[0] == MAGIC[0].code.toByte() &&
                    envelope[1] == MAGIC[1].code.toByte() &&
                    envelope[2] == MAGIC[2].code.toByte() &&
                    envelope[3] == MAGIC[3].code.toByte()
            ) { "Font payload has an unexpected header" }

            val nonce = envelope.copyOfRange(NONCE_OFFSET, TAG_OFFSET)
            // GCM expects ciphertext followed by the tag, while the envelope
            // stores the tag before the ciphertext.
            val ciphertextSize = envelope.size - HEADER_SIZE
            val cipherInput = ByteArray(ciphertextSize + TAG_SIZE)
            envelope.copyInto(cipherInput, 0, HEADER_SIZE, envelope.size)
            envelope.copyInto(cipherInput, ciphertextSize, TAG_OFFSET, HEADER_SIZE)

            val key = Base64.getDecoder().decode(PAYLOAD_KEY_BASE64)
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, nonce),
                )
                // AAD is the UTF-8 asset file name exactly, without the folder.
                cipher.updateAAD(assetName.toByteArray(Charsets.UTF_8))
                return cipher.doFinal(cipherInput)
            } finally {
                key.fill(0)
                cipherInput.fill(0)
            }
        } finally {
            envelope.fill(0)
        }
    }
}
