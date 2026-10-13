package com.anshthakur.notifyring.listener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Shared by AlarmReceiver (fires on-time, screen off) and the screen-off
 *  catch-up in NotifyListenerService (fires a threshold that elapsed while
 *  the screen was on and got deferred). */
object RingLauncher {

  private const val CHANNEL_ID = "notify_ring_alerts"

  fun launch(context: Context, pending: PendingNotification) {
    val fullScreenIntent = Intent(context, RingActivity::class.java).apply {
      putExtra(RingActivity.EXTRA_NOTIF_KEY, pending.notifKey)
      putExtra(RingActivity.EXTRA_PACKAGE_NAME, pending.packageName)
      addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP
      )
    }

    val fullScreenPendingIntent = PendingIntent.getActivity(
      context,
      pending.notifKey.hashCode(),
      fullScreenIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Missed notification alerts",
        NotificationManager.IMPORTANCE_HIGH
      ).apply {
        description = "Rings when a watched notification goes unanswered"
      }
      notificationManager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
      .setContentTitle("Missed notification")
      .setContentText(appLabel(context, pending.packageName) + " needs your attention")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setFullScreenIntent(fullScreenPendingIntent, true)
      .setAutoCancel(true)
      .setOngoing(true)
      .build()

    notificationManager.notify(pending.notifKey.hashCode(), notification)

    // Also try to launch directly - covers devices/states where the
    // full-screen intent notification alone doesn't pop the activity.
    try {
      context.startActivity(fullScreenIntent)
    } catch (_: Exception) {
      // Background activity-launch restrictions on some OEMs/Android versions
      // can block this; the full-screen-intent notification above is the fallback.
    }
  }

  private fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
  } catch (_: Exception) {
    packageName
  }
}
