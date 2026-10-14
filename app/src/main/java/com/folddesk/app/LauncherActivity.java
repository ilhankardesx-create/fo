package com.folddesk.app;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LauncherActivity extends Activity {
    private final ArrayList<ResolveInfo> allApps = new ArrayList<>();
    private final ArrayList<ResolveInfo> visibleApps = new ArrayList<>();
    private PackageManager packageManager;
    private AppAdapter adapter;
    private EditText search;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean splitBootstrapped;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        immersive();
        packageManager = getPackageManager();
        buildUi();
        loadApps();
        handler.postDelayed(this::bootstrapLaptopSplit, 450);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.setBackgroundColor(Color.rgb(18, 21, 27));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("FOLDDESK");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));

        TextView status = new TextView(this);
        status.setText("LAPTOP LAUNCHER • TOP STAGE");
        status.setTextColor(Color.rgb(148, 210, 255));
        status.setTextSize(11f);
        status.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        header.addView(status, new LinearLayout.LayoutParams(0, dp(42), 1f));
        root.addView(header);

        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(130, 136, 145));
        search.setHint("Uygulama ara...");
        search.setShowSoftInputOnFocus(false);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(roundRect(Color.rgb(31, 35, 43), dp(12), Color.rgb(64, 70, 82), 1));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        GridView grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setVerticalSpacing(dp(8));
        grid.setHorizontalSpacing(dp(8));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setPadding(0, dp(8), 0, dp(4));
        grid.setClipToPadding(false);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < visibleApps.size()) launchApp(visibleApps.get(position));
        });
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private void bootstrapLaptopSplit() {
        if (splitBootstrapped) return;
        splitBootstrapped = true;

        boolean toggled = FoldDeskAccessibilityService.enterSplitScreen();
        long delay = toggled ? 700 : 150;
        handler.postDelayed(() -> {
            try {
                Intent deck = new Intent(this, DeckHostActivity.class);
                deck.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
                ActivityOptions options = ActivityOptions.makeBasic();
                try {
                    Method method = ActivityOptions.class.getDeclaredMethod("setLaunchWindowingMode", int.class);
                    method.setAccessible(true);
                    method.invoke(options, 4); // split-screen secondary on older Samsung/Android builds
                } catch (Throwable ignored) { }
                startActivity(deck, options.toBundle());
            } catch (Throwable e) {
                try { startService(new Intent(this, OverlayControlService.class)); } catch (Throwable ignored) { }
            }
        }, delay);
    }

    private void loadApps() {
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = packageManager.queryIntentActivities(i, PackageManager.MATCH_ALL);
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(packageManager));
        allApps.clear();
        for (ResolveInfo info : apps) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg)) continue;
            allApps.add(info);
        }
        filterApps("");
    }

    private void filterApps(String q) {
        visibleApps.clear();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
        for (ResolveInfo info : allApps) {
            String label = info.loadLabel(packageManager).toString().toLowerCase(Locale.getDefault());
            if (query.isEmpty() || label.contains(query)) visibleApps.add(info);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void launchApp(ResolveInfo info) {
        Intent intent = packageManager.getLaunchIntentForPackage(info.activityInfo.packageName);
        if (intent == null) {
            Toast.makeText(this, "Uygulama açılamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            // When FoldDesk already occupies the top split stage, a normal launch should replace only that stage.
            ActivityOptions options = ActivityOptions.makeBasic();
            try {
                Method method = ActivityOptions.class.getDeclaredMethod("setLaunchWindowingMode", int.class);
                method.setAccessible(true);
                method.invoke(options, 3); // split-screen primary / top stage on older builds
            } catch (Throwable ignored) { }
            startActivity(intent, options.toBundle());
        } catch (Throwable e) {
            try { startActivity(intent); } catch (Throwable ignored) {
                Toast.makeText(this, "Uygulama başlatılamadı", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private GradientDrawable roundRect(int fill, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), strokeColor);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
                cell = new LinearLayout(LauncherActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(5), dp(5), dp(5), dp(5));
                cell.setBackground(roundRect(Color.rgb(24, 28, 34), dp(12), Color.rgb(39, 44, 53), 1));
                icon = new ImageView(LauncherActivity.this);
                cell.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));
                label = new TextView(LauncherActivity.this);
                label.setTextColor(Color.WHITE);
                label.setTextSize(10f);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                cell.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));
                cell.setTag(new View[]{icon, label});
            }
            ResolveInfo info = visibleApps.get(position);
            icon.setImageDrawable(info.loadIcon(packageManager));
            label.setText(info.loadLabel(packageManager));
            return cell;
        }
    }
}
