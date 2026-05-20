package com.antisahir.app;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class MainActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────────────────
    private TextView tvCurrentBedtime;
    private TextView tvTargetBedtime;
    private TextView tvDaysValue;
    private TextView tvBlockDuration;
    private SeekBar  seekDays;
    private Button   btnToggle;
    private Button   btnSelectApps;
    private Button   btnAccessibility;
    private Button   btnDeviceAdmin;
    private TextView tvTodayBedtime;
    private TextView tvRemainingDays;
    private ProgressBar progressBar;
    private CardView cardStatus;
    private TextView tvAdminStatus;
    private TextView tvAccessibilityStatus;

    // ── State ──────────────────────────────────────────────────────────────
    private int currentHour   = 2;   // 02:00
    private int currentMinute = 0;
    private int targetHour    = 23;  // 23:00
    private int targetMinute  = 0;
    private int totalDays     = 30;
    private int blockDuration = 120; // minutes

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupListeners();
        loadCurrentSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatusCards();
        refreshScheduleCard();
    }

    // ── View binding ───────────────────────────────────────────────────────

    private void bindViews() {
        tvCurrentBedtime       = findViewById(R.id.tvCurrentBedtime);
        tvTargetBedtime        = findViewById(R.id.tvTargetBedtime);
        tvDaysValue            = findViewById(R.id.tvDaysValue);
        tvBlockDuration        = findViewById(R.id.tvBlockDuration);
        seekDays               = findViewById(R.id.seekDays);
        btnToggle              = findViewById(R.id.btnToggle);
        btnSelectApps          = findViewById(R.id.btnSelectApps);
        btnAccessibility       = findViewById(R.id.btnAccessibility);
        btnDeviceAdmin         = findViewById(R.id.btnDeviceAdmin);
        tvTodayBedtime         = findViewById(R.id.tvTodayBedtime);
        tvRemainingDays        = findViewById(R.id.tvRemainingDays);
        progressBar            = findViewById(R.id.progressBar);
        cardStatus             = findViewById(R.id.cardStatus);
        tvAdminStatus          = findViewById(R.id.tvAdminStatus);
        tvAccessibilityStatus  = findViewById(R.id.tvAccessibilityStatus);
    }

    // ── Listeners ──────────────────────────────────────────────────────────

    private void setupListeners() {

        // Pick current bedtime
        tvCurrentBedtime.setOnClickListener(v ->
                new TimePickerDialog(this, (tp, h, m) -> {
                    currentHour   = h;
                    currentMinute = m;
                    tvCurrentBedtime.setText(fmt(h, m));
                }, currentHour, currentMinute, true).show()
        );

        // Pick target bedtime
        tvTargetBedtime.setOnClickListener(v ->
                new TimePickerDialog(this, (tp, h, m) -> {
                    targetHour   = h;
                    targetMinute = m;
                    tvTargetBedtime.setText(fmt(h, m));
                }, targetHour, targetMinute, true).show()
        );

        // Block duration cycle: 30 → 60 → 90 → 120 → 180 → 30 min
        tvBlockDuration.setOnClickListener(v -> {
            int[] options = {30, 60, 90, 120, 180};
            for (int i = 0; i < options.length; i++) {
                if (blockDuration == options[i]) {
                    blockDuration = options[(i + 1) % options.length];
                    tvBlockDuration.setText(blockDuration + " دقيقة");
                    return;
                }
            }
            blockDuration = 120;
            tvBlockDuration.setText("120 دقيقة");
        });

        // Days seekbar
        seekDays.setMax(89);  // 1..90 days
        seekDays.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                totalDays = progress + 1;
                tvDaysValue.setText(totalDays + " يوم");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // Select apps to block
        btnSelectApps.setOnClickListener(v ->
                startActivity(new Intent(this, AppSelectionActivity.class))
        );

        // Enable Accessibility Service
        btnAccessibility.setOnClickListener(v -> {
            Toast.makeText(this,
                    "ابحث عن «مضاد السهر» وفعّله", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        // Enable Device Admin
        btnDeviceAdmin.setOnClickListener(v -> {
            ComponentName admin = new ComponentName(this, AdminReceiver.class);
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "يمنع حذف التطبيق قبل الوقت المبرمج");
            startActivity(intent);
        });

        // Start / Stop toggle
        btnToggle.setOnClickListener(v -> {
            if (SleepManager.isActive(this)) {
                confirmDeactivate();
            } else {
                validateAndActivate();
            }
        });
    }

    // ── Activate / Deactivate ─────────────────────────────────────────────

    private void validateAndActivate() {
        int startNightMins  = SleepManager.toNightMinutes(currentHour, currentMinute);
        int targetNightMins = SleepManager.toNightMinutes(targetHour, targetMinute);

        if (startNightMins <= targetNightMins) {
            Toast.makeText(this,
                    "⚠️ موعد سهرك الحالي يجب أن يكون متأخراً عن الهدف",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (SleepManager.getBlockedApps(this).isEmpty()) {
            Toast.makeText(this,
                    "⚠️ يرجى اختيار تطبيق واحد على الأقل للحجب",
                    Toast.LENGTH_LONG).show();
            return;
        }

        if (!isAccessibilityEnabled()) {
            Toast.makeText(this,
                    "⚠️ يجب تفعيل خدمة إمكانية الوصول أولاً",
                    Toast.LENGTH_LONG).show();
            return;
        }

        SleepManager.saveSettings(this, startNightMins, targetNightMins, totalDays, blockDuration);
        SleepManager.scheduleNextAlarm(this);
        refreshScheduleCard();
        refreshStatusCards();
        btnToggle.setText("⏹ إيقاف البرنامج");
        Toast.makeText(this, "✅ البرنامج مُفعَّل!", Toast.LENGTH_SHORT).show();
    }

    private void confirmDeactivate() {
        new AlertDialog.Builder(this)
                .setTitle("إيقاف البرنامج")
                .setMessage("هل أنت متأكد أنك تريد إيقاف مضاد السهر؟\nستفقد كل التقدم المُحقَّق.")
                .setPositiveButton("نعم، إيقاف", (d, w) -> {
                    SleepManager.deactivate(this);
                    btnToggle.setText("▶ تفعيل البرنامج");
                    refreshScheduleCard();
                    Toast.makeText(this, "تم إيقاف البرنامج", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    // ── UI refresh ────────────────────────────────────────────────────────

    private void loadCurrentSettings() {
        seekDays.setProgress(totalDays - 1);
        tvDaysValue.setText(totalDays + " يوم");
        tvCurrentBedtime.setText(fmt(currentHour, currentMinute));
        tvTargetBedtime.setText(fmt(targetHour, targetMinute));
        tvBlockDuration.setText(blockDuration + " دقيقة");
    }

    private void refreshScheduleCard() {
        boolean active = SleepManager.isActive(this);
        btnToggle.setText(active ? "⏹ إيقاف البرنامج" : "▶ تفعيل البرنامج");

        if (active) {
            int[] hm = SleepManager.getTodayBedtimeHM(this);
            tvTodayBedtime.setText("موعد نومك الليلة: " + fmt(hm[0], hm[1]));
            int remaining = SleepManager.getRemainingDays(this);
            tvRemainingDays.setText(remaining + " يوم متبقٍ للهدف");
            int progress = (int) (SleepManager.getProgress(this) * 100);
            progressBar.setProgress(progress);
        } else {
            tvTodayBedtime.setText("البرنامج غير مُفعَّل");
            tvRemainingDays.setText("—");
            progressBar.setProgress(0);
        }
    }

    private void refreshStatusCards() {
        boolean accessOk = isAccessibilityEnabled();
        boolean adminOk  = isDeviceAdminEnabled();

        tvAccessibilityStatus.setText("خدمة إمكانية الوصول: " + (accessOk ? "✅ مُفعَّلة" : "❌ معطَّلة"));
        tvAdminStatus.setText("مدير الجهاز: " + (adminOk ? "✅ مُفعَّل" : "❌ معطَّل"));

        btnAccessibility.setVisibility(accessOk ? View.GONE : View.VISIBLE);
        btnDeviceAdmin.setVisibility(adminOk   ? View.GONE : View.VISIBLE);
    }

    // ── Permission checks ─────────────────────────────────────────────────

    private boolean isAccessibilityEnabled() {
        String service = getPackageName() + "/" +
                AppBlockAccessibilityService.class.getCanonicalName();
        try {
            int enabled = Settings.Secure.getInt(
                    getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (enabled != 1) return false;
            String settingValue = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(settingValue)) return false;
            return settingValue.contains(service);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDeviceAdminEnabled() {
        DevicePolicyManager dpm =
                (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        return dpm != null && dpm.isAdminActive(admin);
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private String fmt(int h, int m) {
        return String.format("%02d:%02d", h, m);
    }
}
