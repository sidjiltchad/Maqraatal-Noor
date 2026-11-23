package org.maqraatalnoor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "maqraatalnoor_settings")

/**
 * Local-only settings: Mushaf bookmark, follow-the-teacher switch, and the
 * id of a font the user imported by hand on this device (see fonts/).
 * Nothing leaves the device; there is no account and no server yet.
 */
class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val bookmarkPage: Flow<Int> = store.data.map { it[KEY_BOOKMARK_PAGE] ?: 1 }
    val bookmarkAyah: Flow<Int> = store.data.map { it[KEY_BOOKMARK_AYAH] ?: 0 }
    val followTeacher: Flow<Boolean> = store.data.map { it[KEY_FOLLOW_TEACHER] ?: true }
    val selectedFontId: Flow<String> = store.data.map { it[KEY_FONT_ID] ?: FontLoaderDefaultId }

    suspend fun saveBookmark(page: Int, ayah: Int) {
        store.edit {
            it[KEY_BOOKMARK_PAGE] = page
            it[KEY_BOOKMARK_AYAH] = ayah
        }
    }

    suspend fun setFollowTeacher(enabled: Boolean) {
        store.edit { it[KEY_FOLLOW_TEACHER] = enabled }
    }

    suspend fun setSelectedFontId(fontId: String) {
        store.edit { it[KEY_FONT_ID] = fontId }
    }

    companion object {
        const val FontLoaderDefaultId = "default"
        private val KEY_BOOKMARK_PAGE = intPreferencesKey("bookmark_page")
        private val KEY_BOOKMARK_AYAH = intPreferencesKey("bookmark_ayah")
        private val KEY_FOLLOW_TEACHER = booleanPreferencesKey("follow_teacher")
        private val KEY_FONT_ID = stringPreferencesKey("selected_font_id")
    }
}
