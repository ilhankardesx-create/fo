package com.folddesk.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

public class DeckHostActivity extends Activity {
    private static volatile DeckHostActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 14, 18));
        setContentView(root);
        startDeckOverlay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        instance = this;
        startDeckOverlay();
    }

    @Override
    protected void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void startDeckOverlay() {
        if (Settings.canDrawOverlays(this)) {
            try { startService(new Intent(this, OverlayControlService.class)); } catch (Throwable ignored) { }
            try { startService(new Intent(this, CloseButtonService.class)); } catch (Throwable ignored) { }
        }
    }

    public static void closeDeckHost() {
        DeckHostActivity a = instance;
        if (a != null) {
            a.runOnUiThread(() -> {
                try { a.finishAndRemoveTask(); } catch (Throwable ignored) { a.finish(); }
            });
        }
    }

    @Override
    public void onBackPressed() {
        try { stopService(new Intent(this, OverlayControlService.class)); } catch (Throwable ignored) { }
        try { stopService(new Intent(this, CloseButtonService.class)); } catch (Throwable ignored) { }
        FoldDeskAccessibilityService.exitSplitScreen();
        finish();
    }
}
