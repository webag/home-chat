package ru.family.homechat.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Brand = Color(0xFF4F7DF3)

@Composable
fun HomeChatTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Brand)
        else -> lightColorScheme(primary = Brand)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

fun parseColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF90A4AE))

@Composable
fun Avatar(name: String, color: String?, group: Boolean = false, size: Dp = 48.dp) {
    Box(
        Modifier.size(size).background(if (group) Brand else parseColor(color), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (group) Icon(Icons.Filled.Groups, null, tint = Color.White)
        else Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

private fun sameDay(a: Date, b: Date): Boolean {
    val ca = Calendar.getInstance().apply { time = a }
    val cb = Calendar.getInstance().apply { time = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

fun isSameDay(a: Date?, b: Date?) = a != null && b != null && sameDay(a, b)

fun shortTime(d: Date?): String {
    d ?: return ""
    val pattern = if (sameDay(d, Date())) "HH:mm" else "dd.MM"
    return SimpleDateFormat(pattern, Locale("ru")).format(d)
}

fun clock(d: Date?): String = d?.let { SimpleDateFormat("HH:mm", Locale("ru")).format(it) } ?: ""

fun dayTitle(d: Date): String {
    val now = Date()
    val yesterday = Date(now.time - 24 * 3600 * 1000)
    return when {
        sameDay(d, now) -> "Сегодня"
        sameDay(d, yesterday) -> "Вчера"
        else -> SimpleDateFormat("d MMMM", Locale("ru")).format(d)
    }
}

fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> String.format(Locale("ru"), "%.1f МБ", bytes / 1024.0 / 1024.0)
}
