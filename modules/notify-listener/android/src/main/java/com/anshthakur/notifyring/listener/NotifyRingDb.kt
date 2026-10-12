package com.anshthakur.notifyring.listener

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain android.database SQLite store, independent of the RN/JS runtime.
 * The NotificationListenerService and AlarmReceiver run even when the JS
 * engine is not alive, so persistence must not depend on it.
 */
class NotifyRingDb private constructor(context: Context) :
  SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE watched_apps (
        package_name TEXT PRIMARY KEY,
        threshold_seconds INTEGER NOT NULL,
        enabled INTEGER NOT NULL DEFAULT 1
      )
      """.trimIndent()
    )
    db.execSQL(
      """
      CREATE TABLE pending_notifications (
        notif_key TEXT PRIMARY KEY,
        package_name TEXT NOT NULL,
        posted_at INTEGER NOT NULL,
        fire_at INTEGER NOT NULL,
        alarm_triggered INTEGER NOT NULL DEFAULT 0
      )
      """.trimIndent()
    )
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE IF EXISTS watched_apps")
    db.execSQL("DROP TABLE IF EXISTS pending_notifications")
    onCreate(db)
  }

  // ---- watched apps ----

  fun setWatchedApp(packageName: String, thresholdSeconds: Int, enabled: Boolean) {
    val values = ContentValues().apply {
      put("package_name", packageName)
      put("threshold_seconds", thresholdSeconds)
      put("enabled", if (enabled) 1 else 0)
    }
    writableDatabase.insertWithOnConflict(
      "watched_apps", null, values, SQLiteDatabase.CONFLICT_REPLACE
    )
  }

  fun removeWatchedApp(packageName: String) {
    writableDatabase.delete("watched_apps", "package_name = ?", arrayOf(packageName))
  }

  fun getWatchedApp(packageName: String): WatchedApp? {
    readableDatabase.query(
      "watched_apps", null, "package_name = ? AND enabled = 1", arrayOf(packageName),
      null, null, null
    ).use { c ->
      if (c.moveToFirst()) {
        return WatchedApp(
          packageName = c.getString(c.getColumnIndexOrThrow("package_name")),
          thresholdSeconds = c.getInt(c.getColumnIndexOrThrow("threshold_seconds")),
          enabled = true
        )
      }
    }
    return null
  }

  fun getAllWatchedApps(): List<WatchedApp> {
    val result = mutableListOf<WatchedApp>()
    readableDatabase.query("watched_apps", null, null, null, null, null, null).use { c ->
      while (c.moveToNext()) {
        result.add(
          WatchedApp(
            packageName = c.getString(c.getColumnIndexOrThrow("package_name")),
            thresholdSeconds = c.getInt(c.getColumnIndexOrThrow("threshold_seconds")),
            enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1
          )
        )
      }
    }
    return result
  }

  // ---- pending notifications ----

  fun insertPending(notifKey: String, packageName: String, postedAt: Long, fireAt: Long) {
    val values = ContentValues().apply {
      put("notif_key", notifKey)
      put("package_name", packageName)
      put("posted_at", postedAt)
      put("fire_at", fireAt)
      put("alarm_triggered", 0)
    }
    writableDatabase.insertWithOnConflict(
      "pending_notifications", null, values, SQLiteDatabase.CONFLICT_REPLACE
    )
  }

  fun removePending(notifKey: String) {
    writableDatabase.delete("pending_notifications", "notif_key = ?", arrayOf(notifKey))
  }

  fun getPending(notifKey: String): PendingNotification? {
    readableDatabase.query(
      "pending_notifications", null, "notif_key = ?", arrayOf(notifKey),
      null, null, null
    ).use { c ->
      if (c.moveToFirst()) return c.toPendingNotification()
    }
    return null
  }

  fun getAllPending(): List<PendingNotification> {
    val result = mutableListOf<PendingNotification>()
    readableDatabase.query("pending_notifications", null, null, null, null, null, null).use { c ->
      while (c.moveToNext()) result.add(c.toPendingNotification())
    }
    return result
  }

  fun markTriggered(notifKey: String) {
    val values = ContentValues().apply { put("alarm_triggered", 1) }
    writableDatabase.update("pending_notifications", values, "notif_key = ?", arrayOf(notifKey))
  }

  private fun android.database.Cursor.toPendingNotification() = PendingNotification(
    notifKey = getString(getColumnIndexOrThrow("notif_key")),
    packageName = getString(getColumnIndexOrThrow("package_name")),
    postedAt = getLong(getColumnIndexOrThrow("posted_at")),
    fireAt = getLong(getColumnIndexOrThrow("fire_at")),
    alarmTriggered = getInt(getColumnIndexOrThrow("alarm_triggered")) == 1
  )

  companion object {
    private const val DB_NAME = "notify_ring.db"
    private const val DB_VERSION = 1

    @Volatile private var instance: NotifyRingDb? = null

    fun get(context: Context): NotifyRingDb =
      instance ?: synchronized(this) {
        instance ?: NotifyRingDb(context).also { instance = it }
      }
  }
}

data class WatchedApp(
  val packageName: String,
  val thresholdSeconds: Int,
  val enabled: Boolean
)

data class PendingNotification(
  val notifKey: String,
  val packageName: String,
  val postedAt: Long,
  val fireAt: Long,
  val alarmTriggered: Boolean
)
