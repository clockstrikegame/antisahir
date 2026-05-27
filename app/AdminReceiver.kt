package com.focusguard.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.util.Calendar

class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "✓ صلاحيات المشرف مُفعَّلة", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val prefs = PrefsManager(context)
        return if (prefs.isScheduleActive && isBlocking(prefs))
            "⚠ لا يمكن التعطيل أثناء فترة حجب نشطة!"
        else
            "هل تريد تعطيل حارس التركيز؟"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "تم تعطيل صلاحيات المشرف", Toast.LENGTH_SHORT).show()
    }

    private fun isBlocking(prefs: PrefsManager): Boolean {
        val now = Calendar.getInstance()
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val s = prefs.startHour * 60 + prefs.startMinute
        val e = prefs.endHour * 60 + prefs.endMinute
        return if (s <= e) cur in s until e else cur >= s || cur < e
    }
}
