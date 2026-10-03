package ru.family.homechat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import ru.family.homechat.BuildConfig
import java.io.File

data class Release(val versionCode: Int, val versionName: String)

/**
 * Updates from GitHub Releases. CI attaches version.json and HomeChat.apk to every release;
 * Android itself rejects an APK signed with a different key, so a tampered file cannot be installed.
 */
object Updater {
    /** Returns the newest release if it is newer than the installed app. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${BuildConfig.UPDATES}/version.json").build()
        Api.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "version.json: ${r.code}")
            val o = JSONObject(r.body!!.string())
            Release(o.getInt("versionCode"), o.getString("versionName"))
                .takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        }
    }

    suspend fun download(ctx: Context, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dest = File(ctx.cacheDir, "update/HomeChat.apk")
        val req = Request.Builder().url("${BuildConfig.UPDATES}/HomeChat.apk").build()
        Api.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "apk: ${r.code}")
            val body = r.body!!
            val total = body.contentLength().coerceAtLeast(1)
            dest.parentFile?.mkdirs()
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        dest
    }

    /**
     * Opens the system installer. The first time Android asks to allow installs from this app:
     * then returns false after opening that settings screen, and the user has to tap install again.
     */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
