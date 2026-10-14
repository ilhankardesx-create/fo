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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * PocketDroid user-facing launcher.
 *
 * v0.4 understands the ready-made LineageOS ARM64 UTM package. The user picks
 * the ZIP once; PocketDroid extracts only the VM disks/EFI variables and then
 * boots them with hidden QEMU settings.
 */
public class PocketDroidActivity extends LimboEmuActivity {

    private static final int PICK_LINEAGE_PACKAGE = 4201;
    private static final String PREFS = "pocketdroid";
    private static final String PREF_PACKAGE_NAME = "lineage_package_name";
    private static final String PREF_READY = "lineage_vm_ready";
    private static final String VM_NAME = "PocketDroidLineageARM64";

    private static final String DISK_A = "vda.qcow2";
    private static final String DISK_B = "vdb.qcow2";
    private static final String EFI_VARS = "efi_vars.fd";
    private static final String EFI_CODE = "AAVMF_CODE.fd";

    private SharedPreferences prefs;
    private TextView stateText;
    private TextView fileText;
    private Button primaryButton;
    private Button changeButton;
    private ProgressBar progress;
    private String packageName;
    private boolean preparing;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MachineController.getInstance().removeOnStatusChangeListener(this);
        MachineController.getInstance().removeOnEventListener(this);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        packageName = prefs.getString(PREF_PACKAGE_NAME, null);

