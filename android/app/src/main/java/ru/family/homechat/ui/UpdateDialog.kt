package ru.family.homechat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.family.homechat.data.Release
import ru.family.homechat.data.Updater
import java.io.File

/** Offer → download with progress → system installer. */
@Composable
fun UpdateDialog(release: Release, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }

    fun install(file: File) {
        if (!Updater.install(ctx, file))
            FileActions.toast(ctx, "Разрешите установку и нажмите «Установить» ещё раз")
    }

    val downloading = progress != null && apk == null
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Доступно обновление") },
        text = {
            Column {
                Text("Версия ${release.versionName}")
                progress?.takeIf { apk == null }?.let {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            val file = apk
            when {
                file != null -> TextButton(onClick = { install(file) }) { Text("Установить") }
                !downloading -> TextButton(onClick = {
                    progress = 0f
                    scope.launch {
                        try {
                            val f = Updater.download(ctx) { p -> progress = p }
                            apk = f
                            install(f)
                        } catch (e: Exception) {
                            progress = null
                            FileActions.toast(ctx, "Не удалось скачать обновление")
                        }
                    }
                }) { Text("Обновить") }
            }
        },
        dismissButton = {
            if (!downloading) TextButton(onClick = onDismiss) { Text("Позже") }
        },
    )
}
