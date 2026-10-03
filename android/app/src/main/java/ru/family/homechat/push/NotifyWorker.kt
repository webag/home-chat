package ru.family.homechat.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import ru.family.homechat.App
import ru.family.homechat.data.Api
import java.util.concurrent.TimeUnit

/** Asks our server to push a just-sent message to the other chat members. */
class NotifyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val chatId = inputData.getString("chatId") ?: return Result.failure()
        val messageId = inputData.getString("messageId") ?: return Result.failure()
        val code = runCatching { Api.notify(chatId, messageId) }.getOrDefault(-1)
        return when {
            code in 200..299 -> Result.success()
            // 404: the message hasn't reached Firestore yet (offline write) — try again later.
            runAttemptCount >= 8 -> Result.failure()
            else -> Result.retry()
        }
    }

    companion object {
        fun enqueue(chatId: String, messageId: String) {
            val req = OneTimeWorkRequestBuilder<NotifyWorker>()
                .setInputData(workDataOf("chatId" to chatId, "messageId" to messageId))
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setInitialDelay(1, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(App.instance).enqueue(req)
        }
    }
}
