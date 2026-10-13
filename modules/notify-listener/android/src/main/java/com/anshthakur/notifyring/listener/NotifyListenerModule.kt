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

  private fun openAppDetailsSettings() {
    val intent = Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }

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
      // Stock Android dialog. Several OEMs (Vivo/FuntouchOS, some MIUI/ColorOS
      // builds) don't ship this screen at all, so the intent silently fails
      // to resolve - fall back to the app's details page, which always exists,
      // so the button never does visibly nothing.
      val directIntent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

      if (directIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(directIntent)
      } else {
        openAppDetailsSettings()
      }
    }

    // OEM battery managers (Vivo iManager, Oppo/Realme/OnePlus ColorOS,
    // Xiaomi MIUI) kill background services unless the app is separately
    // whitelisted for "autostart" - this is outside stock Android's API,
    // each OEM has its own undocumented settings screen.
    Function("openAutoStartSettings") {
      val candidates = listOf(
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.vivo.abe" to "com.vivo.abe.PowerManagerActivity",
        "com.iqoo.powersaving" to "com.iqoo.powersaving.PowerSavingManagerActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
      )

      val launched = candidates.any { (pkg, cls) ->
        try {
          val intent = Intent().apply {
            component = android.content.ComponentName(pkg, cls)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
          if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
          } else {
            false
          }
        } catch (_: Exception) {
          false
        }
      }

      if (!launched) openAppDetailsSettings()
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
