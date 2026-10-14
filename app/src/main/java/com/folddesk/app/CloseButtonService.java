package com.folddesk.app;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class CloseButtonService extends Service {
    private WindowManager windowManager;
    private View closeView;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Settings.canDrawOverlays(this)) showCloseButton();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (closeView == null && Settings.canDrawOverlays(this)) showCloseButton();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (windowManager != null && closeView != null) {
            try { windowManager.removeView(closeView); } catch (Throwable ignored) { }
        }
        closeView = null;
        super.onDestroy();
    }

    private void showCloseButton() {
        if (windowManager == null || closeView != null) return;

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenHeight = dm.heightPixels;
        int deckHeight = Math.max(dp(240), screenHeight / 2);

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(17f);
        close.setGravity(Gravity.CENTER);
        close.setTypeface(null, android.graphics.Typeface.BOLD);
        close.setBackground(roundRect(Color.rgb(210, 48, 48), dp(12)));
        close.setElevation(dp(8));
        close.setOnClickListener(v -> closeFoldDesk());
        closeView = close;

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(42), dp(42), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.x = dp(18);
        lp.y = Math.max(dp(8), deckHeight - dp(50));
        try { windowManager.addView(closeView, lp); } catch (Throwable ignored) { closeView = null; }
    }

    private void closeFoldDesk() {
        try { stopService(new Intent(this, OverlayControlService.class)); } catch (Throwable ignored) { }
        DeckHostActivity.closeDeckHost();
        FoldDeskAccessibilityService.exitSplitScreen();
        stopSelf();
    }

    private GradientDrawable roundRect(int fill, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
