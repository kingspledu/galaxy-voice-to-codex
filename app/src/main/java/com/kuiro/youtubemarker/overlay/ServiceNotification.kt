package com.kuiro.youtubemarker.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import com.kuiro.youtubemarker.MainActivity

object ServiceNotification {
    fun create(context: Context, text: String, stopIntent: Intent): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("capture", "음성 메모 수집", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(context, if (stopIntent.action == "STOP_RECORDING") 2 else 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(context, "capture").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("YouTube Voice Marker").setContentText(text).setContentIntent(open)
            .setOngoing(true).addAction(Notification.Action.Builder(null, "종료", stop).build()).build()
    }
}
