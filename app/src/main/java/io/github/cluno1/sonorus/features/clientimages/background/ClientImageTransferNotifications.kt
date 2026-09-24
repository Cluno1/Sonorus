package io.github.cluno1.sonorus.features.clientimages.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.cluno1.sonorus.R
import io.github.cluno1.sonorus.activities.MainActivity
import io.github.cluno1.sonorus.features.clientimages.data.local.ClientImageBatchProgress

object ClientImageTransferNotifications {
    const val CHANNEL_ID = "client_image_uploads"

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "图片上传",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "显示图片上传进度" },
        )
    }

    fun build(context: Context, progress: ClientImageBatchProgress? = null): Notification {
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            9100,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val completed = progress?.let { it.succeededCount + it.failedCount } ?: 0
        val total = progress?.totalCount ?: 0
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("正在上传图片")
            .setContentText(if (total > 0) "$completed / $total" else "正在准备上传队列")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setProgress(total.coerceAtLeast(1), completed, total == 0)
            .build()
    }
}
