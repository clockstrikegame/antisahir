package com.antisahir.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class SleepManager {

    private static final String PREFS = "antisahir_prefs";

    // SharedPreferences keys
    public static final String KEY_ACTIVE               = "is_active";
    public static final String KEY_START_NIGHT_MINS     = "start_night_mins";
    public static final String KEY_TARGET_NIGHT_MINS    = "target_night_mins";
    public static final String KEY_TOTAL_DAYS           = "total_days";
    public static final String KEY_BLOCK_DURATION_MINS  = "block_duration_mins";
    public static final String KEY_START_EPOCH_DAY      = "start_epoch_day";
    public static final String KEY_BLOCKED_APPS         = "blocked_apps";

    // Alarm request code
    private static final int ALARM_REQUEST_CODE = 555;

    // ─────────────────────────────────────────────────────────────────────────
    // Time helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Convert wall-clock hour:minute to "night minutes" (minutes since 18:00).
     * Times before 06:00 are treated as continuing the previous night.
     * Examples:  23:00 → 300,   00:00 → 360,   02:00 → 480,   06:00 → 720
     */
    public static int toNightMinutes(int hour, int minute) {
        int total = hour * 60 + minute;
        if (total < 6 * 60) total += 24 * 60;   // before 6 AM → add 24 h
        return total - 18 * 60;                  // subtract 6 PM offset
    }

    /**
     * Convert night minutes back to wall-clock [hour, minute].
     */
    public static int[] fromNightMinutes(int nightMins) {
        int total = nightMins + 18 * 60;
        if (total >= 24 * 60) total -= 24 * 60;
        return new int[]{total / 60, total % 60};
    }

    public static String formatHM(int[] hm) {
        return String.format("%02d:%02d", hm[0], hm[1]);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveSettings(Context ctx,
                                    int startNightMins,
                                    int targetNightMins,
                                    int totalDays,
                                    int blockDurationMins) {
        long epochDay = System.currentTimeMillis() / 86_400_000L;
        prefs(ctx).edit()
                .putInt(KEY_START_NIGHT_MINS,    startNightMins)
                .putInt(KEY_TARGET_NIGHT_MINS,   targetNightMins)
                .putInt(KEY_TOTAL_DAYS,          totalDays)
                .putInt(KEY_BLOCK_DURATION_MINS, blockDurationMins)
                .putLong(KEY_START_EPOCH_DAY,    epochDay)
                .putBoolean(KEY_ACTIVE,          true)
                .apply();
    }

    public static boolean isActive(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ACTIVE, false);
    }

    public static void deactivate(Context ctx) {
        prefs(ctx).edit().putBoolean(KEY_ACTIVE, false).apply();
        cancelAlarm(ctx);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule calculations
    // ─────────────────────────────────────────────────────────────────────────

    public static int getDayIndex(Context ctx) {
        long startDay = prefs(ctx).getLong(KEY_START_EPOCH_DAY, 0L);
        if (startDay == 0L) return 0;
        long today = System.currentTimeMillis() / 86_400_000L;
        return (int) Math.max(0, today - startDay);
    }

    /** Return today's bedtime in night-minutes, clamped to target. */
    public static int getTodayBedtimeNightMins(Context ctx) {
        SharedPreferences p = prefs(ctx);
        int start     = p.getInt(KEY_START_NIGHT_MINS,  480); // default 2 AM
        int target    = p.getInt(KEY_TARGET_NIGHT_MINS, 300); // default 11 PM
        int totalDays = p.getInt(KEY_TOTAL_DAYS,         30);

        if (totalDays <= 0 || start <= target) return target;

        int diff       = start - target;
        int stepPerDay = Math.max(1, diff / totalDays);
        int dayIndex   = getDayIndex(ctx);
        int todayMins  = start - (dayIndex * stepPerDay);
        return Math.max(todayMins, target);
    }

    public static int[] getTodayBedtimeHM(Context ctx) {
        return fromNightMinutes(getTodayBedtimeNightMins(ctx));
    }

    public static int getBlockDurationMins(Context ctx) {
        return prefs(ctx).getInt(KEY_BLOCK_DURATION_MINS, 120);
    }

    public static int getRemainingDays(Context ctx) {
        int totalDays = prefs(ctx).getInt(KEY_TOTAL_DAYS, 30);
        return Math.max(0, totalDays - getDayIndex(ctx));
    }

    public static boolean isGoalReached(Context ctx) {
        int target = prefs(ctx).getInt(KEY_TARGET_NIGHT_MINS, 300);
        return getTodayBedtimeNightMins(ctx) <= target;
    }

    /** Progress from 0.0 (just started) to 1.0 (goal reached). */
    public static float getProgress(Context ctx) {
        int totalDays = prefs(ctx).getInt(KEY_TOTAL_DAYS, 30);
        if (totalDays <= 0) return 1f;
        int elapsed = getDayIndex(ctx);
        return Math.min(1f, (float) elapsed / totalDays);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Blocking state
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isBlockingActiveNow(Context ctx) {
        if (!isActive(ctx)) return false;
        int bedNightMins  = getTodayBedtimeNightMins(ctx);
        int blockDuration = getBlockDurationMins(ctx);
        Calendar now      = Calendar.getInstance();
        int nowNightMins  = toNightMinutes(
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE));
        return nowNightMins >= bedNightMins && nowNightMins < bedNightMins + blockDuration;
    }

    /** Milliseconds until current blocking window ends (0 if not blocking). */
    public static long getBlockEndMillis(Context ctx) {
        int bedNightMins  = getTodayBedtimeNightMins(ctx);
        int blockDuration = getBlockDurationMins(ctx);
        int endNightMins  = bedNightMins + blockDuration;
        int[] endHM       = fromNightMinutes(endNightMins);

        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, endHM[0]);
        end.set(Calendar.MINUTE,      endHM[1]);
        end.set(Calendar.SECOND,      0);
        end.set(Calendar.MILLISECOND, 0);

        // If end time is before midnight but current clock is past midnight, add nothing;
        // if end time has already passed today add a day (edge case).
        if (end.getTimeInMillis() < System.currentTimeMillis()) {
            end.add(Calendar.DAY_OF_MONTH, 1);
        }
        return end.getTimeInMillis();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Blocked-apps helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static void saveBlockedApps(Context ctx, Set<String> packages) {
        prefs(ctx).edit().putStringSet(KEY_BLOCKED_APPS, packages).apply();
    }

    public static Set<String> getBlockedApps(Context ctx) {
        Set<String> raw = prefs(ctx).getStringSet(KEY_BLOCKED_APPS, null);
        return raw != null ? new HashSet<>(raw) : new HashSet<>();
    }

    public static boolean isAppBlocked(Context ctx, String packageName) {
        if (packageName == null) return false;
        if (packageName.equals(ctx.getPackageName())) return false;
        return getBlockedApps(ctx).contains(packageName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AlarmManager helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Schedule today's bedtime alarm (only if it hasn't passed yet).
     * Called on app launch and after reboot.
     */
    public static void scheduleNextAlarm(Context ctx) {
        if (!isActive(ctx)) return;

        int[] hm = getTodayBedtimeHM(ctx);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hm[0]);
        cal.set(Calendar.MINUTE,      hm[1]);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);

        // If today's bedtime is already past, do nothing — DailyAlarmReceiver
        // will schedule tomorrow's alarm when it fires.
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) return;

        setAlarm(ctx, cal.getTimeInMillis());
    }

    /**
     * Schedule tomorrow's bedtime alarm.
     * Called from DailyAlarmReceiver after blocking starts.
     */
    public static void scheduleNextDayAlarm(Context ctx) {
        if (!isActive(ctx)) return;

        SharedPreferences p = prefs(ctx);
        int start     = p.getInt(KEY_START_NIGHT_MINS,  480);
        int target    = p.getInt(KEY_TARGET_NIGHT_MINS, 300);
        int totalDays = p.getInt(KEY_TOTAL_DAYS,         30);

        int nextDayIndex = getDayIndex(ctx) + 1;
        if (start <= target || totalDays <= 0) return;

        int diff       = start - target;
        int stepPerDay = Math.max(1, diff / totalDays);
        int nextMins   = Math.max(start - nextDayIndex * stepPerDay, target);

        int[] hm = fromNightMinutes(nextMins);

        Calendar cal = Calendar.getInstance();
        // Always schedule for "next calendar day" since this is called from tonight's alarm.
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, hm[0]);
        cal.set(Calendar.MINUTE,      hm[1]);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);

        setAlarm(ctx, cal.getTimeInMillis());
    }

    private static void setAlarm(Context ctx, long triggerAtMillis) {
        Intent intent = new Intent(ctx, DailyAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
        }
    }

    private static void cancelAlarm(Context ctx) {
        Intent intent = new Intent(ctx, DailyAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pi);
    }
}
