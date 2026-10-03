package ru.family.homechat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.family.homechat.App
import ru.family.homechat.data.Attachment
import ru.family.homechat.data.Chat
import ru.family.homechat.data.FAMILY_CHAT
import ru.family.homechat.data.MAX_FILE_BYTES
import ru.family.homechat.data.Media
import ru.family.homechat.data.Message
import ru.family.homechat.data.Outbox
import ru.family.homechat.data.Outgoing
import ru.family.homechat.data.Repo
import ru.family.homechat.push.Notifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: MainViewModel, chatId: String, takeShared: Boolean, onBack: () -> Unit, onOpenImage: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me by vm.me.collectAsStateWithLifecycle()
    val members by vm.members.collectAsStateWithLifecycle()
    val chats by vm.chats.collectAsStateWithLifecycle()
    val outbox by Outbox.items.collectAsStateWithLifecycle()

    val isGroup = chatId == FAMILY_CHAT
    val chat = chats.firstOrNull { it.id == chatId }
    val peer = remember(chatId, me) { vm.peerOf(chatId) }
    val peerMember = members.firstOrNull { it.uid == peer }
    val title = if (isGroup) chat?.title ?: "Семья" else peerMember?.name ?: ""

    var limit by remember { mutableLongStateOf(100) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    LaunchedEffect(chatId, limit) { Repo.messages(chatId, limit).collect { messages = it } }
    val pendingUploads = outbox.filter { it.chatId == chatId }

    var text by rememberSaveable { mutableStateOf("") }
    val attachments = remember { mutableStateListOf<Attachment>() }
    val listState = rememberLazyListState()

    fun addAttachments(uris: List<android.net.Uri>, mime: String? = null) = scope.launch {
        val described = withContext(Dispatchers.IO) { uris.map { Media.describe(ctx, it, mime) } }
        described.forEach { a ->
            if (!a.isImage && a.size > MAX_FILE_BYTES) FileActions.toast(ctx, "«${a.name}» больше 50 МБ — не отправится")
            else attachments += a
        }
    }

    LaunchedEffect(Unit) {
        if (takeShared) vm.shared.value?.let { s ->
            vm.shared.value = null
            text = s.text
            addAttachments(s.uris, s.mime)
        }
    }

    // Track which chat is on screen (to suppress its pushes) and mark it read while visible.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    DisposableEffect(chatId) {
        App.openChatId = chatId
        onDispose { if (App.openChatId == chatId) App.openChatId = null }
    }
    LaunchedEffect(visible, messages.firstOrNull()?.id, chat?.readBy?.get(me)) {
        if (!visible) return@LaunchedEffect
        Notifications.clear(ctx, chatId)
        val top = messages.firstOrNull() ?: return@LaunchedEffect
        if (chat == null || top.senderId == me) return@LaunchedEffect
        val read = chat.readBy[me]
        if (read == null || top.createdAt?.after(read) == true) Repo.markRead(chatId)
    }

    // Keep the newest message in view; load older ones when scrolled to the top.
    LaunchedEffect(messages.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }
    val nearTop by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearTop, messages.size) {
        if (nearTop && messages.size >= limit) limit += 100
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { addAttachments(it) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { addAttachments(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(title, peerMember?.color, group = isGroup, size = 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isGroup) Text("${members.size} участников", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                text = text, onText = { text = it },
                attachments = attachments,
                onRemove = { attachments.remove(it) },
                onPickMedia = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onPickFile = { pickFiles.launch(arrayOf("*/*")) },
                onSend = {
                    Outbox.send(ctx, chatId, peer, text, attachments.toList())
                    text = ""
                    attachments.clear()
                    scope.launch { listState.scrollToItem(0) }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = pad,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            items(pendingUploads.reversed(), key = { "out_" + it.id }) { OutgoingRow(it) }
            itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                val older = messages.getOrNull(i + 1)
                val newDay = older == null || !isSameDay(older.createdAt, m.createdAt)
                Column {
                    if (newDay && m.createdAt != null) DayHeader(dayTitle(m.createdAt))
                    val mine = m.senderId == me
                    val sender = if (isGroup && !mine && (newDay || older?.senderId != m.senderId)) vm.member(m.senderId) else null
                    Bubble(
                        m = m, mine = mine,
                        senderName = sender?.name, senderColor = sender?.color,
                        readByOthers = mine && isReadByOthers(chat, me, m),
                        onOpenImage = onOpenImage,
                        onDelete = { Repo.delete(chatId, m.id) },
                    )
                }
            }
        }
    }
}

private fun isReadByOthers(chat: Chat?, me: String?, m: Message): Boolean {
    val at = m.createdAt ?: return false
    return chat?.readBy?.any { (uid, t) -> uid != me && !t.before(at) } == true
}

@Composable
private fun DayHeader(title: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: Message, mine: Boolean, senderName: String?, senderColor: String?, readByOthers: Boolean,
    onOpenImage: (String) -> Unit, onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val f = m.file
    val shape = RoundedCornerShape(16.dp)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Surface(
                color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = shape,
                modifier = Modifier.widthIn(max = 300.dp).clip(shape).combinedClickable(
                    onClick = {
                        when {
                            f == null -> {}
                            m.type == "image" -> onOpenImage(f.url)
                            m.type == "video" -> FileActions.openVideo(ctx, f)
                            else -> FileActions.download(ctx, f)
                        }
                    },
                    onLongClick = { menu = true },
                ),
            ) {
                Column(Modifier.padding(if (f != null && m.type != "file") 4.dp else 10.dp)) {
                    if (senderName != null) Text(
                        senderName, color = parseColor(senderColor), fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                    )
                    when {
                        f != null && m.type == "image" -> AsyncImage(
                            model = f.url, contentDescription = null, contentScale = ContentScale.Crop,
                            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceDim),
                            error = ColorPainter(MaterialTheme.colorScheme.surfaceDim),
                            modifier = Modifier.width(260.dp).aspectRatio(ratio(f.width, f.height)).clip(RoundedCornerShape(12.dp)),
                        )
                        f != null && m.type == "video" -> Box(
                            Modifier.width(260.dp).aspectRatio(ratio(f.width, f.height)).clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            f.thumbUrl?.let {
                                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Icon(Icons.Filled.PlayCircle, "Смотреть", tint = Color.White, modifier = Modifier.size(56.dp))
                            Text(humanSize(f.size), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                        }
                        f != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(humanSize(f.size), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (m.text.isNotBlank()) LinkText(m.text, Modifier.padding(horizontal = if (f != null && m.type != "file") 6.dp else 0.dp, vertical = 2.dp))
                    Row(
                        Modifier.align(Alignment.End).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(clock(m.createdAt), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (mine) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                when { m.pending -> Icons.Filled.Schedule; readByOthers -> Icons.Filled.DoneAll; else -> Icons.Filled.Done },
                                null, modifier = Modifier.size(14.dp),
                                tint = if (readByOthers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                if (m.text.isNotBlank()) DropdownMenuItem(text = { Text("Копировать текст") }, onClick = {
                    menu = false; FileActions.copy(ctx, m.text)
                })
                DropdownMenuItem(text = { Text("Переслать в другое приложение") }, onClick = {
                    menu = false; scope.launch { FileActions.share(ctx, m) }
                })
                if (f != null) DropdownMenuItem(text = { Text("Сохранить на телефон") }, onClick = {
                    menu = false; FileActions.download(ctx, f)
                })
                if (mine) DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

private fun ratio(w: Int, h: Int): Float = if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.6f, 1.8f) else 4f / 3f

private val urlRegex = Regex("""(https?://|www\.)\S+""")

@Composable
private fun LinkText(text: String, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            var pos = 0
            urlRegex.findAll(text).forEach { mr ->
                val url = mr.value.trimEnd('.', ',', ')', '!', '?', ';', ':')
                append(text.substring(pos, mr.range.first))
                val target = if (url.startsWith("www.")) "https://$url" else url
                withLink(LinkAnnotation.Url(target, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                    append(url)
                }
                pos = mr.range.first + url.length
            }
            append(text.substring(pos))
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

@Composable
private fun OutgoingRow(o: Outgoing) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 300.dp)) {
            Column(Modifier.padding(10.dp)) {
                Text(o.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(6.dp))
                if (o.error == null) {
                    LinearProgressIndicator(progress = { o.progress }, modifier = Modifier.width(200.dp))
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false))
                    IconButton(onClick = { Outbox.dismiss(o.id) }) { Icon(Icons.Filled.Close, "Убрать") }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    text: String, onText: (String) -> Unit,
    attachments: List<Attachment>, onRemove: (Attachment) -> Unit,
    onPickMedia: () -> Unit, onPickFile: () -> Unit, onSend: () -> Unit,
) {
    var attachMenu by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            if (attachments.isNotEmpty()) LazyRow(
                Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(attachments) { a -> AttachmentPreview(a) { onRemove(a) } }
            }
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.Bottom) {
                Box {
                    IconButton(onClick = { attachMenu = true }) { Icon(Icons.Filled.AttachFile, "Прикрепить") }
                    DropdownMenu(attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("Фото или видео") }, onClick = { attachMenu = false; onPickMedia() })
                        DropdownMenuItem(text = { Text("Файл") }, onClick = { attachMenu = false; onPickFile() })
                    }
                }
                TextField(
                    value = text, onValueChange = onText,
                    placeholder = { Text("Сообщение") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                val canSend = text.isNotBlank() || attachments.isNotEmpty()
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить",
                        tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(a: Attachment, onRemove: () -> Unit) {
    Box(Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (a.isImage || a.isVideo) {
            AsyncImage(model = a.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (a.isVideo) Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
        } else Column(Modifier.padding(6.dp).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null)
            Text(a.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(3.dp).size(22.dp).background(Color(0x99000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.Close, "Убрать", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
