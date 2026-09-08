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
import com.macroresearch.ui.common.localizedName

class NotificationCenter(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init { updateChannel(ContextCompat.getContextForLanguage(context)) }

    private fun updateChannel(localized: Context) {
        val channel = NotificationChannel(
            RELEASE_CHANNEL,
            localized.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = localized.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(event: SocketEvent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val localized = ContextCompat.getContextForLanguage(context)
        updateChannel(localized)
        val title: String
        val body: String
        when (event.type) {
            "economic_event_released" -> {
                val locale = localized.resources.configuration.locales[0]
                title = localized.getString(R.string.notification_released, event.localizedName(locale) ?: localized.getString(R.string.economic_data))
                body = localized.getString(R.string.actual_consensus, event.actual ?: "--", event.consensus ?: "--")
            }
            "analysis_completed" -> {
                title = localized.getString(R.string.notification_analysis_title)
                body = localized.getString(R.string.notification_analysis_body, event.eventId)
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

