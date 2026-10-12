package com.anshthakur.notifyring.listener

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Pure native listener: no JS/React Native dependency at runtime. Android
 * can bind this service without the app's JS engine running at all, so all
 * watch-list/threshold reads and timer scheduling must stay native.
 */
class NotifyListenerService : NotificationListenerService() {

  private lateinit var db: NotifyRingDb

  override fun onCreate() {
    super.onCreate()
    db = NotifyRingDb.get(this)
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
