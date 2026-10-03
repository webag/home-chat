package ru.family.homechat.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.family.homechat.R
import ru.family.homechat.data.Api
import ru.family.homechat.data.Member
import ru.family.homechat.data.Repo

@Composable
fun LoginScreen() {
    var members by remember { mutableStateOf<List<Member>?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Member?>(null) }

    LaunchedEffect(reload) {
        loadError = false
        runCatching { Api.members() }.onSuccess { members = it }.onFailure { loadError = true }
    }

    Column(Modifier.fillMaxSize().background(BrandGradient)) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 24.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.size(112.dp))
            Text("Семейный чат", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text("Только для своих", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
        }
        Surface(
            Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val sel = selected
                val list = members
                when {
                    sel != null -> PinForm(sel, onBack = { selected = null })
                    loadError -> {
                        Text("Нет связи с сервером", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { reload++ }) { Text("Повторить") }
                    }
                    list == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                    else -> {
                        Text("Кто ты?", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(20.dp))
                        list.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                row.forEach { m -> MemberTile(m, Modifier.weight(1f)) { selected = m } }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberTile(m: Member, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(m.name, m.color, size = 68.dp)
        Spacer(Modifier.height(8.dp))
        Text(m.name, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PinForm(m: Member, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        if (busy || pin.length < 6) return
        busy = true; error = null
        scope.launch {
            runCatching { Repo.signIn(m.email, pin) }.onFailure {
                val wrongPin = it is com.google.firebase.auth.FirebaseAuthException
                error = if (wrongPin) "Неверный PIN" else "Нет связи, попробуй ещё раз"
                if (wrongPin) pin = ""
                busy = false
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(m.name, m.color, size = 84.dp)
        Spacer(Modifier.height(10.dp))
        Text(m.name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("Введи PIN-код", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        // The field itself is invisible: its decoration draws one dot per digit.
        BasicTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter(Char::isDigit).take(12); error = null },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.focusRequester(focus),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(8.dp)) {
                    repeat(maxOf(6, pin.length)) { i ->
                        val brush = when {
                            i >= pin.length -> SolidColor(MaterialTheme.colorScheme.outlineVariant)
                            error != null -> SolidColor(MaterialTheme.colorScheme.error)
                            else -> BrandGradient
                        }
                        Box(Modifier.size(16.dp).background(brush, CircleShape))
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = ::submit, enabled = !busy && pin.length >= 6,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (busy) "Вход…" else "Войти", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBack) { Text("Это не я") }
    }
}
