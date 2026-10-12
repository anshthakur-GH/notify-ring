package com.anshthakur.notifyring.listener

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Shared AlarmManager scheduling, used by the listener service and the snooze action. */
object AlarmScheduler {

  fun schedule(context: Context, notifKey: String, fireAtMillis: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMillis, pendingIntent(context, notifKey))
  }

  fun cancel(context: Context, notifKey: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(pendingIntent(context, notifKey))
  }

  private fun pendingIntent(context: Context, notifKey: String): PendingIntent {
    val intent = Intent(context, AlarmReceiver::class.java).apply {
      putExtra(AlarmReceiver.EXTRA_NOTIF_KEY, notifKey)
    }
    return PendingIntent.getBroadcast(
      context,
      notifKey.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }
}
