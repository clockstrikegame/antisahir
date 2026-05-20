package com.antisahir.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

public class AppBlockAccessibilityService extends AccessibilityService {

    // Package names that should NEVER be blocked
    private static final String[] SYSTEM_EXEMPT = {
            "com.android.settings",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.sec.android.app.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher"
    };

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes  = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags       = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        // Never block our own app or system-exempt packages
        if (pkg.equals(getPackageName())) return;
        if (isSystemExempt(pkg)) return;

        // Check if blocking window is active right now
        if (!SleepManager.isBlockingActiveNow(this)) return;

        // Check if this specific app is in the blocked list
        if (SleepManager.isAppBlocked(this, pkg)) {
            launchBlockScreen();
        }
    }

    private void launchBlockScreen() {
        Intent intent = new Intent(this, BlockScreenActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private boolean isSystemExempt(String pkg) {
        // Check our own package
        if (pkg.equals(getPackageName())) return true;
        // Check known launchers / settings
        for (String exempt : SYSTEM_EXEMPT) {
            if (pkg.equals(exempt)) return true;
        }
        // Generic launcher check
        if (pkg.contains("launcher") || pkg.contains("home")) return true;
        return false;
    }

    @Override
    public void onInterrupt() {
        // Required override — no action needed
    }
}
