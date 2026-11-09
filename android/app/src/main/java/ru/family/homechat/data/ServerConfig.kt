package ru.family.homechat.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import ru.family.homechat.App
import ru.family.homechat.BuildConfig

/**
 * Address of our server. The VPS changes its IP from time to time, so the current address lives in
 * server.json in the GitHub repo: editing that file switches every installed app, no new release needed.
 * The last known address is kept on the phone; BuildConfig.SERVER is only the first-run default.
 */
object ServerConfig {
    private val prefs get() = App.instance.getSharedPreferences("server", Context.MODE_PRIVATE)
    private val _url = MutableStateFlow(prefs.getString("url", null) ?: BuildConfig.SERVER)
    val flow: StateFlow<String> = _url
    val url: String get() = _url.value

    /** Re-reads server.json; returns true if the address changed. Network errors keep the current one. */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val fresh = runCatching {
            Api.http.newCall(Request.Builder().url(BuildConfig.SERVER_CONFIG).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                JSONObject(r.body!!.string()).optString("server").trimEnd('/')
            }
        }.getOrNull()
        if (fresh == null || !fresh.startsWith("https://") || fresh.length > 200 || fresh == url) return@withContext false
        prefs.edit().putString("url", fresh).apply()
        _url.value = fresh
        true
    }
}
