package ru.family.homechat.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.family.homechat.App
import ru.family.homechat.R
import ru.family.homechat.data.NicknameCache
import ru.family.homechat.data.Repo
import ru.family.homechat.ui.MainActivity

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch { Repo.registerPushToken(token) }
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val d = m.data
        val chatId = d["chatId"] ?: return
        if (App.foreground && App.openChatId == chatId) return
        val group = d["group"] == "1"
        val sender = d["senderId"]?.let(NicknameCache::get) ?: d["sender"] ?: ""
        // In direct chats the title is the sender's name, so it gets the custom name too.
        val title = if (group) d["title"] ?: "" else sender
        Notifications.show(this, chatId, title, sender, d["body"] ?: "", group)
    }
}

object Notifications {
    private const val CHANNEL = "messages"
    private val history = mutableMapOf<String, MutableList<Pair<String, String>>>()

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Сообщения", NotificationManager.IMPORTANCE_HIGH)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    @Synchronized
    fun show(ctx: Context, chatId: String, title: String, sender: String, body: String, isGroup: Boolean) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && android.os.Build.VERSION.SDK_INT >= 33) return

        val lines = history.getOrPut(chatId) { mutableListOf() }
        lines += sender to body
        if (lines.size > 8) lines.removeAt(0)

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Я").build())
            .setConversationTitle(if (isGroup) title else null)
            .setGroupConversation(isGroup)
        lines.forEach { (s, b) -> style.addMessage(b, System.currentTimeMillis(), Person.Builder().setName(s).build()) }

        val open = PendingIntent.getActivity(
            ctx, chatId.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_CHAT, chatId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4F7DF3.toInt())
            .setStyle(style)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(ctx).notify(chatId.hashCode(), n)
    }

    @Synchronized
    fun clear(ctx: Context, chatId: String) {
        history.remove(chatId)
        NotificationManagerCompat.from(ctx).cancel(chatId.hashCode())
    }
}
