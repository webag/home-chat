package ru.family.homechat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text("Семейный чат", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            val sel = selected
            when {
                sel != null -> PinForm(sel, onBack = { selected = null })
                loadError -> {
                    Text("Нет связи с сервером", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { reload++ }) { Text("Повторить") }
                }
                members == null -> CircularProgressIndicator()
                else -> {
                    Text("Кто ты?", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    LazyVerticalGrid(GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(members!!) { m ->
                            Column(
                                Modifier.clickable { selected = m }.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(m.name, m.color, size = 64.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(m.name, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
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
                error = if (it is com.google.firebase.auth.FirebaseAuthException) "Неверный PIN" else "Нет связи, попробуй ещё раз"
                busy = false
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(m.name, m.color, size = 80.dp)
        Spacer(Modifier.height(8.dp))
        Text(m.name, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter(Char::isDigit).take(12) },
            label = { Text("PIN-код") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = ::submit, enabled = !busy && pin.length >= 6, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Вход…" else "Войти")
        }
        TextButton(onClick = onBack) { Text("Это не я") }
    }
}
