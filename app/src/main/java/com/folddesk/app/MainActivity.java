package com.folddesk.app;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.util.Consumer;
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;
import androidx.window.layout.WindowInfoTracker;
import androidx.window.layout.WindowLayoutInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final ArrayList<ResolveInfo> allApps = new ArrayList<>();
    private final ArrayList<ResolveInfo> visibleApps = new ArrayList<>();

    private PackageManager packageManager;
    private AppAdapter adapter;
    private EditText searchBox;
    private TextView modeLabel;
    private FrameLayout topArea;
    private View hingeBar;
    private LinearLayout root;
    private TextView cursor;

    private WindowInfoTrackerCallbackAdapter windowInfoTracker;
    private final LayoutStateChangeCallback layoutCallback = new LayoutStateChangeCallback();

    private float cursorX = 140f;
    private float cursorY = 140f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );

        packageManager = getPackageManager();
        buildInterface();
        loadApps();

        windowInfoTracker = new WindowInfoTrackerCallbackAdapter(
                WindowInfoTracker.getOrCreate(this)
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        windowInfoTracker.addWindowLayoutInfoListener(this, Runnable::run, layoutCallback);
    }

    @Override
    protected void onStop() {
        windowInfoTracker.removeWindowLayoutInfoListener(layoutCallback);
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
            topArea.post(() -> {
                if (cursorX <= 0 || cursorY <= 0) {
                    cursorX = topArea.getWidth() * 0.5f;
                    cursorY = topArea.getHeight() * 0.5f;
                }
                updateCursor();
            });
        }
    }

    private void buildInterface() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 12, 15));
        setContentView(root);

        topArea = new FrameLayout(this);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        root.addView(topArea, topParams);

        LinearLayout desktop = new LinearLayout(this);
        desktop.setOrientation(LinearLayout.VERTICAL);
        desktop.setPadding(dp(14), dp(10), dp(14), dp(8));
        desktop.setBackgroundColor(Color.rgb(18, 21, 27));
        topArea.addView(desktop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("FOLDDESK");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));

        modeLabel = new TextView(this);
        modeLabel.setText("PROTOTYPE • OPEN MODE");
        modeLabel.setTextColor(Color.rgb(148, 210, 255));
        modeLabel.setTextSize(12f);
        modeLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(modeLabel, new LinearLayout.LayoutParams(0, dp(42), 1f));

        desktop.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setHintTextColor(Color.rgb(130, 136, 145));
        searchBox.setHint("Uygulama ara...");
        searchBox.setTextSize(14f);
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.setShowSoftInputOnFocus(false);
        searchBox.setBackground(roundRect(Color.rgb(31, 35, 43), dp(12), Color.rgb(64, 70, 82), 1));
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        desktop.addView(searchBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ));

        GridView appGrid = new GridView(this);
        appGrid.setNumColumns(6);
        appGrid.setVerticalSpacing(dp(8));
        appGrid.setHorizontalSpacing(dp(8));
        appGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        appGrid.setPadding(0, dp(8), 0, dp(4));
        appGrid.setClipToPadding(false);
        adapter = new AppAdapter();
        appGrid.setAdapter(adapter);
        appGrid.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleApps.size()) {
                launchApp(visibleApps.get(position));
            }
        });
        desktop.addView(appGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        TextView taskbar = new TextView(this);
        taskbar.setText("⌂  FoldDesk     •     Uygulamalar üst alanda, kontrol paneli alt alanda");
        taskbar.setTextColor(Color.rgb(190, 195, 203));
        taskbar.setTextSize(11f);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding(dp(12), 0, dp(12), 0);
        taskbar.setBackground(roundRect(Color.rgb(26, 29, 35), dp(10), Color.TRANSPARENT, 0));
        desktop.addView(taskbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)
        ));

        cursor = new TextView(this);
        cursor.setText("●");
        cursor.setTextColor(Color.WHITE);
        cursor.setTextSize(22f);
        cursor.setGravity(Gravity.CENTER);
        cursor.setShadowLayer(8f, 0f, 0f, Color.BLACK);
        FrameLayout.LayoutParams cursorParams = new FrameLayout.LayoutParams(dp(28), dp(28));
        topArea.addView(cursor, cursorParams);

        hingeBar = new View(this);
        hingeBar.setBackgroundColor(Color.rgb(55, 61, 72));
        root.addView(hingeBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        ));

        LinearLayout controlDeck = new LinearLayout(this);
        controlDeck.setOrientation(LinearLayout.HORIZONTAL);
        controlDeck.setPadding(dp(8), dp(8), dp(8), dp(8));
        controlDeck.setBackgroundColor(Color.rgb(12, 14, 18));
        root.addView(controlDeck, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        LinearLayout keyboard = buildKeyboard();
        LinearLayout.LayoutParams keyboardParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 3.0f);
        keyboardParams.setMarginEnd(dp(8));
        controlDeck.addView(keyboard, keyboardParams);

        LinearLayout trackpadPanel = buildTrackpad();
        controlDeck.addView(trackpadPanel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));
    }

    private LinearLayout buildKeyboard() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(6), dp(6), dp(6), dp(6));
        keyboard.setBackground(roundRect(Color.rgb(20, 23, 29), dp(16), Color.rgb(47, 52, 62), 1));

        addKeyboardRow(keyboard, "ESC", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "⌫");
        addKeyboardRow(keyboard, "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P");
        addKeyboardRow(keyboard, "A", "S", "D", "F", "G", "H", "J", "K", "L");
        addKeyboardRow(keyboard, "⇧", "Z", "X", "C", "V", "B", "N", "M", ",", ".");
        addKeyboardRow(keyboard, "CTRL", "ALT", "SPACE", "←", "↑", "↓", "→", "ENTER");
        return keyboard;
    }

    private void addKeyboardRow(LinearLayout keyboard, String... keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        rowParams.bottomMargin = dp(4);
        keyboard.addView(row, rowParams);

        for (String key : keys) {
            TextView button = new TextView(this);
            button.setText(key);
            button.setTextColor(Color.WHITE);
            button.setTextSize(key.length() > 3 ? 9f : 12f);
            button.setGravity(Gravity.CENTER);
            button.setBackground(roundRect(Color.rgb(38, 42, 50), dp(7), Color.rgb(67, 73, 84), 1));
            button.setOnClickListener(v -> handleKey(key));
            LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    "SPACE".equals(key) ? 2.8f : ("ENTER".equals(key) ? 1.5f : 1f));
            keyParams.setMargins(dp(2), 0, dp(2), 0);
            row.addView(button, keyParams);
        }
    }

    private LinearLayout buildTrackpad() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(6), dp(6), dp(6), dp(6));
        panel.setBackground(roundRect(Color.rgb(20, 23, 29), dp(16), Color.rgb(47, 52, 62), 1));

        TextView label = new TextView(this);
        label.setText("TOUCHPAD");
        label.setTextColor(Color.rgb(175, 181, 191));
        label.setTextSize(10f);
        label.setGravity(Gravity.CENTER);
        panel.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26)
        ));

        TrackpadView pad = new TrackpadView(this);
        panel.addView(pad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        LinearLayout clicks = new LinearLayout(this);
        clicks.setOrientation(LinearLayout.HORIZONTAL);
        clicks.setPadding(0, dp(5), 0, 0);

        Button left = new Button(this);
        left.setText("L");
        left.setTextSize(11f);
        left.setOnClickListener(v -> pulseCursor("Sol tık"));
        clicks.addView(left, new LinearLayout.LayoutParams(0, dp(42), 1f));

        Button right = new Button(this);
        right.setText("R");
        right.setTextSize(11f);
        right.setOnClickListener(v -> Toast.makeText(this, "Sağ tık • prototip", Toast.LENGTH_SHORT).show());
        clicks.addView(right, new LinearLayout.LayoutParams(0, dp(42), 1f));

        panel.addView(clicks, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ));
        return panel;
    }

    private void handleKey(String key) {
        String current = searchBox.getText().toString();
        switch (key) {
            case "ESC":
                searchBox.setText("");
                break;
            case "⌫":
                if (!current.isEmpty()) {
                    searchBox.setText(current.substring(0, current.length() - 1));
                    searchBox.setSelection(searchBox.length());
                }
                break;
            case "SPACE":
                searchBox.append(" ");
                break;
            case "ENTER":
                if (!visibleApps.isEmpty()) launchApp(visibleApps.get(0));
                break;
            case "←": moveCursor(-dp(28), 0); break;
            case "→": moveCursor(dp(28), 0); break;
            case "↑": moveCursor(0, -dp(28)); break;
            case "↓": moveCursor(0, dp(28)); break;
            case "CTRL":
            case "ALT":
            case "⇧":
                modeLabel.setText("PROTOTYPE • " + key + " KEY");
                break;
            default:
                searchBox.append(key.toLowerCase(Locale.getDefault()));
                break;
        }
    }

    private void loadApps() {
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL);
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(packageManager));

        allApps.clear();
        for (ResolveInfo info : apps) {
            if (info.activityInfo == null) continue;
            if (getPackageName().equals(info.activityInfo.packageName)) continue;
            allApps.add(info);
        }
        filterApps("");
    }

    private void filterApps(String query) {
        visibleApps.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        for (ResolveInfo info : allApps) {
            String label = info.loadLabel(packageManager).toString().toLowerCase(Locale.getDefault());
            if (q.isEmpty() || label.contains(q)) visibleApps.add(info);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void launchApp(ResolveInfo info) {
        String pkg = info.activityInfo.packageName;
        Intent intent = packageManager.getLaunchIntentForPackage(pkg);
        if (intent == null) {
            Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show();
            return;
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT |
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        try {
            int width = Math.max(1, topArea.getWidth());
            int height = Math.max(1, topArea.getHeight());
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchBounds(new Rect(0, 0, width, height));
            startActivity(intent, options.toBundle());
        } catch (Exception e) {
            try {
                startActivity(intent);
            } catch (Exception ignored) {
                Toast.makeText(this, "Uygulama başlatılamadı", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void moveCursor(float dx, float dy) {
        cursorX += dx;
        cursorY += dy;
        updateCursor();
    }

    private void updateCursor() {
        float maxX = Math.max(0, topArea.getWidth() - dp(28));
        float maxY = Math.max(0, topArea.getHeight() - dp(28));
        cursorX = Math.max(0, Math.min(cursorX, maxX));
        cursorY = Math.max(0, Math.min(cursorY, maxY));
        cursor.setX(cursorX);
        cursor.setY(cursorY);
    }

    private void pulseCursor(String message) {
        cursor.animate().scaleX(1.7f).scaleY(1.7f).setDuration(70)
                .withEndAction(() -> cursor.animate().scaleX(1f).scaleY(1f).setDuration(90).start())
                .start();
        modeLabel.setText("PROTOTYPE • " + message.toUpperCase(Locale.getDefault()));
    }

    private void applyFold(FoldingFeature feature) {
        if (feature == null) {
            modeLabel.setText("PROTOTYPE • OPEN MODE");
            resetEqualSplit();
            return;
        }

        boolean halfOpened = FoldingFeature.State.HALF_OPENED.equals(feature.getState());
        boolean horizontal = FoldingFeature.Orientation.HORIZONTAL.equals(feature.getOrientation());

        if (halfOpened && horizontal) {
            modeLabel.setText("LAPTOP MODE • HALF OPENED ✓");
            Rect bounds = feature.getBounds();
            int rootHeight = root.getHeight();
            if (bounds.top > 0 && rootHeight > bounds.bottom) {
                LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, bounds.top
                );
                topArea.setLayoutParams(topParams);

                LinearLayout.LayoutParams hingeParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(dp(3), bounds.height())
                );
                hingeBar.setLayoutParams(hingeParams);
            }
        } else {
            modeLabel.setText(horizontal ? "FOLD DETECTED • OPEN" : "BOOK POSTURE • OPEN");
            resetEqualSplit();
        }
    }

    private void resetEqualSplit() {
        topArea.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        hingeBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)
        ));
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

    private class LayoutStateChangeCallback implements Consumer<WindowLayoutInfo> {
        @Override
        public void accept(WindowLayoutInfo layoutInfo) {
            FoldingFeature fold = null;
            for (DisplayFeature feature : layoutInfo.getDisplayFeatures()) {
                if (feature instanceof FoldingFeature) {
                    fold = (FoldingFeature) feature;
                    break;
                }
            }
            FoldingFeature finalFold = fold;
            runOnUiThread(() -> applyFold(finalFold));
        }
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return visibleApps.size(); }
        @Override public Object getItem(int position) { return visibleApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;

            if (convertView instanceof LinearLayout && convertView.getTag() instanceof View[]) {
                cell = (LinearLayout) convertView;
                View[] views = (View[]) cell.getTag();
                icon = (ImageView) views[0];
                label = (TextView) views[1];
            } else {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(5), dp(5), dp(5), dp(5));
                cell.setBackground(roundRect(Color.rgb(24, 28, 34), dp(12), Color.rgb(39, 44, 53), 1));

                icon = new ImageView(MainActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

                label = new TextView(MainActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(10f);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                cell.addView(label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(24)
                ));
                cell.setTag(new View[]{icon, label});
            }

            ResolveInfo info = visibleApps.get(position);
            icon.setImageDrawable(info.loadIcon(packageManager));
            label.setText(info.loadLabel(packageManager));
            return cell;
        }
    }

    private class TrackpadView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastX;
        private float lastY;
        private float downX;
        private float downY;

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
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = downX = event.getX();
                    lastY = downY = event.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    lastX = event.getX();
                    lastY = event.getY();
                    moveCursor(dx * 1.65f, dy * 1.65f);
                    return true;
                case MotionEvent.ACTION_UP:
                    float travel = Math.abs(event.getX() - downX) + Math.abs(event.getY() - downY);
                    if (travel < dp(12)) pulseCursor("Touchpad tık");
                    return true;
                default:
                    return true;
            }
        }
    }
}
