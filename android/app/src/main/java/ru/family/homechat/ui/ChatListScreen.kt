package ru.family.homechat.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.family.homechat.BuildConfig
import ru.family.homechat.data.ChatEntry
import ru.family.homechat.data.Repo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(vm: MainViewModel, title: String, onOpen: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (Build.VERSION.SDK_INT >= 33) {
        val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { perm.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    LaunchedEffect(me) { Repo.registerPushToken() }
    onBack?.let { BackHandler(onBack = it) }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(Modifier.nestedScroll(scroll.nestedScrollConnection), topBar = {
        LargeTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            scrollBehavior = scroll,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            navigationIcon = {
                onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } }
            },
            actions = {
                if (onBack == null) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        val mine = me?.let { vm.member(it) }
                        DropdownMenuItem(
                            text = { Text(mine?.name ?: "", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Avatar(mine?.name ?: "", mine?.color, size = 28.dp) },
                            onClick = {}, enabled = false,
                            colors = MenuDefaults.itemColors(disabledTextColor = MaterialTheme.colorScheme.onSurface),
                        )
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Проверить обновления") },
                            leadingIcon = { Icon(Icons.Filled.SystemUpdate, null) }, onClick = {
                            menu = false
                            scope.launch {
                                try {
                                    if (vm.checkUpdate() == null) FileActions.toast(ctx, "У вас последняя версия")
                                } catch (e: Exception) {
                                    FileActions.toast(ctx, "Не удалось проверить обновления")
                                }
                            }
                        })
                        DropdownMenuItem(text = { Text("Выйти") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                            onClick = { menu = false; confirmLogout = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium) },
                            onClick = {}, enabled = false)
                    }
                }
            },
        )
    }) { pad ->
        LazyColumn(contentPadding = pad, modifier = Modifier.padding(horizontal = 8.dp)) {
            items(entries, key = { it.id }) { e -> ChatRow(vm, e, me) { onOpen(e.id) } }
        }
    }

    update?.takeIf { onBack == null }?.let { UpdateDialog(it, onDismiss = { vm.update.value = null }) }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Выйти из чата?") },
        text = { Text("Чтобы войти снова, понадобится PIN-код.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; scope.launch { Repo.signOut() } }) { Text("Выйти") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
    )
}

@Composable
private fun ChatRow(vm: MainViewModel, e: ChatEntry, me: String?, onClick: () -> Unit) {
    val last = e.chat?.last
    val preview = when {
        last == null -> if (e.isGroup) "Общий чат всей семьи" else "Напиши первым"
        last.senderId == me -> "Вы: ${last.text}"
        e.isGroup -> "${vm.member(last.senderId)?.name ?: ""}: ${last.text}"
        else -> last.text
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(e.title, e.color, group = e.isGroup, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shortTime(last?.at), style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (e.unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (e.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preview, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (e.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (e.unread) FontWeight.Medium else FontWeight.Normal)
                if (e.unread) Box(Modifier.padding(start = 8.dp).size(12.dp).background(BrandGradient, CircleShape))
            }
        }
    }
}
