package org.maqraatalnoor.fonts

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/**
 * Manual interface-font import, kept for user-supplied faces.
 *
 * The app's default interface family is the bundled Thmanyah Sans payload
 * (see [ThmanyahSans]): three official OTF files stored only as AES-256-GCM
 * encrypted assets, decrypted in memory and never written back to disk. This
 * object deals only with fonts the user imports herself (.otf/.ttf via the
 * system file picker) into this app's private files folder. An imported font
 * replaces the default on the interface only, on the user's own device.
 *
 * Quran text never uses an imported font: it always uses the bundled Noto
 * Naskh Arabic family (see ui/theme).
 */
object FontLoader {
    const val OFFICIAL_SITE = "ask.thmanyah.com"
    const val FONTS_DIR = "fonts"
    const val DEFAULT_FONT_ID = "default"

    data class ImportedFont(val id: String, val displayName: String, val file: File)

    fun fontsDir(context: Context): File =
        File(context.filesDir, FONTS_DIR).also { it.mkdirs() }

    /** Imported font files (.otf/.ttf) in the app's private folder. */
    fun listImported(context: Context): List<ImportedFont> {
        val dir = fontsDir(context)
        return (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && (it.name.endsWith(".otf", true) || it.name.endsWith(".ttf", true)) }
            .sortedBy { it.name }
            .map { ImportedFont(id = it.name, displayName = it.nameWithoutExtension, file = it) }
    }

    /** Copy a user-picked font file (SAF) into the private fonts folder. */
    fun importFont(context: Context, uri: Uri, displayName: String): ImportedFont? {
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._\\- ]"), "_")
            .takeIf { it.endsWith(".otf", true) || it.endsWith(".ttf", true) }
            ?: return null
        val target = File(fontsDir(context), safeName)
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            if (!FontFileCheck.looksLikeFontFile(bytes.copyOf(minOf(4, bytes.size)))) return null
            target.writeBytes(bytes)
            // The file must load as a Typeface, or it is rejected.
            Typeface.createFromFile(target) ?: return null
            ImportedFont(id = target.name, displayName = target.nameWithoutExtension, file = target)
        }.getOrNull()
    }

    fun deleteFont(context: Context, id: String): Boolean {
        val file = File(fontsDir(context), id)
        if (!file.exists() || file.parentFile != fontsDir(context)) return false
        return file.delete()
    }

    /** Typeface for the selected font id, or null for the bundled default. */
    fun typefaceFor(context: Context, fontId: String?): Typeface? {
        if (fontId.isNullOrBlank() || fontId == DEFAULT_FONT_ID) return null
        val file = File(fontsDir(context), fontId)
        if (!file.exists()) return null
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }
}
