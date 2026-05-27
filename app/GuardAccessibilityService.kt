package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Calendar

class GuardAccessibilityService : AccessibilityService() {

    // حزم مراقبة الإعدادات لمختلف الشركات المصنعة
    private val settingsPkgs = setOf(
        "com.android.settings",
        "com.samsung.android.lool",
        "com.miui.securitycenter",
        "com.huawei.systemmanager",
        "com.coloros.safecenter",
        "com.oppo.safe",
        "com.vivo.permissionmanager"
    )

    private val installerPkgs = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val prefs = PrefsManager(this)
        if (!prefs.isScheduleActive || !isBlocking(prefs)) return

        val pkg = event.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return

        when (pkg) {
            in installerPkgs -> {
                // منع إلغاء تثبيت التطبيق
                if (containsAny(root, "FocusGuard", "focusguard", "com.focusguard")) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
            in settingsPkgs -> {
                // منع الوصول لإعدادات التطبيق أو صلاحيات المشرف
                if (containsAny(root, "FocusGuard", "focusguard",
                        "Device admin", "مشرف الجهاز", "device_admin")) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
        root.recycle()
    }

    override fun onInterrupt() {}

    private fun containsAny(node: AccessibilityNodeInfo, vararg texts: String): Boolean {
        return texts.any { text ->
            node.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true
        }
    }

    private fun isBlocking(prefs: PrefsManager): Boolean {
        val now = Calendar.getInstance()
        val cur = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val s = prefs.startHour * 60 + prefs.startMinute
        val e = prefs.endHour * 60 + prefs.endMinute
        return if (s <= e) cur in s until e else cur >= s || cur < e
    }
}
