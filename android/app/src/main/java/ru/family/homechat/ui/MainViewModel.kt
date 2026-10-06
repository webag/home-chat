package ru.family.homechat.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.family.homechat.data.Chat
import ru.family.homechat.data.ChatEntry
import ru.family.homechat.data.FAMILY_CHAT
import ru.family.homechat.data.Member
import ru.family.homechat.data.NicknameCache
import ru.family.homechat.data.Release
import ru.family.homechat.data.Repo
import ru.family.homechat.data.Updater
import ru.family.homechat.data.directChatId

/** Content received via "Share" from another app, waiting for the user to pick a chat. */
data class Shared(val text: String, val uris: List<Uri>, val mime: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel : ViewModel() {
    val me: StateFlow<String?> = callbackFlow {
        val l = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        Repo.auth.addAuthStateListener(l)
        awaitClose { Repo.auth.removeAuthStateListener(l) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Repo.uid)

    /** The viewer's own names for other members (uid -> name). */
    val nicknames: StateFlow<Map<String, String>> =
        me.flatMapLatest { if (it == null) flowOf(emptyMap()) else Repo.nicknames(it) }
            .onEach { NicknameCache.save(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Members with custom names applied, so every screen shows them without extra work. */
    val members: StateFlow<List<Member>> = combine(
        me.flatMapLatest { if (it == null) flowOf(emptyList()) else Repo.members() }, nicknames, me,
    ) { list, nicks, me ->
        list.map { m -> nicks[m.uid]?.takeIf { m.uid != me }?.let { m.copy(name = it) } ?: m }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val chats: StateFlow<List<Chat>> = me.flatMapLatest { if (it == null) flowOf(emptyList()) else Repo.chats(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Chats marked as unread by hand; the mark is cleared when the chat is opened. */
    val markedUnread: StateFlow<Set<String>> = me.flatMapLatest { if (it == null) flowOf(emptySet()) else Repo.markedUnread(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Family chat + one direct chat per other member, most recent first. */
    val entries: StateFlow<List<ChatEntry>> = combine(me, members, chats, markedUnread) { me, members, chats, marked ->
        if (me == null) return@combine emptyList()
        val byId = chats.associateBy { it.id }
        fun unread(c: Chat?): Boolean {
            if (c != null && c.id in marked) return true
            val last = c?.last ?: return false
            if (last.senderId == me) return false
            val read = c.readBy[me] ?: return true
            return last.at != null && last.at.after(read)
        }
        val family = byId[FAMILY_CHAT]
        val list = mutableListOf(ChatEntry(FAMILY_CHAT, family?.title ?: "Семья", null, true, family, unread(family)))
        members.filter { it.uid != me }.forEach { m ->
            val id = directChatId(me, m.uid)
            list += ChatEntry(id, m.name, m.color, false, byId[id], unread(byId[id]), m.avatarUrl)
        }
        list.sortedByDescending { it.chat?.last?.at?.time ?: if (it.isGroup) 1L else 0L }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val shared = MutableStateFlow<Shared?>(null)
    /** Chat to open from a tapped notification. */
    val openChat = MutableStateFlow<String?>(null)

    /** Newer release to offer; null when up to date or the user chose "later". */
    val update = MutableStateFlow<Release?>(null)

    init {
        viewModelScope.launch { runCatching { checkUpdate() } }
    }

    /** Throws on network errors so a manual check can report them. */
    suspend fun checkUpdate(): Release? = Updater.check().also { update.value = it }

    fun member(uid: String) = members.value.firstOrNull { it.uid == uid }

    fun markUnread(chatId: String) = Repo.setMarkedUnread(chatId, true)

    /** Clears the manual mark and, if there are new messages, really reads them (senders see ticks). */
    fun markRead(chatId: String) {
        if (chatId in markedUnread.value) Repo.setMarkedUnread(chatId, false)
        val e = entries.value.firstOrNull { it.id == chatId }
        if (e?.chat != null && e.unread) Repo.markRead(chatId)
    }

    /** For direct chats returns the other member's uid. */
    fun peerOf(chatId: String): String? =
        if (chatId == FAMILY_CHAT) null else chatId.removePrefix("dm_").split("_").firstOrNull { it != me.value }

    fun titleOf(chatId: String): String =
        if (chatId == FAMILY_CHAT) chats.value.firstOrNull { it.id == chatId }?.title ?: "Семья"
        else peerOf(chatId)?.let { member(it)?.name } ?: ""
}
