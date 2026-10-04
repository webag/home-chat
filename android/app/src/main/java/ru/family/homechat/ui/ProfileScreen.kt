package ru.family.homechat.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.family.homechat.data.Api
import ru.family.homechat.data.Media
import java.io.File
import kotlin.math.roundToInt

private const val AVATAR_PX = 512

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by vm.me.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val mine = members.firstOrNull { it.uid == me }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            source = withContext(Dispatchers.IO) { runCatching { Media.decode(ctx, uri, 2048) }.getOrNull() }
            if (source == null) FileActions.toast(ctx, "Не удалось открыть фото")
        }
    }

    fun run(action: suspend () -> Unit) {
        busy = true
        scope.launch {
            try { action() } catch (e: Exception) { FileActions.toast(ctx, "Не получилось, проверь интернет") }
            busy = false
        }
    }

    source?.let { src ->
        AvatarCropper(src, onCancel = { source = null }, onDone = { square ->
            source = null
            run {
                val file = File(ctx.cacheDir, "avatar.webp")
                withContext(Dispatchers.IO) {
                    file.outputStream().use { square.compress(Media.WEBP, Media.WEBP_QUALITY, it) }
                    square.recycle()
                }
                try { Api.setAvatar(file) } finally { file.delete() }
            }
        })
        return
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Профиль") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(contentAlignment = Alignment.Center) {
                mine?.let { Avatar(it, 148.dp) }
                if (busy) Box(Modifier.size(148.dp).background(Color(0x66000000), CircleShape), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(mine?.realName ?: "", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text("Это фото видят все в семье", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text(if (mine?.avatar != null) "Сменить фото" else "Выбрать фото", style = MaterialTheme.typography.titleMedium)
            }
            if (mine?.avatar != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { run { Api.deleteAvatar() } }, enabled = !busy) {
                    Text("Удалить фото", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Full-screen square crop: the photo is panned and pinch-zoomed under a fixed round frame.
 * The image always covers the frame; returns a [AVATAR_PX]-sized square.
 */
@Composable
private fun AvatarCropper(src: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    val image = remember(src) { src.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }
    BackHandler(onBack = onCancel)

    // Frame side in screen px, and the scale at which the image's short side fills the frame.
    fun frame() = (minOf(box.width, box.height) - 2 * margin).coerceAtLeast(1f)
    fun scale() = frame() / minOf(src.width, src.height) * zoom
    fun clamp(o: Offset): Offset {
        val maxX = ((src.width * scale() - frame()) / 2).coerceAtLeast(0f)
        val maxY = ((src.height * scale() - frame()) / 2).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    fun cut(): Bitmap {
        val s = scale()
        val left = box.width / 2f + offset.x - src.width * s / 2
        val top = box.height / 2f + offset.y - src.height * s / 2
        val side = (frame() / s).roundToInt().coerceIn(1, minOf(src.width, src.height))
        val x = (((box.width - frame()) / 2 - left) / s).roundToInt().coerceIn(0, src.width - side)
        val y = (((box.height - frame()) / 2 - top) / s).roundToInt().coerceIn(0, src.height - side)
        val square = Bitmap.createBitmap(src, x, y, side, side)
        return Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true).also { if (it != square) square.recycle() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            Modifier.fillMaxSize().onSizeChanged { box = it }.pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                    offset = clamp(offset + pan)
                }
            },
        ) {
            val s = scale()
            val w = src.width * s
            val h = src.height * s
            drawImage(
                image,
                dstOffset = IntOffset((size.width / 2 + offset.x - w / 2).roundToInt(), (size.height / 2 + offset.y - h / 2).roundToInt()),
                dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            val r = frame() / 2
            clipPath(Path().apply { addOval(Rect(center, r)) }, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }
            drawCircle(Color.White.copy(alpha = 0.9f), radius = r, style = Stroke(2.dp.toPx()))
        }
        Text(
            "Подвинь и увеличь фото", color = Color.White, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp),
        )
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) { Text("Отмена", color = Color.White) }
            Button(onClick = { onDone(cut()) }) { Text("Готово") }
        }
    }
}
