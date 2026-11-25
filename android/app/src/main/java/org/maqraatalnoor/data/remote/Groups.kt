package org.maqraatalnoor.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** المستويات الثلاثة. الإجازة ليست مستوى، بل تخرّج يمنحه المشرفة العامة. */
enum class HalqaGroup(val key: String, val arabic: String) {
    FOUNDATION("foundation", "التأسيس"),
    TAJWEED("tajweed", "التجويد"),
    HIFZ("hifz", "الحفظ"),
    ;

    companion object {
        fun fromKey(key: String): HalqaGroup? = entries.firstOrNull { it.key == key }
    }
}

/** منح الإجازة (التخرّج) أو إلغاؤها — من المشرفة العامة. */
suspend fun HalqaRemote.setGraduated(userId: String, graduated: Boolean) =
    withContext(Dispatchers.IO) {
        SupabaseApi.patch(
            "halqa_members?user_id=eq.$userId",
            JSONObject().put("graduated", graduated),
            session.accessToken,
        )
    }
