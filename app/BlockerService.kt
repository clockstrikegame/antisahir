package com.focusguard.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import java.util.Calendar

class BlockerService : Service() {

    private lateinit var audio: AudioManager
    private lateinit var prefs: PrefsManager
    private val handler = Handler(Looper.getMainLooper())
    private var headphonesOn = false

    // ── دوري كل ثانيتين لإعادة الكتم إن لزم
    private val ticker = object : Runnable {
        override fun run() {
            if (headphonesOn && isBlocking()) muteAll()
            refreshNotification()
            handler.postDelayed(this, 2000)
        }
    }

    // ── مراقبة توصيل / فصل أجهزة الصوت (سلكية + بلوتوث)
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<AudioDeviceInfo>) {
            updateHeadphones(); if (headphonesOn && isBlocking()) muteAll()
        }
        override fun onAudioDevicesRemoved(removed: Array<AudioDeviceInfo>) {
            updateHeadphones()
        }
    }

    // ── مراقبة تغييرات مستوى الصوت وإعادة الكتم فوراً
    private val volumeObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (headphonesOn && isBlocking()) {
                handler.postDelayed({ muteAll() }, 80)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AUDIO_SERVICE) as AudioManager
        prefs = PrefsManager(this)

        createChannel()
        startForeground(NOTIF_ID, buildNotif())

        updateHeadphones()
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI, true, volumeObserver
        )
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(ticker)
        audio.unregisterAudioDeviceCallback(deviceCallback)
        contentResolver.unregisterContentObserver(volumeObserver)
        scheduleRestart()     // إعادة التشغيل التلقائية دائماً طالما الجدول نشط
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    // ── إعادة تشغيل الخدمة بعد 2 ثانية إن كان الجدول لا يزال نشطاً
    private fun scheduleRestart() {
        if (!prefs.isScheduleActive) return
        val pi = PendingIntent.getService(
            this, 99,
            Intent(this, BlockerService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(ALARM_SERVICE) as AlarmManager).set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 2000, pi
        )
    }

    private fun updateHeadphones() {
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        headphonesOn = outputs.any {
            it.type in listOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET
            )
        }
    }

    private fun muteAll() {
        val streams = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_SYSTEM
        )
        streams.forEach { try { audio.setStreamVolume(it, 0, 0) } catch (_: Exception) {} }
    }

    private fun isBlocking(): Boolean {
        if (!prefs.isScheduleActive) return false
        val now = Calendar.getInstance()
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val s = prefs.startHour * 60 + prefs.startMinute
        val e = prefs.endHour * 60 + prefs.endMinute
        return if (s <= e) cur in s until e else cur >= s || cur < e
    }

    private fun refreshNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif())
    }

    private fun buildNotif(): Notification {
        val blocking = headphonesOn && isBlocking()
        val title = when {
            blocking        -> "🔒 السماعة محجوبة"
            prefs.isScheduleActive -> "FocusGuard — نشط"
            else            -> "FocusGuard"
        }
        val body = when {
            blocking        -> "ينتهي الحجب في ${t(prefs.endHour, prefs.endMinute)}"
            prefs.isScheduleActive -> "تبدأ فترة الحجب في ${t(prefs.startHour, prefs.startMinute)}"
            else            -> "الجدول غير مفعّل"
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH_ID)
            .setContentTitle(title).setContentText(body)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi).setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH_ID, "FocusGuard Service", NotificationManager.IMPORTANCE_HIGH)
                .apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun t(h: Int, m: Int) = String.format("%02d:%02d", h, m)

    companion object {
        const val CH_ID   = "fg_svc"
        const val NOTIF_ID = 7001
    }
}
