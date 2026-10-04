package ru.family.homechat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import ru.family.homechat.BuildConfig
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/** Talks to our own server: member list, file upload/download, push trigger. */
object Api {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private suspend fun bearer(): String {
        val user = Repo.auth.currentUser ?: throw ApiException(401, "not signed in")
        return "Bearer " + user.getIdToken(false).await().token
    }

    suspend fun members(): List<Member> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${BuildConfig.SERVER}/members").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "members: ${r.code}")
            val arr = JSONArray(r.body!!.string())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Member(o.getString("uid"), o.getString("name"), o.getString("email"), o.optString("color", "#90A4AE"),
                    o.optString("avatar").ifBlank { null })
            }
        }
    }

    /** Uploads a file and returns its server path (used in [FileRef.path]). */
    suspend fun upload(file: File, name: String, mime: String, onProgress: (Float) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            val body = ProgressBody(file, mime.toMediaTypeOrNull(), onProgress)
            val req = Request.Builder()
                .url("${BuildConfig.SERVER}/upload?name=" + URLEncoder.encode(name, "UTF-8"))
                .header("Authorization", bearer())
                .post(body)
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw ApiException(r.code, "upload: ${r.code}")
                JSONObject(r.body!!.string()).getString("path")
            }
        }

    /** Uploads a square WebP; the server stores it and updates users/{uid}.avatar itself. */
    suspend fun setAvatar(file: File) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("${BuildConfig.SERVER}/avatar")
            .header("Authorization", bearer())
            .post(file.readBytes().toRequestBody("image/webp".toMediaTypeOrNull()))
            .build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw ApiException(r.code, "avatar: ${r.code}") }
    }

    suspend fun deleteAvatar() = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${BuildConfig.SERVER}/avatar").header("Authorization", bearer()).delete().build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) throw ApiException(r.code, "avatar: ${r.code}") }
    }

    /** Returns HTTP status code. */
    suspend fun notify(chatId: String, messageId: String): Int = withContext(Dispatchers.IO) {
        val json = JSONObject().put("chatId", chatId).put("messageId", messageId).toString()
        val req = Request.Builder()
            .url("${BuildConfig.SERVER}/notify")
            .header("Authorization", bearer())
            .post(json.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        http.newCall(req).execute().use { it.code }
    }

    suspend fun download(url: String, dest: File) = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "download: ${r.code}")
            dest.parentFile?.mkdirs()
            dest.outputStream().use { r.body!!.byteStream().copyTo(it) }
        }
    }

    private class ProgressBody(val file: File, val type: MediaType?, val onProgress: (Float) -> Unit) : RequestBody() {
        override fun contentType() = type
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            val total = file.length().coerceAtLeast(1)
            var sent = 0L
            file.source().use { src ->
                while (true) {
                    val n = src.read(sink.buffer, 64 * 1024)
                    if (n == -1L) break
                    sink.flush()
                    sent += n
                    onProgress(sent.toFloat() / total)
                }
            }
        }
    }
}
