package ru.family.homechat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ru.family.homechat.push.Notifications

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Notifications.createChannel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { foreground = true }
            override fun onStop(owner: LifecycleOwner) { foreground = false }
        })
    }

    companion object {
        lateinit var instance: App
            private set

        @Volatile var foreground = false
        /** Chat currently on screen — pushes for it are not shown. */
        @Volatile var openChatId: String? = null
    }
}
