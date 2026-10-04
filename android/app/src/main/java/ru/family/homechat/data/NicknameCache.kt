package ru.family.homechat.data

import android.content.Context
import org.json.JSONObject
import ru.family.homechat.App

/**
 * Local copy of the viewer's own names for contacts. Push notifications are built in the background
 * from the server's payload, without Firestore, so they read the names from here.
 */
object NicknameCache {
    private val prefs get() = App.instance.getSharedPreferences("nicknames", Context.MODE_PRIVATE)

    fun save(names: Map<String, String>) {
        prefs.edit().putString("names", JSONObject(names).toString()).apply()
    }

    fun get(uid: String): String? = runCatching {
        JSONObject(prefs.getString("names", null) ?: return null).optString(uid).ifBlank { null }
    }.getOrNull()

    fun clear() = prefs.edit().clear().apply()
}
