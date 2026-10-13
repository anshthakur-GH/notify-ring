package com.anshthakur.notifyring.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

/**
 * Fired by AlarmManager when a watched notification's threshold elapses.
 * The notification may already be gone (user replied/dismissed it) - the
 * DB is the source of truth, not the alarm firing.
 *
 * Only rings if the screen is off (user is actually away from the phone).
 * If the screen is on, the notification is marked "deferred" and
 * NotifyListenerService's screen-off receiver rings it the moment the
 * screen turns off (unless it gets cleared first).
 */
class AlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val notifKey = intent.getStringExtra(EXTRA_NOTIF_KEY) ?: return
    val db = NotifyRingDb.get(context)
    val pending = db.getPending(notifKey) ?: return // already cleared, nothing to do

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (powerManager.isInteractive) {
      db.markDeferred(notifKey)
      return
    }

    db.markTriggered(notifKey)
    RingLauncher.launch(context, pending)
  }

  companion object {
    const val EXTRA_NOTIF_KEY = "notif_key"
  }
}
