package com.anshthakur.notifyring.listener

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen, call-style alarm. Built with plain views (no XML/layout
 * inflation, no image assets) to keep this module's footprint tiny.
 */
class RingActivity : AppCompatActivity() {

  private var ringtone: Ringtone? = null
  private var vibrator: Vibrator? = null
  private lateinit var notifKey: String
  private lateinit var packageName_: String

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    notifKey = intent.getStringExtra(EXTRA_NOTIF_KEY) ?: ""
    packageName_ = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

    showOverLockScreen()
    buildUi()
    startRinging()
  }

  private fun showOverLockScreen() {
    window.addFlags(
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
      val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
      keyguardManager.requestDismissKeyguard(this, null)
    }
  }

  private fun buildUi() {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setBackgroundColor(Color.parseColor("#1A1A2E"))
      setPadding(48, 48, 48, 48)
    }

    val title = TextView(this).apply {
      text = "Missed notification"
      textSize = 24f
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
    }

    val subtitle = TextView(this).apply {
      text = "${appLabel()} is waiting for you"
      textSize = 16f
      setTextColor(Color.LTGRAY)
      gravity = Gravity.CENTER
      setPadding(0, 16, 0, 96)
    }

    val dismissButton = Button(this).apply {
      text = "Dismiss"
      setOnClickListener { finishAndStop() }
    }

    val snoozeButton = Button(this).apply {
      text = "Snooze 5 min"
      setOnClickListener { snooze() }
    }

    root.addView(title)
    root.addView(subtitle)
    root.addView(dismissButton)
    root.addView(snoozeButton, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = 24 })

    setContentView(root)
  }

  private fun appLabel(): String = try {
    val pm = packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName_, 0)).toString()
  } catch (_: Exception) {
    packageName_
  }

  private fun startRinging() {
    val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
    ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        audioAttributes = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ALARM)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      }
      play()
    }

    val pattern = longArrayOf(0, 500, 500)
    vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
      vm.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
  }

  private fun stopRinging() {
    ringtone?.stop()
    vibrator?.cancel()
  }

  private fun snooze() {
    val db = NotifyRingDb.get(this)
    val snoozeSeconds = 5 * 60
    val fireAt = System.currentTimeMillis() + snoozeSeconds * 1000L
    db.insertPending(notifKey, packageName_, System.currentTimeMillis(), fireAt)
    AlarmScheduler.schedule(this, notifKey, fireAt)
    finishAndStop()
  }

  private fun finishAndStop() {
    stopRinging()
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.cancel(notifKey.hashCode())
    if (notifKey.isNotEmpty()) {
      NotifyRingDb.get(this).removePending(notifKey)
    }
    finish()
  }

  override fun onDestroy() {
    super.onDestroy()
    stopRinging()
  }

  override fun onBackPressed() {
    // Prevent dismissing the alarm with the back button - must use a button.
  }

  companion object {
    const val EXTRA_NOTIF_KEY = "notif_key"
    const val EXTRA_PACKAGE_NAME = "package_name"
  }
}
