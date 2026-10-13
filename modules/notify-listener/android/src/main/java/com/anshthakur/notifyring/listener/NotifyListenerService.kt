package com.anshthakur.notifyring.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat

/**
 * Pure native listener: no JS/React Native dependency at runtime. Android
 * can bind this service without the app's JS engine running at all, so all
 * watch-list/threshold reads and timer scheduling must stay native.
 *
 * Also owns the screen-off catch-up: ACTION_SCREEN_OFF can't be declared in
 * the manifest (implicit-broadcast restriction since Android 8), so it's
 * registered here instead, in the one component that's alive the whole time
 * notification access is granted.
 */
class NotifyListenerService : NotificationListenerService() {

  private lateinit var db: NotifyRingDb

  private val screenOffReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      db.getDeferredPending().forEach { pending ->
        db.markTriggered(pending.notifKey)
        RingLauncher.launch(context, pending)
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    db = NotifyRingDb.get(this)
    ContextCompat.registerReceiver(
      this,
      screenOffReceiver,
      IntentFilter(Intent.ACTION_SCREEN_OFF),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    try {
      unregisterReceiver(screenOffReceiver)
    } catch (_: IllegalArgumentException) {
      // not registered - nothing to do
    }
  }

  override fun onListenerConnected() {
    super.onListenerConnected()
    // Re-sync: drop any pending record whose notification already disappeared
    // while the listener was unbound (e.g. during an app update or reboot).
    val active = activeNotifications?.map { it.key }?.toSet() ?: emptySet()
    db.getAllPending().forEach { pending ->
      if (pending.notifKey !in active) {
        db.removePending(pending.notifKey)
        AlarmScheduler.cancel(this, pending.notifKey)
      }
    }
  }

  override fun onNotificationPosted(sbn: StatusBarNotification) {
    val packageName = sbn.packageName ?: return
    val watched = db.getWatchedApp(packageName) ?: return

    // Ongoing/ foreground-service style notifications (music players, call
    // notifications, download progress) are not "missed messages" - skip them.
    if (sbn.isOngoing) return

    val notifKey = sbn.key
    val postedAt = sbn.postTime
    val fireAt = postedAt + watched.thresholdSeconds * 1000L

    db.insertPending(notifKey, packageName, postedAt, fireAt)
    AlarmScheduler.schedule(this, notifKey, fireAt)
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification) {
    val notifKey = sbn.key
    if (db.getPending(notifKey) != null) {
      db.removePending(notifKey)
      AlarmScheduler.cancel(this, notifKey)
    }
  }
}
