package ru.family.homechat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.unit.Dp
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
import ru.family.homechat.data.Member
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
    var chatMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    if (renaming && peerMember != null) RenameDialog(peerMember, onDismiss = { renaming = false })

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
    // Opening a chat removes the manual "unread" mark (once, so marking it from the menu here sticks).
    LaunchedEffect(chatId) {
        if (chatId in vm.markedUnread.value) Repo.setMarkedUnread(chatId, false)
    }
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
        containerColor = Color.Transparent,
        modifier = Modifier.background(LocalChatColors.current.background),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(title, peerMember?.color, group = isGroup, size = 40.dp, url = peerMember?.avatarUrl)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val subtitle = when {
                                isGroup -> plural(members.size, "участник", "участника", "участников")
                                peerMember != null && peerMember.name != peerMember.realName -> peerMember.realName
                                else -> null
                            }
                            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { chatMenu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                        DropdownMenu(chatMenu, onDismissRequest = { chatMenu = false }) {
                            if (chat?.last != null) DropdownMenuItem(text = { Text("Отметить непрочитанным") },
                                leadingIcon = { Icon(Icons.Outlined.MarkChatUnread, null) },
                                // Back to the list, otherwise the open chat would clear the mark right away.
                                onClick = { chatMenu = false; vm.markUnread(chatId); onBack() })
                            if (peerMember != null) DropdownMenuItem(text = { Text("Переименовать") },
                                leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                onClick = { chatMenu = false; renaming = true })
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
            modifier = Modifier.fillMaxSize(),
        ) {
            items(pendingUploads.reversed(), key = { "out_" + it.id }) { OutgoingRow(it) }
            itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                // The list is reversed: i + 1 is the older (upper) neighbour, i - 1 the newer one.
                val older = messages.getOrNull(i + 1)
                val newer = messages.getOrNull(i - 1)
                val newDay = older == null || !isSameDay(older.createdAt, m.createdAt)
                val first = newDay || older?.senderId != m.senderId
                val last = newer == null || newer.senderId != m.senderId || !isSameDay(newer.createdAt, m.createdAt)
                val mine = m.senderId == me
                val withAvatar = isGroup && !mine
                Column {
                    if (newDay && m.createdAt != null) DayHeader(dayTitle(m.createdAt))
                    Bubble(
                        m = m, mine = mine, first = first, last = last,
                        sender = if (withAvatar && first) vm.member(m.senderId) else null,
                        avatarSlot = withAvatar,
                        avatar = if (withAvatar && last) vm.member(m.senderId) else null,
                        readByOthers = mine && isReadByOthers(chat, me, m),
                        onOpenImage = onOpenImage,
                        onDelete = { Repo.delete(chatId, m.id) },
                    )
                }
            }
        }
    }
}

