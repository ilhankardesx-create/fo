package com.pocketdroid.vm;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity implements VncView.Listener {
    private static final int PICK_PACKAGE = 7001;
    private static final String DISK_A = "vda.qcow2";
    private static final String DISK_B = "vdb.qcow2";
    private static final String EFI_VARS = "efi_vars.fd";
    private static final String EFI_CODE = "AAVMF_CODE.fd";

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private TextView status;
    private TextView detail;
    private TextView logView;
    private ScrollView logScroll;
    private Button primary;
    private Button change;
    private ProgressBar progress;
    private VncView vncView;
    private volatile boolean preparing;
    private volatile boolean booting;
    private volatile boolean logTailRunning;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setupFullscreen();
        buildUi();
        refreshUi();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private void setupFullscreen() {
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        w.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(34), dp(24), dp(20));
        root.setBackgroundColor(Color.rgb(7, 9, 13));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text("P", 25, Color.WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(rounded(Color.rgb(47, 106, 255), 18));
        header.addView(badge, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        TextView title = text("PocketDroid", 25, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);
        TextView sub = text("Temiz QEMU motoru • ikinci Android", 13, Color.rgb(146, 154, 173));
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(rounded(Color.rgb(20, 23, 31), 20));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(22);
        root.addView(card, cardLp);

        status = text("", 18, Color.WHITE);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(status);
        detail = text("", 13, Color.rgb(150, 158, 176));
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(7);
        card.addView(detail, detailLp);

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        progressLp.gravity = Gravity.CENTER_HORIZONTAL;
        progressLp.topMargin = dp(12);
        card.addView(progress, progressLp);

        primary = new Button(this);
        primary.setAllCaps(false);
        primary.setTextColor(Color.WHITE);
        primary.setTextSize(17);
        primary.setTypeface(Typeface.DEFAULT_BOLD);
        primary.setBackground(rounded(Color.rgb(47, 106, 255), 18));
        primary.setOnClickListener(v -> {
            if (preparing || booting) return;
            if (isVmReady()) startAndroid(); else choosePackage();
        });
        LinearLayout.LayoutParams primaryLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        primaryLp.topMargin = dp(16);
        root.addView(primary, primaryLp);

        change = new Button(this);
        change.setAllCaps(false);
        change.setText("Android paketini değiştir");
        change.setTextColor(Color.rgb(170, 180, 204));
        change.setTextSize(13);
        change.setBackgroundColor(Color.TRANSPARENT);
        change.setOnClickListener(v -> choosePackage());
        root.addView(change, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        vncView = new VncView(this);
        vncView.setListener(this);
        vncView.setVisibility(View.GONE);
        LinearLayout.LayoutParams vncLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        vncLp.topMargin = dp(12);
        root.addView(vncView, vncLp);

        logScroll = new ScrollView(this);
        logScroll.setFillViewport(true);
        logScroll.setBackground(rounded(Color.rgb(12, 14, 20), 14));
        logView = text("", 11, Color.rgb(184, 194, 214));
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(dp(12), dp(10), dp(12), dp(10));
        logScroll.addView(logView);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(180));
        logLp.topMargin = dp(12);
        root.addView(logScroll, logLp);

        setContentView(root);
    }

    private File vmDir() {
        return new File(getFilesDir(), "pocketdroid/lineage");
    }

    private File vmFile(String name) {
        return new File(vmDir(), name);
    }

    private File qemuLog() {
        return vmFile("qemu.log");
    }

    private File serialLog() {
        return vmFile("serial.log");
    }

    private boolean isVmReady() {
        return vmFile(DISK_A).isFile()
                && vmFile(DISK_B).isFile()
                && vmFile(EFI_VARS).isFile();
    }

    private void refreshUi() {
        progress.setVisibility(preparing || booting ? View.VISIBLE : View.GONE);
        if (booting) {
            status.setText("Android başlatılıyor…");
            detail.setText("QEMU ARM64 doğrudan PocketDroid tarafından çalıştırılıyor");
            primary.setText("Başlatılıyor…");
            primary.setEnabled(false);
            change.setVisibility(View.GONE);
        } else if (preparing) {
            status.setText("LineageOS hazırlanıyor…");
            detail.setText("ZIP içindeki diskler çıkarılıyor");
            primary.setText("Hazırlanıyor…");
            primary.setEnabled(false);
            change.setVisibility(View.GONE);
        } else if (isVmReady()) {
            status.setText("LineageOS ARM64 hazır");
            detail.setText("vda.qcow2 + vdb.qcow2 + UEFI hazır");
            primary.setText("Android’i Başlat");
            primary.setEnabled(true);
            change.setVisibility(View.VISIBLE);
        } else {
            status.setText("Android paketi seçilmedi");
            detail.setText("UTM-VM LineageOS ARM64 ZIP dosyasını seç");
            primary.setText("Android Paketini Seç");
            primary.setEnabled(true);
            change.setVisibility(View.GONE);
        }
    }

    private void choosePackage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, PICK_PACKAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PACKAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        preparePackage(uri);
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return "LineageOS-ARM64.zip";
    }

    private void preparePackage(Uri uri) {
        String name = displayName(uri);
        if (name == null || !name.toLowerCase(Locale.US).endsWith(".zip")) {
            Toast.makeText(this, "ARM64 LineageOS ZIP dosyasını seç dayı 😁", Toast.LENGTH_LONG).show();
            return;
        }
        preparing = true;
        refreshUi();
        appendLog("[PocketDroid] Paket: " + name);
        executor.execute(() -> {
            try {
                File dir = vmDir();
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("VM klasörü oluşturulamadı");
                extractPackage(uri);
                ensureFirmware();
                runOnUiThread(() -> {
                    preparing = false;
                    refreshUi();
                    Toast.makeText(this, "LineageOS hazır 😁", Toast.LENGTH_LONG).show();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    preparing = false;
                    refreshUi();
                    appendLog("[HATA] " + ex.getMessage());
                    Toast.makeText(this, "Kurulum hatası: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void extractPackage(Uri uri) throws Exception {
        File aTmp = new File(vmDir(), DISK_A + ".tmp");
        File bTmp = new File(vmDir(), DISK_B + ".tmp");
        File varsTmp = new File(vmDir(), EFI_VARS + ".tmp");
        delete(aTmp); delete(bTmp); delete(varsTmp);

        boolean a = false, b = false, vars = false;
        ContentResolver resolver = getContentResolver();
        try (InputStream raw = resolver.openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, 1024 * 1024))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) { zip.closeEntry(); continue; }
                String lower = e.getName().replace('\\', '/').toLowerCase(Locale.US);
                if (lower.endsWith("/data/vda.qcow2") || lower.equals("vda.qcow2")) {
                    appendLog("[✓] Sistem diski çıkarılıyor…");
                    copyEntry(zip, aTmp); a = true;
                } else if (lower.endsWith("/data/vdb.qcow2") || lower.equals("vdb.qcow2")) {
                    appendLog("[✓] Veri diski çıkarılıyor…");
                    copyEntry(zip, bTmp); b = true;
                } else if (lower.endsWith("/data/efi_vars.fd") || lower.equals("efi_vars.fd")) {
                    appendLog("[✓] UEFI değişkenleri çıkarılıyor…");
                    copyEntry(zip, varsTmp); vars = true;
                }
                zip.closeEntry();
            }
        }
        if (!a || !b || !vars) throw new Exception("ZIP içinde gerekli VM dosyaları bulunamadı");
        replace(aTmp, vmFile(DISK_A));
        replace(bTmp, vmFile(DISK_B));
        replace(varsTmp, vmFile(EFI_VARS));
    }

    private void copyEntry(ZipInputStream zip, File target) throws Exception {
        try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target), 1024 * 1024)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = zip.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private void replace(File src, File dst) throws Exception {
        delete(dst);
        if (!src.renameTo(dst)) throw new Exception(dst.getName() + " kaydedilemedi");
    }

    private void delete(File f) {
        if (f.exists()) f.delete();
    }

    private File ensureFirmware() throws Exception {
        File f = vmFile(EFI_CODE);
        if (f.isFile() && f.length() > 0) return f;
        if (!vmDir().exists() && !vmDir().mkdirs()) throw new Exception("VM klasörü açılamadı");
        File tmp = new File(vmDir(), EFI_CODE + ".tmp");
        delete(tmp);
        try (InputStream in = getAssets().open(EFI_CODE);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp), 1024 * 1024)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        replace(tmp, f);
        return f;
    }

    private void startAndroid() {
        booting = true;
        refreshUi();
        vncView.setVisibility(View.VISIBLE);
        logView.setText("");
        delete(qemuLog());
        delete(serialLog());
        acquireWakeLock();

        executor.execute(() -> {
            try {
                File firmware = ensureFirmware();
                File vars = vmFile(EFI_VARS);
                File vda = vmFile(DISK_A);
                File vdb = vmFile(DISK_B);
                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");

                appendLog("[✓] ARM64 motor yolu: " + qemu.getAbsolutePath());
                if (!qemu.isFile()) throw new Exception("ARM64 QEMU motoru APK içinde bulunamadı");
                appendLog("[✓] ARM64 motor bulundu");
                appendLog("[✓] UEFI bulundu: " + firmware.length() / 1024 + " KB");
                appendLog("[✓] Sistem diski bulundu: " + vda.length() / 1024 / 1024 + " MB");
                appendLog("[✓] Veri diski bulundu: " + vdb.length() / 1024 / 1024 + " MB");
                appendLog("[→] QEMU doğrudan başlatılıyor…");

                ArrayList<String> a = new ArrayList<>();
                a.add("qemu-system-aarch64");
                add(a, "-machine", "virt");
                add(a, "-cpu", "max,pauth-impdef=on");
                add(a, "-accel", "tcg,tb-size=1024,thread=multi");
                add(a, "-m", "2048");
                add(a, "-device", "virtio-blk-pci,drive=vda,bootindex=0");
                add(a, "-device", "virtio-blk-pci,drive=vdb,bootindex=1");
                add(a, "-drive", "if=pflash,unit=0,file=" + firmware.getAbsolutePath() + ",format=raw,readonly=on");
                add(a, "-drive", "if=pflash,unit=1,file=" + vars.getAbsolutePath() + ",format=raw");
                add(a, "-drive", "file=" + vda.getAbsolutePath() + ",if=none,id=vda,discard=unmap,detect-zeroes=unmap");
                add(a, "-drive", "file=" + vdb.getAbsolutePath() + ",if=none,id=vdb,discard=unmap,detect-zeroes=unmap");
                add(a, "-device", "virtio-net-pci,netdev=net0");
                add(a, "-netdev", "user,id=net0,hostfwd=tcp:127.0.0.1:5555-:5555,hostfwd=tcp:127.0.0.1:5554-:5554");
                add(a, "-device", "usb-ehci,id=usb-bus");
                add(a, "-device", "usb-tablet,bus=usb-bus.0");
                add(a, "-device", "usb-mouse,bus=usb-bus.0");
                add(a, "-device", "usb-kbd,bus=usb-bus.0");
                add(a, "-device", "virtio-serial");
                add(a, "-device", "virtio-rng-pci");
                add(a, "-device", "virtio-gpu-pci");
                add(a, "-vnc", "127.0.0.1:1");
                add(a, "-serial", "file:" + serialLog().getAbsolutePath());
                add(a, "-monitor", "none");

                startLogTail();
                runOnUiThread(() -> vncView.connectWithRetry("127.0.0.1", 5901));
                String result = QemuBridge.nativeStart(qemu.getAbsolutePath(), qemuLog().getAbsolutePath(), a.toArray(new String[0]));
                appendLog("[QEMU] " + result);
            } catch (Throwable ex) {
                appendLog("[HATA] " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } finally {
                logTailRunning = false;
                releaseWakeLock();
                runOnUiThread(() -> {
                    booting = false;
                    primary.setEnabled(true);
                    refreshUi();
                });
            }
        });
    }

    private static void add(ArrayList<String> list, String a, String b) {
        list.add(a); list.add(b);
    }

    private void startLogTail() {
        if (logTailRunning) return;
        logTailRunning = true;
        executor.execute(() -> {
            long qPos = 0;
            long sPos = 0;
            while (logTailRunning) {
                try {
                    File q = qemuLog();
                    if (q.isFile() && q.length() > qPos) {
                        qPos = appendNewFileBytes(q, qPos, "QEMU");
                    }
                    File s = serialLog();
                    if (s.isFile() && s.length() > sPos) {
                        sPos = appendNewFileBytes(s, sPos, "SERIAL");
                    }
                    Thread.sleep(500);
                } catch (Exception ignored) {}
            }
        });
    }

    private long appendNewFileBytes(File file, long from, String prefix) throws Exception {
        long len = file.length();
        if (len < from) from = 0;
        int amount = (int) Math.min(len - from, 64 * 1024);
        byte[] data = new byte[amount];
        try (FileInputStream in = new FileInputStream(file)) {
            long skipped = 0;
            while (skipped < from) {
                long n = in.skip(from - skipped);
                if (n <= 0) break;
                skipped += n;
            }
            int off = 0;
            while (off < amount) {
                int n = in.read(data, off, amount - off);
                if (n < 0) break;
                off += n;
            }
        }
        String txt = new String(data, StandardCharsets.UTF_8);
        if (!txt.trim().isEmpty()) appendLog("[" + prefix + "] " + txt.trim());
        return from + amount;
    }

    private void appendLog(String line) {
        runOnUiThread(() -> {
            String old = logView.getText().toString();
            String next = old + (old.isEmpty() ? "" : "\n") + line;
            if (next.length() > 18000) next = next.substring(next.length() - 18000);
            logView.setText(next);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketDroid:VM");
            wakeLock.acquire(30 * 60 * 1000L);
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        wakeLock = null;
    }

    @Override
    public void onConnected(int width, int height, String name) {
        status.setText("Android ekranı bağlandı 😁");
        detail.setText(width + "×" + height + " • " + name);
        appendLog("[✓] VNC görüntüsü bağlandı: " + width + "×" + height);
    }

    @Override
    public void onMessage(String message) {
        detail.setText(message);
        appendLog("[VNC] " + message);
    }

    @Override
    public void onDisconnected(String reason) {
        appendLog("[VNC] Bağlantı kurulamadı: " + reason);
    }

    @Override
    protected void onDestroy() {
        vncView.disconnect();
        if (booting) {
            try { QemuBridge.nativeStop(); } catch (Throwable ignored) {}
        }
        logTailRunning = false;
        releaseWakeLock();
        executor.shutdownNow();
        super.onDestroy();
    }
}