        setupFullscreen();
        buildPocketDroidUi();
        refreshUi();
    }

    private File vmDir() {
        return new File(getFilesDir(), "pocketdroid/lineage");
    }

    private File vmFile(String name) {
        return new File(vmDir(), name);
    }

    private boolean isVmReady() {
        return prefs.getBoolean(PREF_READY, false)
                && vmFile(DISK_A).isFile()
                && vmFile(DISK_B).isFile()
                && vmFile(EFI_VARS).isFile();
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
                if (preparing) return;
                if (isVmReady()) startGuest(); else pickPackage();
            }
        });
        root.addView(primaryButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        changeButton = new Button(this);
        changeButton.setText("Android paketini değiştir");
        changeButton.setTextColor(Color.rgb(175, 184, 205));
        changeButton.setTextSize(14);
        changeButton.setAllCaps(false);
        changeButton.setBackgroundColor(Color.TRANSPARENT);
        changeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!preparing) pickPackage();
            }
        });
        LinearLayout.LayoutParams changeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        changeParams.topMargin = dp(8);
        root.addView(changeButton, changeParams);

        TextView note = text("İlk kurulum biraz sürebilir. ZIP dosyasını açmana gerek yok.",
                13, Color.rgb(103, 111, 129));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(20);
        root.addView(note, noteParams);

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
        boolean ready = isVmReady();
        progress.setVisibility(preparing ? View.VISIBLE : View.GONE);
        changeButton.setVisibility(ready && !preparing ? View.VISIBLE : View.GONE);
        primaryButton.setEnabled(!preparing);

        if (preparing) {
            stateText.setText("İkinci Android hazırlanıyor…");
            if (fileText.getText() == null || fileText.getText().length() == 0) {
                fileText.setText(packageName == null ? "LineageOS paketi açılıyor" : packageName);
            }
            primaryButton.setText("Hazırlanıyor…");
        } else if (ready) {
            stateText.setText("LineageOS ARM64 hazır");
            fileText.setText(packageName == null ? "İkinci Android kuruldu" : packageName);
            primaryButton.setText("Android’i Başlat");
        } else {
            stateText.setText("Android paketi seçilmedi");
            fileText.setText("İndirdiğin UTM-VM ... arm64only ZIP dosyasını seç");
            primaryButton.setText("Android Paketini Seç");
        }
    }

    private void pickPackage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_LINEAGE_PACKAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_LINEAGE_PACKAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                prepareLineagePackage(uri);
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
        return "LineageOS-ARM64.zip";
    }

    private void setProgressText(final String value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fileText.setText(value);
            }
        });
    }

    private void prepareLineagePackage(final Uri uri) {
        packageName = queryDisplayName(uri);
        if (packageName == null || !packageName.toLowerCase(Locale.US).endsWith(".zip")) {
            Toast.makeText(this, "Lütfen indirdiğin UTM-VM ARM64 ZIP dosyasını seç.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        preparing = true;
        fileText.setText(packageName);
        refreshUi();

        new Thread(new Runnable() {
            @Override
            public void run() {
                ZipInputStream zip = null;
                try {
                    File dir = vmDir();
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new Exception("Sanal Android klasörü oluşturulamadı");
                    }

                    prefs.edit().putBoolean(PREF_READY, false).apply();
                    deleteIfExists(vmFile(DISK_A));
                    deleteIfExists(vmFile(DISK_B));
                    deleteIfExists(vmFile(EFI_VARS));

                    // Remove obsolete v0.3 single-image copies if present.
                    File oldDir = new File(getFilesDir(), "pocketdroid");
                    deleteIfExists(new File(oldDir, "guest.img"));
                    deleteIfExists(new File(oldDir, "guest.qcow2"));
                    deleteIfExists(new File(oldDir, "guest.iso"));
                    deleteIfExists(new File(oldDir, "guest.tmp"));

                    ContentResolver resolver = getContentResolver();
                    InputStream raw = resolver.openInputStream(uri);
                    if (raw == null) throw new Exception("ZIP dosyası açılamadı");
                    zip = new ZipInputStream(new BufferedInputStream(raw, 1024 * 1024));

                    boolean foundA = false;
                    boolean foundB = false;
                    boolean foundVars = false;
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            zip.closeEntry();
                            continue;
                        }

                        String name = entry.getName().replace('\\', '/');
                        String lower = name.toLowerCase(Locale.US);
                        File target = null;
                        String label = null;

                        if (lower.endsWith("/data/vda.qcow2") || lower.equals("vda.qcow2")) {
                            target = vmFile(DISK_A);
                            label = "Android sistem diski çıkarılıyor…";
                            foundA = true;
                        } else if (lower.endsWith("/data/vdb.qcow2") || lower.equals("vdb.qcow2")) {
                            target = vmFile(DISK_B);
                            label = "Android veri diski çıkarılıyor…";
                            foundB = true;
                        } else if (lower.endsWith("/data/efi_vars.fd") || lower.equals("efi_vars.fd")) {
                            target = vmFile(EFI_VARS);
                            label = "Android boot ayarları hazırlanıyor…";
                            foundVars = true;
                        }

                        if (target != null) {
                            setProgressText(label);
                            extractCurrentEntry(zip, target);
                        }
                        zip.closeEntry();
                    }

                    if (!foundA || !foundB || !foundVars
                            || !vmFile(DISK_A).isFile()
                            || !vmFile(DISK_B).isFile()
                            || !vmFile(EFI_VARS).isFile()) {
                        throw new Exception("Bu ZIP uygun LineageOS UTM ARM64 paketi değil");
                    }

                    setProgressText("ARM64 UEFI hazırlanıyor…");
                    ensureFirmware();

                    prefs.edit()
                            .putString(PREF_PACKAGE_NAME, packageName)
                            .putBoolean(PREF_READY, true)
                            .apply();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            preparing = false;
                            refreshUi();
                            Toast.makeText(PocketDroidActivity.this,
                                    "İkinci Android hazır 😁", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Exception ex) {
                    prefs.edit().putBoolean(PREF_READY, false).apply();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            preparing = false;
                            refreshUi();
                            Toast.makeText(PocketDroidActivity.this,
                                    "Kurulum tamamlanamadı: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                } finally {
                    try { if (zip != null) zip.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private void deleteIfExists(File file) {
        if (file.exists()) file.delete();
    }

    private void extractCurrentEntry(ZipInputStream zip, File target) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + ".tmp");
        deleteIfExists(temp);
        BufferedOutputStream output = null;
        try {
            output = new BufferedOutputStream(new FileOutputStream(temp, false), 1024 * 1024);
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = zip.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            if (output != null) output.close();
        }

        deleteIfExists(target);
        if (!temp.renameTo(target)) {
            deleteIfExists(temp);
            throw new Exception(target.getName() + " kaydedilemedi");
        }
    }

    private File ensureFirmware() throws Exception {
        File firmware = vmFile(EFI_CODE);
        if (firmware.isFile() && firmware.length() > 0) return firmware;

        InputStream input = null;
        BufferedOutputStream output = null;
        File temp = new File(vmDir(), EFI_CODE + ".tmp");
        try {
            input = getAssets().open("pocketdroid/" + EFI_CODE);
            output = new BufferedOutputStream(new FileOutputStream(temp, false), 1024 * 1024);
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
        }
        deleteIfExists(firmware);
        if (!temp.renameTo(firmware)) {
            deleteIfExists(temp);
            throw new Exception("ARM64 UEFI hazırlanamadı");
        }
        return firmware;
    }

    private void startGuest() {
        try {
            if (!isVmReady()) {
                refreshUi();
                return;
            }

            File firmware = ensureFirmware();
            File vars = vmFile(EFI_VARS);
            File vda = vmFile(DISK_A);
            File vdb = vmFile(DISK_B);

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

            // Mirrors the platform-independent QEMU recipe published by the
            // LineageOS QEMU project, adapted to Limbo's hidden machine model.
            machine.setMemory(2048);
            machine.setCpuNum(2);
            machine.setCpu("max");
            machine.setMachineType("virt");
            machine.setEnableKVM(0);
            machine.setEnableMTTCG(1);
            machine.setEnableVNC(0);
            machine.setVga("virtio-gpu-pci");
            machine.setNetwork("user");
            machine.setNetworkCard("virtio-net-pci");
            machine.setHostFwd("tcp:5555:5555,tcp:5554:5554");

            machine.setEnableCDROM(false);
            machine.setCdImagePath(null);
            machine.setHdaImagePath(vda.getAbsolutePath());
            machine.setHdaInterface("virtio");
            machine.setHdbImagePath(vdb.getAbsolutePath());
            machine.setHdbInterface("virtio");
            machine.setHdcImagePath(null);
            machine.setHddImagePath(null);
            machine.setBootDevice("Default");

            String extra = "-drive if=pflash,unit=0,file=" + firmware.getAbsolutePath()
                    + ",format=raw,readonly=on"
                    + " -drive if=pflash,unit=1,file=" + vars.getAbsolutePath()
                    + ",format=raw";
            machine.setExtraParams(extra);

            File saveDir = new File(controller.getMachineSaveDir());
            if (!saveDir.exists()) saveDir.mkdirs();
            FileInstaller.installFiles(this, false);

            stateText.setText("Android başlatılıyor…");
            fileText.setText("LineageOS ARM64 boot ediliyor");
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
        if (primaryButton != null && !preparing) {
            primaryButton.setEnabled(true);
            refreshUi();
        }
    }
}
