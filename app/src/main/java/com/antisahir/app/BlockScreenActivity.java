package com.antisahir.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class BlockScreenActivity extends Activity {

    private TextView tvCountdown;
    private TextView tvBedtime;
    private CountDownTimer countDownTimer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable checkBlockRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Full-screen, show over lock screen, keep screen on
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON      |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD    |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED    |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON      |
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        setContentView(R.layout.activity_block_screen);

        tvCountdown = findViewById(R.id.tvCountdown);
        tvBedtime   = findViewById(R.id.tvBedtime);

        String[] hm = { SleepManager.formatHM(SleepManager.getTodayBedtimeHM(this)) };
        tvBedtime.setText("موعد نومك الليلة: " + hm[0]);

        startCountdown();
        startBlockCheck();
    }

    private void startCountdown() {
        long endMillis = SleepManager.getBlockEndMillis(this);
        long remaining = endMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            finish();
            return;
        }

        countDownTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long hours   = TimeUnit.MILLISECONDS.toHours(millisUntilFinished);
                long minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60;
                long seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60;
                tvCountdown.setText(String.format(Locale.getDefault(),
                        "%02d:%02d:%02d", hours, minutes, seconds));
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("00:00:00");
                finish();
            }
        }.start();
    }

    /**
     * Periodically check if blocking is still active.
     * In case the user changes time or deactivates the app.
     */
    private void startBlockCheck() {
        checkBlockRunnable = new Runnable() {
            @Override
            public void run() {
                if (!SleepManager.isBlockingActiveNow(BlockScreenActivity.this)) {
                    finish();
                    return;
                }
                handler.postDelayed(this, 10_000); // check every 10 seconds
            }
        };
        handler.postDelayed(checkBlockRunnable, 10_000);
    }

    // ── Prevent all hardware-key dismissal ─────────────────────────────────

    @Override
    public void onBackPressed() {
        // Do nothing — back key disabled during block period
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Block volume keys, menu, etc. from closing this screen
        if (keyCode == KeyEvent.KEYCODE_BACK     ||
            keyCode == KeyEvent.KEYCODE_MENU     ||
            keyCode == KeyEvent.KEYCODE_SEARCH   ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            return true; // consumed
        }
        return super.onKeyDown(keyCode, event);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        // If somehow the activity was paused and block is over, close
        if (!SleepManager.isBlockingActiveNow(this)) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        handler.removeCallbacks(checkBlockRunnable);
    }
}
