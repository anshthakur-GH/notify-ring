package com.anshthakur.notifyring.listener

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class NotifyListenerModule : Module() {

  private val context get() = requireNotNull(appContext.reactContext)
  private val db get() = NotifyRingDb.get(context)

  override fun definition() = ModuleDefinition {
    Name("NotifyListener")

    // --- permission / settings state, all read-only checks (no requesting) ---

    Function("isNotificationAccessGranted") {
      val enabledListeners = Settings.Secure.getString(
        context.contentResolver, "enabled_notification_listeners"
      ) ?: ""
      enabledListeners.contains(context.packageName)
    }

    Function("openNotificationAccessSettings") {
      val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }

    Function("isIgnoringBatteryOptimizations") {
      val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
      pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    Function("requestIgnoreBatteryOptimizations") {
      val intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
    }

    Function("canScheduleExactAlarms") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.canScheduleExactAlarms()
      } else {
        true
      }
    }

    Function("openExactAlarmSettings") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
      }
    }

    // --- installed apps (for the picker UI) ---

    Function("getInstalledApps") {
      val pm = context.packageManager
      val launchIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
      pm.queryIntentActivities(launchIntent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .map { pkg ->
          val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
          } catch (_: Exception) {
            pkg
          }
          mapOf("packageName" to pkg, "label" to label)
        }
        .sortedBy { (it["label"] as String).lowercase() }
    }

    // --- watch list CRUD, backed by native SQLite ---

    Function("getWatchedApps") {
      db.getAllWatchedApps().map {
        mapOf(
          "packageName" to it.packageName,
          "thresholdSeconds" to it.thresholdSeconds,
          "enabled" to it.enabled
        )
      }
    }

    Function("setWatchedApp") { packageName: String, thresholdSeconds: Int, enabled: Boolean ->
      db.setWatchedApp(packageName, thresholdSeconds, enabled)
    }

    Function("removeWatchedApp") { packageName: String ->
      db.removeWatchedApp(packageName)
    }
  }
}
