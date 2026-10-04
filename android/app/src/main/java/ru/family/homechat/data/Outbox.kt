package ru.family.homechat.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

const val MAX_FILE_BYTES = 50L * 1024 * 1024

/** Something picked or shared, not yet sent. */
data class Attachment(val uri: Uri, val name: String, val mime: String, val size: Long) {
    val isImage get() = mime.startsWith("image/")
    val isVideo get() = mime.startsWith("video/")
}

/** An attachment currently being uploaded (shown at the bottom of the chat). */
data class Outgoing(val id: String, val chatId: String, val name: String, val progress: Float = 0f, val error: String? = null)

/**
 * Uploads run in an app-wide scope so they finish even if the user leaves the chat screen.
 */
object Outbox {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val items = MutableStateFlow<List<Outgoing>>(emptyList())

    fun send(ctx: Context, chatId: String, peer: String?, text: String, attachments: List<Attachment>) {
        val trimmed = text.trim()
        if (attachments.isEmpty()) {
            if (trimmed.isNotEmpty()) Repo.send(chatId, peer, "text", trimmed, null)
            return
        }
        val app = ctx.applicationContext
        val jobs = attachments.map { Outgoing(UUID.randomUUID().toString(), chatId, it.name) }
        items.update { it + jobs }
        scope.launch {
            attachments.zip(jobs).forEachIndexed { i, (att, job) ->
                val caption = if (i == 0) trimmed else ""
                try {
                    upload(app, chatId, peer, caption, att, job.id)
                    items.update { list -> list.filterNot { it.id == job.id } }
                } catch (e: Exception) {
                    val msg = when {
                        e is TooLargeException -> "Файл больше 50 МБ"
                        e is ApiException && e.code == 413 -> "Файл больше 50 МБ"
                        e is ApiException && e.code == 507 -> "На сервере закончилось место"
                        else -> "Не отправлено: проверьте интернет"
                    }
                    items.update { list -> list.map { if (it.id == job.id) it.copy(error = msg) else it } }
                }
            }
        }
    }

    fun dismiss(id: String) = items.update { list -> list.filterNot { it.id == id } }

    private suspend fun upload(ctx: Context, chatId: String, peer: String?, caption: String, att: Attachment, jobId: String) {
        val p = Media.prepare(ctx, att)
        try {
            val thumbPath = p.thumb?.let { Api.upload(it, "thumb.webp", "image/webp") }
            val path = Api.upload(p.file, p.name, p.mime) { f ->
                items.update { list -> list.map { if (it.id == jobId) it.copy(progress = f) else it } }
            }
            val ref = FileRef(path, p.name, p.file.length(), p.mime, p.width, p.height, thumbPath)
            Repo.send(chatId, peer, p.type, caption, ref)
        } finally {
            p.file.delete(); p.thumb?.delete()
        }
    }
}

class TooLargeException : Exception()

object Media {
    data class Prepared(val file: File, val name: String, val mime: String, val type: String,
                        val width: Int = 0, val height: Int = 0, val thumb: File? = null)

    fun describe(ctx: Context, uri: Uri, fallbackMime: String? = null): Attachment {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }
        val mime = ctx.contentResolver.getType(uri) ?: fallbackMime ?: "application/octet-stream"
        return Attachment(uri, name, mime, size)
    }

    suspend fun prepare(ctx: Context, a: Attachment): Prepared = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "outgoing").apply { mkdirs() }
        when {
            a.isImage && a.mime != "image/gif" -> compressImage(ctx, a, dir)
            a.isVideo -> {
                val f = copy(ctx, a, dir)
                videoInfo(f, dir, a)
            }
            else -> {
                val f = copy(ctx, a, dir)
                Prepared(f, a.name, a.mime, if (a.isImage) "image" else "file")
            }
        }
    }

    private fun copy(ctx: Context, a: Attachment, dir: File): File {
        val out = File(dir, UUID.randomUUID().toString())
        ctx.contentResolver.openInputStream(a.uri)!!.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_FILE_BYTES) { out.delete(); throw TooLargeException() }
                    output.write(buf, 0, n)
                }
            }
        }
        return out
    }

    /** Max 1920px on the long side, WebP 80%; only this compressed copy is uploaded and stored. */
    private fun compressImage(ctx: Context, a: Attachment, dir: File): Prepared {
        // Not decodable (heic on old phones, etc.) — send as is.
        val bmp = decode(ctx, a.uri, 1920) ?: return Prepared(copy(ctx, a, dir), a.name, a.mime, "file")
        val out = File(dir, UUID.randomUUID().toString())
        out.outputStream().use { bmp.compress(WEBP, WEBP_QUALITY, it) }
        val w = bmp.width; val h = bmp.height
        bmp.recycle()
        val name = a.name.substringBeforeLast('.', a.name) + ".webp"
        return Prepared(out, name, "image/webp", "image", w, h)
    }

    /** Decodes an image upright (EXIF rotation applied) with the long side at most [maxSide]; null if undecodable. */
    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds decodeStream always returns null; only the filled-in bounds matter.
        val stream = cr.openInputStream(uri) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            cr.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        }.getOrDefault(0)
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        if (scale < 1f || rotation != 0) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated != bmp) bmp.recycle()
            bmp = rotated
        }
        return bmp
    }

    const val WEBP_QUALITY = 80

    /** Lossy WebP; before Android 11 the old WEBP format is lossy for quality < 100. */
    @Suppress("DEPRECATION")
    val WEBP: Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private fun videoInfo(f: File, dir: File, a: Attachment): Prepared {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val thumb = r.getFrameAtTime(0)?.let { frame ->
                val s = minOf(1f, 480f / maxOf(frame.width, frame.height))
                val small = Bitmap.createScaledBitmap(frame, (frame.width * s).toInt(), (frame.height * s).toInt(), true)
                File(dir, UUID.randomUUID().toString()).also { tf ->
                    tf.outputStream().use { small.compress(WEBP, WEBP_QUALITY, it) }
                }
            }
            Prepared(f, a.name, a.mime, "video", w, h, thumb)
        } catch (e: Exception) {
            Prepared(f, a.name, a.mime, "video")
        } finally {
            r.release()
        }
    }
}
