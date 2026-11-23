package org.maqraatalnoor.fonts

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import java.io.File

/**
 * Manual font import — Thamaniya pattern, as in Miraat. NO font file is
 * bundled in this app, downloaded by it, or requested from anyone.
 *
 * The user downloads the font herself from the official site only
 * (ask.thmanyah.com, by email, OTF) and imports it here (.otf/.ttf via
 * the system file picker) into this app's private files folder. The app
 * then offers the imported fonts plus the bundled default, IBM Plex Sans
 * Arabic (open licence). This app does not distribute Thamaniya and does
 * not host it. The full licence text arrives with the download email and
 * was not read here, so embedding the font in an app build is NOT decided
 * by this code: import is device-local only.
 *
 * Quran text never uses an imported font: it always uses the bundled
 * Noto Naskh Arabic family (see ui/theme).
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
