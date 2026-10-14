package com.max2idea.android.limbo.machine;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.limbo.emu.main.arm.LimboEmuActivity;
import com.max2idea.android.limbo.files.FileInstaller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * PocketDroid's user-facing launcher.
 * The legacy emulator configuration UI remains hidden; PocketDroid owns
 * guest selection and applies ARM64 VM defaults automatically.
 */
public class PocketDroidActivity extends LimboEmuActivity {

    private static final int PICK_GUEST_IMAGE = 4201;
    private static final String PREFS = "pocketdroid";
    private static final String PREF_IMAGE_PATH = "guest_image_path";
    private static final String PREF_IMAGE_NAME = "guest_image_name";
    private static final String VM_NAME = "PocketDroidARM64";

    private SharedPreferences prefs;
    private TextView stateText;
    private TextView fileText;
    private Button primaryButton;
    private Button changeButton;
    private ProgressBar progress;
    private String imagePath;
    private String imageName;
    private boolean copying;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MachineController.getInstance().removeOnStatusChangeListener(this);
        MachineController.getInstance().removeOnEventListener(this);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        imagePath = prefs.getString(PREF_IMAGE_PATH, null);
        imageName = prefs.getString(PREF_IMAGE_NAME, null);

        setupFullscreen();
        buildPocketDroidUi();
        refreshUi();
    }

    private void setupFullscreen() {
        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(Color.BLACK);
        window.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp((int) radiusDp));
        return drawable;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private void buildPocketDroidUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(54), dp(28), dp(36));
        root.setBackgroundColor(Color.rgb(8, 10, 14));

        TextView badge = text("P", 34, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rounded(Color.rgb(43, 105, 255), 24));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(74), dp(74));
        badgeParams.bottomMargin = dp(18);
        root.addView(badge, badgeParams);

        TextView title = text("PocketDroid", 32, Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = text("Telefonun içinde ikinci Android", 16, Color.rgb(167, 174, 190));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(8);
        subtitleParams.bottomMargin = dp(34);
        root.addView(subtitle, subtitleParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(22), dp(24), dp(22), dp(24));
        card.setBackground(rounded(Color.rgb(20, 23, 31), 22));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(24);
        root.addView(card, cardParams);

        stateText = text("", 18, Color.WHITE);
        card.addView(stateText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        fileText = text("", 13, Color.rgb(143, 151, 168));
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fileParams.topMargin = dp(9);
        card.addView(fileText, fileParams);

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        progressParams.gravity = Gravity.CENTER;
        progressParams.topMargin = dp(16);
        card.addView(progress, progressParams);

        primaryButton = new Button(this);
        primaryButton.setTextColor(Color.WHITE);
        primaryButton.setTextSize(17);
        primaryButton.setAllCaps(false);
        primaryButton.setBackground(rounded(Color.rgb(43, 105, 255), 18));
        primaryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (copying) return;
                if (imagePath == null || !(new File(imagePath).exists())) {
                    pickImage();
                } else {
                    startGuest();
                }
            }
        });
        root.addView(primaryButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        changeButton = new Button(this);
        changeButton.setText("Android imajını değiştir");
        changeButton.setTextColor(Color.rgb(175, 184, 205));
        changeButton.setTextSize(14);
        changeButton.setAllCaps(false);
        changeButton.setBackgroundColor(Color.TRANSPARENT);
        changeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!copying) pickImage();
            }
        });
        LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        changeParams.topMargin = dp(8);
        root.addView(changeButton, changeParams);

        badge.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(PocketDroidActivity.this,
                        "Motor tanılama ekranı", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(PocketDroidActivity.this, LimboEmuActivity.class));
                return true;
            }
        });

        setContentView(root);
    }

    private void refreshUi() {
        boolean hasImage = imagePath != null && new File(imagePath).exists();
        progress.setVisibility(copying ? View.VISIBLE : View.GONE);
        changeButton.setVisibility(hasImage && !copying ? View.VISIBLE : View.GONE);
        primaryButton.setEnabled(!copying);

        if (copying) {
            stateText.setText("Android imajı hazırlanıyor…");
            fileText.setText(imageName == null ? "Lütfen bekle" : imageName);
            primaryButton.setText("Hazırlanıyor…");
        } else if (hasImage) {
            stateText.setText("ARM64 Android hazır");
            fileText.setText(imageName == null ? new File(imagePath).getName() : imageName);
            primaryButton.setText("Android’i Başlat");
        } else {
            stateText.setText("Henüz Android imajı seçilmedi");
            fileText.setText("ARM64 QEMU imajını bir kez seçmen yeterli");
            primaryButton.setText("Android İmajını Seç");
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_GUEST_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_GUEST_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                copyGuestImage(uri);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return "android-arm64.img";
    }

    private void copyGuestImage(final Uri uri) {
        imageName = queryDisplayName(uri);
        copying = true;
        refreshUi();

        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream input = null;
                FileOutputStream output = null;
                try {
                    File imageDir = new File(getFilesDir(), "pocketdroid");
                    if (!imageDir.exists() && !imageDir.mkdirs()) {
                        throw new Exception("İmaj klasörü oluşturulamadı");
                    }

                    String lower = imageName == null ? "" : imageName.toLowerCase();
                    String ext = lower.endsWith(".iso") ? ".iso"
                            : lower.endsWith(".qcow2") ? ".qcow2" : ".img";
                    File target = new File(imageDir, "guest" + ext);
                    File temp = new File(imageDir, "guest.tmp");
                    if (temp.exists()) temp.delete();

                    ContentResolver resolver = getContentResolver();
                    input = resolver.openInputStream(uri);
                    if (input == null) throw new Exception("Dosya açılamadı");
                    output = new FileOutputStream(temp, false);

                    byte[] buffer = new byte[1024 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                    output.flush();

                    if (target.exists() && !target.delete()) {
                        throw new Exception("Eski imaj silinemedi");
                    }
                    if (!temp.renameTo(target)) {
                        throw new Exception("İmaj kaydedilemedi");
                    }

                    imagePath = target.getAbsolutePath();
                    prefs.edit()
                            .putString(PREF_IMAGE_PATH, imagePath)
                            .putString(PREF_IMAGE_NAME, imageName)
                            .apply();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            copying = false;
                            refreshUi();
                            Toast.makeText(PocketDroidActivity.this,
                                    "Android imajı hazır", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (final Exception ex) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            copying = false;
                            imagePath = null;
                            refreshUi();
                            Toast.makeText(PocketDroidActivity.this,
                                    "İmaj hazırlanamadı: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    try { if (input != null) input.close(); } catch (Exception ignored) {}
                    try { if (output != null) output.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void startGuest() {
        try {
            File guest = new File(imagePath);
            if (!guest.exists()) {
                imagePath = null;
                refreshUi();
                return;
            }

            setupNativeLibs();

            MachineController controller = MachineController.getInstance();
            ArrayList<String> machines = controller.getStoredMachines();
            if (machines.contains(VM_NAME)) {
                controller.setStoredMachine(VM_NAME);
            } else {
                controller.createVM(VM_NAME);
            }

            Machine machine = controller.getMachine();
            if (machine == null) throw new Exception("Sanal makine oluşturulamadı");

            // ARM64 guest defaults for QEMU's generic virt board.
            machine.setMemory(3072);
            machine.setCpuNum(2);
            machine.setCpu("cortex-a72");
            machine.setMachineType("virt");
            machine.setEnableKVM(0);
            machine.setEnableMTTCG(1);
            machine.setEnableVNC(0);
            machine.setVga("virtio-gpu-pci");
            machine.setNetwork("user");
            machine.setNetworkCard("virtio-net-pci");

            String lower = guest.getName().toLowerCase();
            if (lower.endsWith(".iso")) {
                machine.setEnableCDROM(true);
                machine.setCdImagePath(guest.getAbsolutePath());
                machine.setHdaImagePath(null);
                machine.setBootDevice("d");
            } else {
                machine.setEnableCDROM(false);
                machine.setCdImagePath(null);
                machine.setHdaImagePath(guest.getAbsolutePath());
                machine.setBootDevice("c");
            }

            File saveDir = new File(controller.getMachineSaveDir());
            if (!saveDir.exists()) saveDir.mkdirs();
            FileInstaller.installFiles(this, false);

            stateText.setText("Android başlatılıyor…");
            fileText.setText("ARM64 sanal makine hazırlanıyor");
            primaryButton.setEnabled(false);
            startSDL();
        } catch (Exception ex) {
            primaryButton.setEnabled(true);
            Toast.makeText(this, "Başlatılamadı: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (primaryButton != null && !copying) {
            primaryButton.setEnabled(true);
            refreshUi();
        }
    }
}
