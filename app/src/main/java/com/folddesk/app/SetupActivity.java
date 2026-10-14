package com.folddesk.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class SetupActivity extends Activity {
    private Button overlayButton;
    private Button accessibilityButton;
    private Button imeButton;
    private Button launchButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateState();
        if (allReady() && getIntent() != null && getIntent().hasCategory(Intent.CATEGORY_HOME)) {
            startActivity(new Intent(this, LauncherActivity.class));
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(34), dp(26), dp(28));
        root.setBackgroundColor(0xff11151b);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("FOLDDESK v0.4");
        title.setTextColor(0xffffffff);
        title.setTextSize(28f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView info = new TextView(this);
        info.setText("Laptop modu için üç izin gerekiyor. v0.4 uygulamaları gerçek split-screen üst bölmesine, FoldDesk kontrol destesini ise alt bölmeye yerleştirmeyi dener.");
        info.setTextColor(0xffb7bec8);
        info.setTextSize(15f);
        info.setPadding(0, dp(16), 0, dp(20));
        root.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        overlayButton = button(root, "1. Ekran üstünde göster");
        overlayButton.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        accessibilityButton = button(root, "2. Touchpad erişilebilirlik servisi");
        accessibilityButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        imeButton = button(root, "3. FoldDesk klavyesi");
        imeButton.setOnClickListener(v -> {
            if (!isImeEnabled()) {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            } else {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            }
        });

        launchButton = button(root, "FOLDDESK'İ BAŞLAT");
        launchButton.setOnClickListener(v -> {
            if (allReady()) {
                startActivity(new Intent(this, LauncherActivity.class));
                finish();
            }
        });
    }

    private Button button(LinearLayout parent, String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        lp.bottomMargin = dp(12);
        parent.addView(b, lp);
        return b;
    }

    private void updateState() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean access = isAccessibilityEnabled();
        boolean imeEnabled = isImeEnabled();
        boolean imeSelected = isImeSelected();

        overlayButton.setText(overlay ? "✓ Ekran üstünde göster açık" : "1. Ekran üstünde göster");
        accessibilityButton.setText(access ? "✓ Touchpad servisi açık" : "2. Touchpad erişilebilirlik servisi");
        if (!imeEnabled) {
            imeButton.setText("3. FoldDesk klavyesini etkinleştir");
        } else if (!imeSelected) {
            imeButton.setText("3. FoldDesk klavyesini seç");
        } else {
            imeButton.setText("✓ FoldDesk klavyesi seçili");
        }

        launchButton.setEnabled(allReady());
        launchButton.setAlpha(allReady() ? 1f : 0.45f);
    }

    private boolean allReady() {
        return Settings.canDrawOverlays(this) && isAccessibilityEnabled() && isImeEnabled() && isImeSelected();
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String expected = new ComponentName(this, FoldDeskAccessibilityService.class).flattenToString();
        return enabled.toLowerCase().contains(expected.toLowerCase());
    }

    private boolean isImeEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        List<InputMethodInfo> list = imm.getEnabledInputMethodList();
        for (InputMethodInfo info : list) {
            if (getPackageName().equals(info.getPackageName())) return true;
        }
        return false;
    }

    private boolean isImeSelected() {
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return current != null && current.startsWith(getPackageName() + "/");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
