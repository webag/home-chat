package ru.family.homechat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import ru.family.homechat.data.Member
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun parseColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF90A4AE))

/** Photo if the member has set one; the letter stays underneath while it loads or if it fails. */
@Composable
fun Avatar(name: String, color: String?, group: Boolean = false, size: Dp = 48.dp, url: String? = null) {
    val brush = if (group) BrandGradient else parseColor(color).let { c ->
        Brush.linearGradient(listOf(lerp(c, Color.White, 0.28f), c))
    }
    Box(Modifier.size(size).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        if (group) Icon(Icons.Filled.Groups, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        else Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp)
        if (url != null) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun Avatar(m: Member, size: Dp = 48.dp) = Avatar(m.name, m.color, size = size, url = m.avatarUrl)

/** Russian plural: plural(6, "участник", "участника", "участников"). */
fun plural(n: Int, one: String, few: String, many: String): String {
    val word = when {
        n % 100 in 11..14 -> many
        n % 10 == 1 -> one
        n % 10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
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
