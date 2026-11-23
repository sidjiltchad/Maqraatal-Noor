package org.maqraatalnoor.data

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * The two mushafs bundled with the app, page by page as images.
 *
 * Each mushaf is a folder under `assets/` holding `p0001.webp … pNNNN.webp`
 * and an `index.json` with the title, the page count and the 114 surah
 * starting pages:
 *
 * - `mushaf_reading` — مصحف القراءة (مصحف قطر، حفص عن عاصم)
 * - `mushaf_tajweed` — مصحف التجويد (مصحف المعلم، حفص عن عاصم)
 *
 * The pages come from the two owner-supplied PDFs, rendered once to WebP so
 * the reader is instant and offline; no PDF engine is needed on the device.
 */
class MushafRepository(private val assets: AssetManager) {

    private var cached: List<MushafSource>? = null

    fun sources(): List<MushafSource> {
        cached?.let { return it }
        val built = BUILT_IN.mapNotNull { spec ->
            runCatching {
                val raw = assets.open("${spec.assetDir}/index.json")
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val json = JSONObject(raw)
                val surahsJson: JSONArray = json.getJSONArray("surahs")
                val surahs = (0 until surahsJson.length()).map { i ->
                    val item = surahsJson.getJSONObject(i)
                    MushafSurah(i + 1, item.getString("name"), item.getInt("page"))
                }
                MushafSource(
                    id = spec.id,
                    title = spec.title,
                    assetDir = spec.assetDir,
                    pageCount = json.getInt("pageCount"),
                    surahs = surahs,
                    hizbs = marks(json.optJSONArray("hizbs")),
                    juzs = marks(json.optJSONArray("juzs")),
                )
            }.getOrNull()
        }
        cached = built
        return built
    }

    private fun marks(array: JSONArray?): List<MushafMark> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            MushafMark(item.getInt("number"), item.getInt("page"))
        }
    }

    fun source(id: String): MushafSource? = sources().firstOrNull { it.id == id }

    /** Asset path of one page image. */
    fun pagePath(source: MushafSource, page: Int): String =
        String.format(Locale.US, "%s/p%04d.webp", source.assetDir, page)

    private data class Spec(val id: String, val assetDir: String, val title: String)

    private companion object {
        val BUILT_IN = listOf(
            Spec("reading", "mushaf_reading", "مصحف القراءة"),
            Spec("tajweed", "mushaf_tajweed", "مصحف التجويد"),
        )
    }
}
