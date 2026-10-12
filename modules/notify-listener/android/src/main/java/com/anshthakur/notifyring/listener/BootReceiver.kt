package com.anshthakur.notifyring.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager alarms are wiped on reboot - reschedule anything still pending. */
class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

    val db = NotifyRingDb.get(context)
    val now = System.currentTimeMillis()

    db.getAllPending().forEach { pending ->
      if (pending.alarmTriggered) {
        db.removePending(pending.notifKey)
      } else {
        // If the threshold already passed while the phone was off, fire almost immediately.
        val fireAt = if (pending.fireAt <= now) now + 2_000 else pending.fireAt
        AlarmScheduler.schedule(context, pending.notifKey, fireAt)
      }
    }
  }
}
