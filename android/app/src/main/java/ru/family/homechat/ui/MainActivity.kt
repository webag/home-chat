package ru.family.homechat.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent { HomeChatTheme { Root(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra(EXTRA_CHAT)?.let { vm.openChat.value = it }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = listOfNotNull(
                    intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { s ->
                        intent.getStringExtra(Intent.EXTRA_TEXT)?.contains(s) != true
                    },
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                ).joinToString("\n")
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (text.isNotBlank() || uri != null) vm.shared.value = Shared(text, listOfNotNull(uri), intent.type)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                if (uris.isNotEmpty()) vm.shared.value = Shared(text, uris, intent.type)
            }
        }
    }

    companion object {
        const val EXTRA_CHAT = "chatId"
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    val me by vm.me.collectAsStateWithLifecycle()
    if (me == null) {
        LoginScreen()
        return
    }
    val nav = rememberNavController()
    val shared by vm.shared.collectAsStateWithLifecycle()
    val openChat by vm.openChat.collectAsStateWithLifecycle()

    LaunchedEffect(shared != null) {
        if (shared != null) nav.navigate("share") { launchSingleTop = true }
    }
    LaunchedEffect(openChat) {
        openChat?.let {
            vm.openChat.value = null
            nav.navigate("chat/$it") { popUpTo("chats"); launchSingleTop = true }
        }
    }

    NavHost(nav, startDestination = "chats") {
        composable("chats") {
            ChatListScreen(vm, title = "Семья", onOpen = { nav.navigate("chat/$it") })
        }
        composable("share") {
            ChatListScreen(vm, title = "Кому отправить?", onBack = {
                vm.shared.value = null
                nav.popBackStack()
            }, onOpen = { id ->
                nav.navigate("chat/$id?share=true") { popUpTo("chats") }
            })
        }
        composable(
            "chat/{id}?share={share}",
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("share") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            val id = entry.arguments!!.getString("id")!!
            val share = entry.arguments!!.getBoolean("share")
            ChatScreen(vm, id, takeShared = share, onBack = { nav.popBackStack() },
                onOpenImage = { url -> nav.navigate("image?url=" + Uri.encode(url)) })
        }
        composable("image?url={url}", arguments = listOf(navArgument("url") { type = NavType.StringType })) { entry ->
            ImageViewer(entry.arguments!!.getString("url")!!, onBack = { nav.popBackStack() })
        }
    }
}
