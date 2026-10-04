package ru.family.homechat.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import ru.family.homechat.BuildConfig
import java.util.Date

/** [name] may be the viewer's own name for this contact; [realName] is the one the member has. */
data class Member(
    val uid: String,
    val name: String,
    val email: String,
    val color: String,
    val avatar: String? = null,
    val realName: String = name,
) {
    val avatarUrl get() = avatar?.let(FileRef::fileUrl)
}

data class FileRef(
    val path: String,
    val name: String,
    val size: Long,
    val mime: String,
    val width: Int = 0,
    val height: Int = 0,
    val thumbPath: String? = null,
) {
    val url get() = fileUrl(path)
    val thumbUrl get() = thumbPath?.let(::fileUrl)

    fun toMap() = buildMap {
        put("path", path); put("name", name); put("size", size); put("mime", mime)
        if (width > 0) { put("width", width); put("height", height) }
        thumbPath?.let { put("thumbPath", it) }
    }

    companion object {
        fun fileUrl(path: String) = "${BuildConfig.SERVER}/f/$path"

        fun from(m: Map<*, *>?): FileRef? = m?.let {
            FileRef(
                path = it["path"] as? String ?: return null,
                name = it["name"] as? String ?: "",
                size = (it["size"] as? Number)?.toLong() ?: 0,
                mime = it["mime"] as? String ?: "",
                width = (it["width"] as? Number)?.toInt() ?: 0,
                height = (it["height"] as? Number)?.toInt() ?: 0,
                thumbPath = it["thumbPath"] as? String,
            )
        }
    }
}

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val type: String,           // text | image | video | file
    val file: FileRef?,
    val createdAt: Date?,
    val pending: Boolean,
) {
    companion object {
        fun from(d: DocumentSnapshot) = Message(
            id = d.id,
            senderId = d.getString("senderId") ?: "",
            text = d.getString("text") ?: "",
            type = d.getString("type") ?: "text",
            file = FileRef.from(d.get("file") as? Map<*, *>),
            createdAt = d.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE)?.toDate(),
            pending = d.metadata.hasPendingWrites(),
        )
    }
}

data class LastMessage(val senderId: String, val text: String, val at: Date?)

data class Chat(
    val id: String,
    val type: String,
    val title: String?,
    val members: List<String>,
    val last: LastMessage?,
    val readBy: Map<String, Date>,
) {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(d: DocumentSnapshot): Chat {
            val lm = d.get("lastMessage", ServerTimestampBehavior.ESTIMATE) as? Map<String, Any?>
            val rb = (d.get("readBy", ServerTimestampBehavior.ESTIMATE) as? Map<String, Any?>).orEmpty()
            return Chat(
                id = d.id,
                type = d.getString("type") ?: "direct",
                title = d.getString("title"),
                members = (d.get("members") as? List<String>).orEmpty(),
                last = lm?.let {
                    LastMessage(it["senderId"] as? String ?: "", it["text"] as? String ?: "", (it["at"] as? Timestamp)?.toDate())
                },
                readBy = rb.mapNotNull { (k, v) -> (v as? Timestamp)?.let { k to it.toDate() } }.toMap(),
            )
        }
    }
}

/** One row of the chat list: the family group or a direct chat with one member. */
data class ChatEntry(
    val id: String, val title: String, val color: String?, val isGroup: Boolean, val chat: Chat?, val unread: Boolean,
    val avatarUrl: String? = null,
)

fun directChatId(a: String, b: String) = "dm_" + listOf(a, b).sorted().joinToString("_")

const val FAMILY_CHAT = "family"
