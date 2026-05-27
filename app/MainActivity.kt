package com.focusguard.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import com.focusguard.app.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: PrefsManager
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComp: ComponentName

    private val handler = Handler(Looper.getMainLooper())
    private val uiClock = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        prefs     = PrefsManager(this)
        dpm       = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComp = ComponentName(this, AdminReceiver::class.java)

        setupClicks()
    }

    override fun onResume()  { super.onResume();  refresh(); handler.post(uiClock) }
    override fun onPause()   { super.onPause();   handler.removeCallbacks(uiClock) }

    // ══════════════════════════════════════════
    private fun setupClicks() {
        b.btnStartTime.setOnClickListener { if (!blocking()) pickTime(true)  }
        b.btnEndTime.setOnClickListener   { if (!blocking()) pickTime(false) }

        b.btnActivate.setOnClickListener {
            if (blocking()) {
                Toast.makeText(this, "🔒 لا يمكن الإيقاف أثناء فترة الحجب", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!adminOn()) {
                Toast.makeText(this, "⚠ فعّل صلاحيات المشرف أولاً", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (prefs.isScheduleActive) {
                prefs.isScheduleActive = false
                stopService(Intent(this, BlockerService::class.java))
                Toast.makeText(this, "تم إيقاف الجدول", Toast.LENGTH_SHORT).show()
            } else {
                prefs.isScheduleActive = true
                startForegroundService(this, Intent(this, BlockerService::class.java))
                Toast.makeText(this, "✓ تم تفعيل الجدول", Toast.LENGTH_SHORT).show()
            }
            refresh()
        }

        b.btnAdmin.setOnClickListener {
            if (!adminOn()) startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "مطلوب لمنع حذف التطبيق أثناء فترة الحجب")
                })
        }

        b.btnAccess.setOnClickListener {
            if (!accessOn()) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    // ══════════════════════════════════════════
    private fun pickTime(isStart: Boolean) {
        val h = if (isStart) prefs.startHour   else prefs.endHour
        val m = if (isStart) prefs.startMinute else prefs.endMinute
        val fmt = if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H

        MaterialTimePicker.Builder()
            .setTimeFormat(fmt).setHour(h).setMinute(m)
            .setTitleText(if (isStart) "وقت بدء الحجب" else "وقت انتهاء الحجب")
            .build().also { picker ->
                picker.addOnPositiveButtonClickListener {
                    if (isStart) { prefs.startHour = picker.hour; prefs.startMinute = picker.minute }
                    else         { prefs.endHour   = picker.hour; prefs.endMinute   = picker.minute }
                    refresh()
                }
                picker.show(supportFragmentManager, if (isStart) "tp_s" else "tp_e")
            }
    }

    // ══════════════════════════════════════════
    private fun refresh() {
        val isBlock  = blocking()
        val isActive = prefs.isScheduleActive
        val adminOk  = adminOn()
        val accessOk = accessOn()

        // أزرار الوقت
        b.btnStartTime.text = fmt(prefs.startHour, prefs.startMinute)
        b.btnEndTime.text   = fmt(prefs.endHour,   prefs.endMinute)
        b.btnStartTime.isEnabled = !isBlock
        b.btnEndTime.isEnabled   = !isBlock

        // بطاقة الحالة
        when {
            isBlock -> {
                b.tvStatus.text    = "السماعة محجوبة"
                b.tvStatusSub.text = "ينتهي الحجب في ${fmt(prefs.endHour, prefs.endMinute)}"
                tintDot(R.color.accent_danger)
                b.tvRemainingLabel.visibility = View.VISIBLE
                b.tvRemaining.visibility      = View.VISIBLE
                b.tvRemaining.text            = remaining()
            }
            isActive -> {
                b.tvStatus.text    = "الجدول مفعَّل"
                b.tvStatusSub.text = "الحجب يبدأ في ${fmt(prefs.startHour, prefs.startMinute)}"
                tintDot(R.color.accent_success)
                b.tvRemainingLabel.visibility = View.GONE
                b.tvRemaining.visibility      = View.GONE
            }
            else -> {
                b.tvStatus.text    = "غير نشط"
                b.tvStatusSub.text = "اضبط الوقت وفعّل الجدول"
                tintDot(R.color.text_muted)
                b.tvRemainingLabel.visibility = View.GONE
                b.tvRemaining.visibility      = View.GONE
            }
        }

        // زر التفعيل
        b.btnActivate.isEnabled = !isBlock
        b.btnActivate.alpha     = if (isBlock) 0.45f else 1f
        b.btnActivate.text      = when {
            isBlock   -> "🔒  الحجب نشط — لا يمكن الإيقاف"
            isActive  -> "إيقاف الجدول"
            else      -> "تفعيل الجدول"
        }
        b.btnActivate.backgroundTintList = color(when {
            isBlock  -> R.color.accent_danger
            isActive -> R.color.bg_card
            else     -> R.color.accent_primary
        })

        // أزرار الصلاحيات
        permBtn(b.btnAdmin,
            adminOk, "✓ صلاحيات المشرف مفعّلة", "تفعيل صلاحيات المشرف")
        permBtn(b.btnAccess,
            accessOk, "✓ خدمة الحماية مفعّلة", "تفعيل خدمة الحماية")
    }

    private fun permBtn(btn: MaterialButton, done: Boolean, doneText: String, pendText: String) {
        btn.text      = if (done) doneText else pendText
        btn.isEnabled = !done
        btn.alpha     = if (done) 0.5f else 1f
        btn.strokeColor = color(if (done) R.color.accent_success else R.color.divider)
    }

    private fun tintDot(colorRes: Int) {
        b.statusDot.backgroundTintList = color(colorRes)
    }

    // ══════════════════════════════════════════
    private fun blocking(): Boolean {
        if (!prefs.isScheduleActive) return false
        val now = Calendar.getInstance()
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val s   = prefs.startHour * 60 + prefs.startMinute
        val e   = prefs.endHour   * 60 + prefs.endMinute
        return if (s <= e) cur in s until e else cur >= s || cur < e
    }

    private fun adminOn() = dpm.isAdminActive(adminComp)

    private fun accessOn(): Boolean = try {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        enabled.contains("${packageName}/${GuardAccessibilityService::class.java.name}")
    } catch (_: Exception) { false }

    private fun remaining(): String {
        val now  = Calendar.getInstance()
        val curS = now.get(Calendar.HOUR_OF_DAY) * 3600 +
                   now.get(Calendar.MINUTE)      * 60   +
                   now.get(Calendar.SECOND)
        val endS = prefs.endHour * 3600 + prefs.endMinute * 60
        var diff = if (endS > curS) endS - curS else 86400 - curS + endS
        val h = diff / 3600; diff %= 3600
        val m = diff / 60;   val s = diff % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    private fun fmt(h: Int, m: Int) = String.format("%02d:%02d", h, m)
    private fun color(res: Int) = ColorStateList.valueOf(ContextCompat.getColor(this, res))
}
