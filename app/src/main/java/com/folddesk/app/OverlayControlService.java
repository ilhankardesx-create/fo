package com.folddesk.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayControlService extends Service {
    private WindowManager windowManager;
    private View deckView;
    private CursorView cursorView;
    private WindowManager.LayoutParams cursorParams;
    private int screenWidth;
    private int screenHeight;
    private int deckHeight;
    private float cursorX;
    private float cursorY;
    private boolean shift;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Settings.canDrawOverlays(this)) showOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (deckView == null && Settings.canDrawOverlays(this)) showOverlay();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    private void showOverlay() {
        if (windowManager == null || deckView != null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        deckHeight = Math.max(dp(240), screenHeight / 2);
        cursorX = screenWidth * 0.5f;
        cursorY = Math.max(dp(40), (screenHeight - deckHeight) * 0.45f);

        deckView = buildDeck();
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams deckParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                deckHeight,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        deckParams.gravity = Gravity.BOTTOM | Gravity.START;
        windowManager.addView(deckView, deckParams);

        cursorView = new CursorView(this);
        cursorParams = new WindowManager.LayoutParams(
                dp(30), dp(38), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        cursorParams.gravity = Gravity.TOP | Gravity.START;
        updateCursorWindow();
        windowManager.addView(cursorView, cursorParams);
    }

    private void removeOverlay() {
        if (windowManager == null) return;
        if (deckView != null) {
            try { windowManager.removeView(deckView); } catch (Throwable ignored) { }
            deckView = null;
        }
        if (cursorView != null) {
            try { windowManager.removeView(cursorView); } catch (Throwable ignored) { }
            cursorView = null;
        }
    }

    private View buildDeck() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.setBackgroundColor(Color.rgb(12, 14, 18));

        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(6), dp(6), dp(6), dp(6));
        keyboard.setBackground(roundRect(Color.rgb(20, 23, 29), dp(16), Color.rgb(47, 52, 62), 1));
        addKeyboardRow(keyboard, "ESC", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "⌫");
        addKeyboardRow(keyboard, "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P");
        addKeyboardRow(keyboard, "A", "S", "D", "F", "G", "H", "J", "K", "L");
        addKeyboardRow(keyboard, "⇧", "Z", "X", "C", "V", "B", "N", "M", ",", ".");
        addKeyboardRow(keyboard, "CTRL", "ALT", "SPACE", "←", "↑", "↓", "→", "ENTER");

        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3.0f);
        keyboardParams.setMarginEnd(dp(8));
        root.addView(keyboard, keyboardParams);

        LinearLayout padPanel = new LinearLayout(this);
        padPanel.setOrientation(LinearLayout.VERTICAL);
        padPanel.setPadding(dp(6), dp(6), dp(6), dp(6));
        padPanel.setBackground(roundRect(Color.rgb(20, 23, 29), dp(16), Color.rgb(47, 52, 62), 1));

        TextView label = new TextView(this);
        label.setText("TOUCHPAD");
        label.setTextColor(Color.rgb(175, 181, 191));
        label.setTextSize(10f);
        label.setGravity(Gravity.CENTER);
        padPanel.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        TrackpadView trackpad = new TrackpadView(this);
        padPanel.addView(trackpad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout clicks = new LinearLayout(this);
        clicks.setOrientation(LinearLayout.HORIZONTAL);
        clicks.setPadding(0, dp(5), 0, 0);

        Button left = new Button(this);
        left.setText("L");
        left.setTextSize(11f);
        left.setOnClickListener(v -> FoldDeskAccessibilityService.tap(cursorX, cursorY));
        clicks.addView(left, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button right = new Button(this);
        right.setText("R");
        right.setTextSize(11f);
        right.setOnClickListener(v -> FoldDeskAccessibilityService.longPress(cursorX, cursorY));
        clicks.addView(right, new LinearLayout.LayoutParams(0, dp(42), 1f));

        padPanel.addView(clicks, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(padPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));
        return root;
    }

    private void addKeyboardRow(LinearLayout keyboard, String... keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rowParams.bottomMargin = dp(4);
        keyboard.addView(row, rowParams);

        for (String key : keys) {
            TextView button = new TextView(this);
            button.setText(key);
            button.setTextColor(Color.WHITE);
            button.setTextSize(key.length() > 3 ? 9f : 12f);
            button.setGravity(Gravity.CENTER);
            button.setBackground(roundRect(Color.rgb(38, 42, 50), dp(7), Color.rgb(67, 73, 84), 1));
            button.setOnClickListener(v -> handleOverlayKey(key));
            LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT,
                    "SPACE".equals(key) ? 2.8f : ("ENTER".equals(key) ? 1.5f : 1f));
            keyParams.setMargins(dp(2), 0, dp(2), 0);
            row.addView(button, keyParams);
        }
    }

    private void handleOverlayKey(String key) {
        if ("⇧".equals(key)) {
            shift = !shift;
            return;
        }
        if ("CTRL".equals(key) || "ALT".equals(key) || "ESC".equals(key) ||
                "←".equals(key) || "↑".equals(key) || "↓".equals(key) || "→".equals(key)) {
            return;
        }

        String send = key;
        if (key.length() == 1 && Character.isLetter(key.charAt(0))) {
            send = shift ? key.toUpperCase() : key.toLowerCase();
            shift = false;
        }

        boolean ok = FoldDeskImeService.sendKey(send);
        if (!ok) {
            if ("⌫".equals(send)) {
                FoldDeskAccessibilityService.backspaceFallback();
            } else if ("SPACE".equals(send)) {
                FoldDeskAccessibilityService.setTextFallback(" ");
            } else if (!"ENTER".equals(send)) {
                FoldDeskAccessibilityService.setTextFallback(send);
            }
        }
    }

    private void moveCursor(float dx, float dy) {
        cursorX += dx * 1.55f;
        cursorY += dy * 1.55f;
        float maxX = Math.max(dp(4), screenWidth - dp(34));
        float maxY = Math.max(dp(4), screenHeight - deckHeight - dp(42));
        cursorX = Math.max(dp(2), Math.min(cursorX, maxX));
        cursorY = Math.max(dp(2), Math.min(cursorY, maxY));
        updateCursorWindow();
    }

    private void updateCursorWindow() {
        if (cursorParams == null) return;
        cursorParams.x = Math.round(cursorX);
        cursorParams.y = Math.round(cursorY);
        if (cursorView != null && cursorView.isAttachedToWindow()) {
            try { windowManager.updateViewLayout(cursorView, cursorParams); } catch (Throwable ignored) { }
        }
    }

    private GradientDrawable roundRect(int fill, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class TrackpadView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastX;
        private float lastY;
        private float downX;
        private float downY;
        private float lastTwoFingerY;
        private boolean twoFinger;

        TrackpadView(Context context) {
            super(context);
            setBackground(roundRect(Color.rgb(29, 33, 40), dp(14), Color.rgb(67, 73, 84), 1));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(Color.rgb(78, 84, 96));
            paint.setStrokeWidth(dp(1));
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            canvas.drawLine(cx - dp(14), cy, cx + dp(14), cy, paint);
            canvas.drawLine(cx, cy - dp(14), cx, cy + dp(14), paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int count = event.getPointerCount();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                lastX = downX = event.getX();
                lastY = downY = event.getY();
                twoFinger = false;
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN && count >= 2) {
                twoFinger = true;
                lastTwoFingerY = (event.getY(0) + event.getY(1)) / 2f;
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (count >= 2 || twoFinger) {
                    if (count >= 2) {
                        float avgY = (event.getY(0) + event.getY(1)) / 2f;
                        float dy = avgY - lastTwoFingerY;
                        if (Math.abs(dy) > dp(5)) {
                            float gestureX = Math.max(dp(80), Math.min(cursorX, screenWidth - dp(80)));
                            float gestureY = Math.max(dp(140), Math.min(cursorY, screenHeight - deckHeight - dp(140)));
                            FoldDeskAccessibilityService.scroll(gestureX, gestureY, dy);
                            lastTwoFingerY = avgY;
                        }
                    }
                    return true;
                }

                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                lastX = event.getX();
                lastY = event.getY();
                moveCursor(dx, dy);
                return true;
            }

            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (!twoFinger) {
                    float travel = Math.abs(event.getX() - downX) + Math.abs(event.getY() - downY);
                    if (travel < dp(12)) FoldDeskAccessibilityService.tap(cursorX, cursorY);
                }
                twoFinger = false;
                return true;
            }
            return true;
        }
    }

    private class CursorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        CursorView(Context context) { super(context); }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Path path = new Path();
            path.moveTo(dp(2), dp(2));
            path.lineTo(dp(2), dp(30));
            path.lineTo(dp(10), dp(23));
            path.lineTo(dp(16), dp(36));
            path.lineTo(dp(22), dp(33));
            path.lineTo(dp(16), dp(20));
            path.lineTo(dp(28), dp(19));
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setShadowLayer(dp(3), 0, 0, Color.BLACK);
            setLayerType(View.LAYER_TYPE_SOFTWARE, paint);
            canvas.drawPath(path, paint);
        }
    }
}