/** Own name for a contact, stored privately in Firestore; only the person who sets it sees it. */
@Composable
private fun RenameDialog(m: Member, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(m.name) }
    val custom = m.name != m.realName
    fun save(value: String) {
        Repo.setNickname(m.uid, if (value.trim() == m.realName) "" else value)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Как подписать контакт?") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    placeholder = { Text(m.realName) }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Это имя увидишь только ты.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (custom) TextButton(onClick = { save("") }, contentPadding = PaddingValues(0.dp)) {
                    Text("Вернуть «${m.realName}»")
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(name) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun isReadByOthers(chat: Chat?, me: String?, m: Message): Boolean {
    val at = m.createdAt ?: return false
    return chat?.readBy?.any { (uid, t) -> uid != me && !t.before(at) } == true
}

@Composable
private fun DayHeader(title: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(shape = CircleShape, color = LocalChatColors.current.pill) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

/**
 * Consecutive messages of one sender form a group: corners facing the neighbour shrink,
 * and the newest one gets a sharp "tail" corner at the bottom on the sender's side.
 */
private fun bubbleShape(mine: Boolean, first: Boolean, last: Boolean, inset: Dp = 0.dp): RoundedCornerShape {
    fun r(d: Dp) = (d - inset).coerceAtLeast(2.dp)
    val big = r(20.dp)
    val joined = r(6.dp)
    val top = if (first) big else joined
    val bottom = if (last) r(4.dp) else joined
    return if (mine) RoundedCornerShape(topStart = big, topEnd = top, bottomEnd = bottom, bottomStart = big)
    else RoundedCornerShape(topStart = top, topEnd = big, bottomEnd = big, bottomStart = bottom)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: Message, mine: Boolean, first: Boolean, last: Boolean,
    sender: Member?, avatarSlot: Boolean, avatar: Member?, readByOthers: Boolean,
    onOpenImage: (String) -> Unit, onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalChatColors.current
    var menu by remember { mutableStateOf(false) }
    val f = m.file
    val media = f != null && (m.type == "image" || m.type == "video")
    val shape = bubbleShape(mine, first, last)
    val inner = bubbleShape(mine, first, last, inset = 3.dp)
    val onBubble = if (mine) Color.White else colors.onBubbleIn
    val muted = if (mine) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = if (first) 6.dp else 1.dp, bottom = 1.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (avatarSlot) {
            Box(Modifier.size(32.dp)) { avatar?.let { Avatar(it, 32.dp) } }
            Spacer(Modifier.width(6.dp))
        }
        if (mine) Spacer(Modifier.width(48.dp))
        Box(Modifier.weight(1f, fill = false)) {
            Column(
                Modifier.widthIn(max = 300.dp).clip(shape)
                    .background(if (mine) colors.bubbleOut else colors.bubbleIn)
                    .combinedClickable(
                        onClick = {
                            when {
                                f == null -> {}
                                m.type == "image" -> onOpenImage(f.url)
                                m.type == "video" -> FileActions.openVideo(ctx, f)
                                else -> FileActions.download(ctx, f)
                            }
                        },
                        onLongClick = { menu = true },
                    )
                    .padding(if (media) PaddingValues(3.dp) else PaddingValues(horizontal = 12.dp, vertical = 7.dp)),
            ) {
                CompositionLocalProvider(LocalContentColor provides onBubble) {
                    if (sender != null) Text(
                        sender.name, color = parseColor(sender.color), fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(
                            start = if (media) 9.dp else 0.dp, top = if (media) 5.dp else 0.dp, bottom = 3.dp,
                        ),
                    )
                    val overlayMeta = media && m.text.isBlank()
                    when {
                        f != null && m.type == "image" -> Box {
                            AsyncImage(
                                model = f.url, contentDescription = null, contentScale = ContentScale.Crop,
                                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceDim),
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceDim),
                                modifier = Modifier.width(260.dp).aspectRatio(ratio(f.width, f.height)).clip(inner),
                            )
                            if (overlayMeta) MediaMeta(m, mine, readByOthers, Modifier.align(Alignment.BottomEnd))
                        }
                        f != null && m.type == "video" -> Box(
                            Modifier.width(260.dp).aspectRatio(ratio(f.width, f.height)).clip(inner).background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            f.thumbUrl?.let {
                                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Box(
                                Modifier.size(56.dp).background(Color(0x66000000), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Filled.PlayArrow, "Смотреть", tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            Text(humanSize(f.size), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                    .background(Color(0x66000000), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
                            if (overlayMeta) MediaMeta(m, mine, readByOthers, Modifier.align(Alignment.BottomEnd))
                        }
                        f != null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Box(
                                Modifier.size(44.dp).background(
                                    if (mine) SolidColor(Color.White.copy(alpha = 0.22f)) else BrandGradient, CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(humanSize(f.size), style = MaterialTheme.typography.labelMedium, color = muted)
                            }
                        }
                    }
                    if (m.text.isNotBlank()) LinkText(
                        m.text, linkColor = if (mine) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = if (media) 9.dp else 0.dp, vertical = 2.dp),
                    )
                    if (!overlayMeta) Meta(
                        m, mine, readByOthers, muted,
                        Modifier.align(Alignment.End).padding(end = if (media) 6.dp else 0.dp, bottom = if (media) 3.dp else 0.dp),
                    )
                }
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                if (m.text.isNotBlank()) DropdownMenuItem(text = { Text("Копировать текст") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {
                        menu = false; FileActions.copy(ctx, m.text)
                    })
                DropdownMenuItem(text = { Text("Переслать в другое приложение") },
                    leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = {
                        menu = false; scope.launch { FileActions.share(ctx, m) }
                    })
                if (f != null) DropdownMenuItem(text = { Text("Сохранить на телефон") },
                    leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = {
                        menu = false; FileActions.download(ctx, f)
                    })
                if (mine) DropdownMenuItem(
                    text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
        if (!mine) Spacer(Modifier.width(48.dp))
    }
}

/** Time and delivery ticks under the text. */
@Composable
private fun Meta(m: Message, mine: Boolean, readByOthers: Boolean, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(clock(m.createdAt), style = MaterialTheme.typography.labelSmall, color = color)
        if (mine) {
            Spacer(Modifier.width(3.dp))
            Icon(
                when { m.pending -> Icons.Filled.Schedule; readByOthers -> Icons.Filled.DoneAll; else -> Icons.Filled.Done },
                null, modifier = Modifier.size(15.dp),
                tint = if (readByOthers) Color.White else color,
            )
        }
    }
}

/** Time over a photo or video without a caption. */
@Composable
private fun MediaMeta(m: Message, mine: Boolean, readByOthers: Boolean, modifier: Modifier) {
    Meta(
        m, mine, readByOthers, Color.White,
        modifier.padding(6.dp).background(Color(0x73000000), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

private fun ratio(w: Int, h: Int): Float = if (w > 0 && h > 0) (w.toFloat() / h).coerceIn(0.6f, 1.8f) else 4f / 3f

private val urlRegex = Regex("""(https?://|www\.)\S+""")

@Composable
private fun LinkText(text: String, linkColor: Color, modifier: Modifier = Modifier) {
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
    val shape = bubbleShape(mine = true, first = true, last = true)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(LocalChatColors.current.bubbleOut)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(o.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
            Spacer(Modifier.size(8.dp))
            if (o.error == null) {
                LinearProgressIndicator(
                    progress = { o.progress }, modifier = Modifier.width(200.dp).clip(CircleShape),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.3f), drawStopIndicator = {},
                )
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.error, color = Color.White, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false))
                IconButton(onClick = { Outbox.dismiss(o.id) }) { Icon(Icons.Filled.Close, "Убрать", tint = Color.White) }
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
    val colors = LocalChatColors.current
    var attachMenu by remember { mutableStateOf(false) }
    val canSend = text.isNotBlank() || attachments.isNotEmpty()
    Column(Modifier.navigationBarsPadding().imePadding()) {
        if (attachments.isNotEmpty()) LazyRow(
            Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(attachments) { a -> AttachmentPreview(a) { onRemove(a) } }
        }
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            val pill = RoundedCornerShape(24.dp)
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).shadow(2.dp, pill).background(colors.bubbleIn, pill),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box {
                    IconButton(onClick = { attachMenu = true }) {
                        Icon(Icons.Filled.Add, "Прикрепить", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("Фото или видео") }, leadingIcon = { Icon(Icons.Filled.Image, null) },
                            onClick = { attachMenu = false; onPickMedia() })
                        DropdownMenuItem(text = { Text("Файл") }, leadingIcon = { Icon(Icons.Outlined.Description, null) },
                            onClick = { attachMenu = false; onPickFile() })
                    }
                }
                Box(Modifier.weight(1f).padding(top = 13.dp, bottom = 13.dp, end = 16.dp)) {
                    if (text.isEmpty()) Text("Сообщение", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        value = text, onValueChange = onText, maxLines = 6,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBubbleIn),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            val idle by animateColorAsState(
                if (canSend) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest, label = "send",
            )
            Box(
                Modifier.size(48.dp).shadow(if (canSend) 4.dp else 0.dp, CircleShape)
                    .background(if (canSend) BrandGradient else SolidColor(idle), CircleShape)
                    .clip(CircleShape).clickable(enabled = canSend, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Отправить", modifier = Modifier.size(22.dp),
                    tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AttachmentPreview(a: Attachment, onRemove: () -> Unit) {
    Box(Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (a.isImage || a.isVideo) {
            AsyncImage(model = a.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (a.isVideo) Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
        } else Column(Modifier.padding(6.dp).align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null)
            Text(a.name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(Color(0x99000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Filled.Close, "Убрать", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}
