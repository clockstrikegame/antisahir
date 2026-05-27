package com.focusguard.app

import android.content.Context

class PrefsManager(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var startHour: Int
        get() = prefs.getInt(K_S_H, 22)
        set(v) = prefs.edit().putInt(K_S_H, v).apply()

    var startMinute: Int
        get() = prefs.getInt(K_S_M, 0)
        set(v) = prefs.edit().putInt(K_S_M, v).apply()

    var endHour: Int
        get() = prefs.getInt(K_E_H, 6)
        set(v) = prefs.edit().putInt(K_E_H, v).apply()

    var endMinute: Int
        get() = prefs.getInt(K_E_M, 0)
        set(v) = prefs.edit().putInt(K_E_M, v).apply()

    var isScheduleActive: Boolean
        get() = prefs.getBoolean(K_ACTIVE, false)
        set(v) = prefs.edit().putBoolean(K_ACTIVE, v).apply()

    companion object {
        private const val NAME    = "fg_prefs"
        private const val K_S_H   = "start_hour"
        private const val K_S_M   = "start_min"
        private const val K_E_H   = "end_hour"
        private const val K_E_M   = "end_min"
        private const val K_ACTIVE = "active"
    }
}
