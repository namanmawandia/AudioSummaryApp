package com.example.audiosummeryapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.audiosummeryapp.MainActivity
import com.example.audiosummeryapp.R

object RecordingNotificationManager {

    const val CHANNEL_ID         = "recording_channel"
    const val NOTIFICATION_ID    = 1001

    const val ACTION_PAUSE       = "action_pause"
    const val ACTION_RESUME      = "action_resume"
    const val ACTION_STOP        = "action_stop"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio recording status"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    //Notification variants

    fun buildRecordingNotification(context: Context, elapsedTime: String): Notification {
        return base(context)
            .setContentTitle("Recording")
            .setContentText("Duration: $elapsedTime")
            .addAction(pauseAction(context))
            .addAction(stopAction(context))
            .build()
    }

    fun buildPausedNotification(context: Context, reason: String): Notification {
        return base(context)
            .setContentTitle(reason)
            .setContentText("Tap Resume to continue")
            .addAction(resumeAction(context))
            .addAction(stopAction(context))
            .build()
    }

    fun buildStoppedNotification(context: Context, reason: String): Notification {
        return base(context)
            .setContentTitle(reason)
            .setContentText("Recording has ended")
            .build()
    }

    fun buildSourceChangedNotification(context: Context, message: String): Notification {
        return base(context)
            .setContentTitle("Audio source changed")
            .setContentText(message)
            .addAction(stopAction(context))
            .build()
    }

    // Internal helpers

    private fun base(context: Context): NotificationCompat.Builder {
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_notification)   // add a 24dp mic vector drawable
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun pauseAction(context: Context) = NotificationCompat.Action(
        0, "Pause", servicePendingIntent(context, ACTION_PAUSE, 10)
    )

    private fun resumeAction(context: Context) = NotificationCompat.Action(
        0, "Resume", servicePendingIntent(context, ACTION_RESUME, 11)
    )

    private fun stopAction(context: Context) = NotificationCompat.Action(
        0, "Stop", servicePendingIntent(context, ACTION_STOP, 12)
    )

    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RecordingService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
