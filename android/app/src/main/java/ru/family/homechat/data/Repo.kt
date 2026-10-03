package ru.family.homechat.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import ru.family.homechat.push.NotifyWorker

object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    val uid: String? get() = auth.currentUser?.uid

    suspend fun signIn(email: String, pin: String) {
        auth.signInWithEmailAndPassword(email, pin).await()
        registerPushToken()
    }

    suspend fun signOut() {
        val me = uid ?: return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            db.collection("users").document(me).update("fcmTokens", FieldValue.arrayRemove(token)).await()
        }
        auth.signOut()
    }

    suspend fun registerPushToken(token: String? = null) {
        val me = uid ?: return
        runCatching {
            val t = token ?: FirebaseMessaging.getInstance().token.await()
            db.collection("users").document(me).update("fcmTokens", FieldValue.arrayUnion(t)).await()
        }
    }

    fun members(): Flow<List<Member>> = callbackFlow {
        val reg = db.collection("users").orderBy("order").addSnapshotListener { snap, _ ->
            snap ?: return@addSnapshotListener
            trySend(snap.documents.map {
                Member(it.id, it.getString("name") ?: "?", it.getString("email") ?: "", it.getString("color") ?: "#90A4AE")
            })
        }
        awaitClose { reg.remove() }
    }

    fun chats(me: String): Flow<List<Chat>> = callbackFlow {
        val reg = db.collection("chats").whereArrayContains("members", me).addSnapshotListener { snap, _ ->
            snap ?: return@addSnapshotListener
            trySend(snap.documents.map(Chat::from))
        }
        awaitClose { reg.remove() }
    }

    fun messages(chatId: String, limit: Long): Flow<List<Message>> = callbackFlow {
        val reg = db.collection("chats").document(chatId).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
            .addSnapshotListener { snap, _ ->
                snap ?: return@addSnapshotListener
                trySend(snap.documents.map(Message::from))
            }
        awaitClose { reg.remove() }
    }

    /**
     * Writes the message and the chat summary in one batch; the push is sent by a
     * background job so it survives the app being closed right after sending.
     * [peer] is set for direct chats, which are created on the first message.
     */
    fun send(chatId: String, peer: String?, type: String, text: String, file: FileRef?) {
        val me = uid ?: return
        val chatRef = db.collection("chats").document(chatId)
        val msgRef = chatRef.collection("messages").document()
        val msg = buildMap<String, Any> {
            put("senderId", me); put("type", type); put("text", text)
            put("createdAt", FieldValue.serverTimestamp())
            file?.let { put("file", it.toMap()) }
        }
        val preview = when (type) {
            "image" -> "📷 " + text.ifBlank { "Фото" }
            "video" -> "🎬 " + text.ifBlank { "Видео" }
            "file" -> "📎 " + (file?.name ?: "Файл")
            else -> text
        }
        val chat = buildMap<String, Any> {
            put("lastMessage", mapOf("senderId" to me, "text" to preview.take(200), "at" to FieldValue.serverTimestamp()))
            put("updatedAt", FieldValue.serverTimestamp())
            put("readBy", mapOf(me to FieldValue.serverTimestamp()))
            if (peer != null) { put("type", "direct"); put("members", listOf(me, peer).sorted()) }
        }
        db.batch().set(chatRef, chat, SetOptions.merge()).set(msgRef, msg).commit()
        NotifyWorker.enqueue(chatId, msgRef.id)
    }

    fun markRead(chatId: String) {
        val me = uid ?: return
        db.collection("chats").document(chatId).update("readBy.$me", FieldValue.serverTimestamp())
    }

    fun delete(chatId: String, messageId: String) {
        db.collection("chats").document(chatId).collection("messages").document(messageId).delete()
    }
}
