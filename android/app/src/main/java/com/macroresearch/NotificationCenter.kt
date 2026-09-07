package com.macroresearch

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.macroresearch.data.model.SocketEvent

class NotificationCenter(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val channel = NotificationChannel(
            RELEASE_CHANNEL,
            "财经数据公布",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Actual 公布及分析完成提醒" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(event: SocketEvent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val title: String
        val body: String
        when (event.type) {
            "economic_event_released" -> {
                title = "${event.event ?: "财经数据"} 已公布"
                body = "Actual ${event.actual ?: "--"} · Consensus ${event.consensus ?: "--"}"
            }
            "analysis_completed" -> {
                title = "市场反应分析已完成"
                body = "事件 #${event.eventId} 的 60 分钟分析已生成"
            }
            else -> return
        }
        manager.notify(
            event.eventId.hashCode(),
            NotificationCompat.Builder(context, RELEASE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    private companion object {
        const val RELEASE_CHANNEL = "economic_release"
    }
}

