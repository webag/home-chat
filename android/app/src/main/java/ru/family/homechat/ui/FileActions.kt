package ru.family.homechat.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import ru.family.homechat.data.Api
import ru.family.homechat.data.FileRef
import ru.family.homechat.data.Message
import java.io.File

object FileActions {
    fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

    /** Streams the video in whatever player the phone has. */
    fun openVideo(ctx: Context, f: FileRef) {
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(f.url), f.mime.ifBlank { "video/*" })
        try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) { download(ctx, f) }
    }

    /** Saves to the phone's Downloads folder with a system notification. */
    fun download(ctx: Context, f: FileRef) {
        try {
            val req = DownloadManager.Request(Uri.parse(f.url))
                .setTitle(f.name)
                .setMimeType(f.mime)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Семья/" + f.name)
            ctx.getSystemService(DownloadManager::class.java).enqueue(req)
            toast(ctx, "Скачивается в «Загрузки»")
        } catch (e: Exception) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(f.url)))
        }
    }

    fun copy(ctx: Context, text: String) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("text", text))
        toast(ctx, "Скопировано")
    }

    /** Forward to another app: text as text, attachments as real files (not links). */
    suspend fun share(ctx: Context, m: Message) {
        val f = m.file
        val intent = Intent(Intent.ACTION_SEND)
        if (f == null) {
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, m.text)
        } else {
            val dir = File(ctx.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
            val local = File(dir, f.name.ifBlank { "file" })
            try {
                Api.download(f.url, local)
            } catch (e: Exception) {
                toast(ctx, "Не удалось скачать файл"); return
            }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", local)
            intent.type = f.mime.ifBlank { "*/*" }
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            if (m.text.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, m.text)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Переслать"))
    }
}
