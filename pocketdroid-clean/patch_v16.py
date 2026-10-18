from pathlib import Path

# v1.16: move the known-good -bios + VDA boot path back into source control,
# use Limbo's official VMExecutor JNI entry, and temporarily swallow VNC touch
# events so a pointer packet can never crash the app while boot is being debugged.

root = Path('pocketdroid-clean')
main = root / 'app/src/main/java/com/pocketdroid/vm/MainActivity.java'
vnc = root / 'app/src/main/java/com/pocketdroid/vm/VncView.java'
service = root / 'app/src/main/java/com/pocketdroid/vm/QemuProcessService.java'
vmexec = root / 'app/src/main/java/com/max2idea/android/limbo/jni/VMExecutor.java'
gradle = root / 'app/build.gradle'

# --- TouchGuard: consume touches, do not send RFB PointerEvent yet. ---
v = vnc.read_text()
start = v.index('    @Override\n    public boolean onTouchEvent(MotionEvent event) {')
end = v.index('    private synchronized void sendPointer', start)
v = v[:start] + r'''    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // v1.16 TouchGuard: during boot diagnostics we deliberately consume all
        // touch events locally. The old path wrote RFB PointerEvent packets from
        // the UI thread and the app could disappear as soon as the user touched
        // the screen. Keyboard/menu input will be added back through a safer path.
        return true;
    }

''' + v[end:]
vnc.write_text(v)

# --- Minimal Java peer for Limbo's official liblimbo.so VMExecutor JNI API. ---
vmexec.parent.mkdir(parents=True, exist_ok=True)
vmexec.write_text(r'''package com.max2idea.android.limbo.jni;

import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * Minimal Java peer for Limbo 6.0.1's exported VMExecutor JNI entry points.
 * PocketDroid only needs start() plus the fd callbacks used by the compatibility
 * layer. Keeping the exact package/class name preserves the native JNI symbols.
 */
public final class VMExecutor {
    private static volatile boolean loaded;

    public static synchronized void ensureLoaded(String nativeDir) {
        if (loaded) return;
        String[] libs = new String[]{
                "libcompat-limbo.so",
                "libcompat-musl.so",
                "libglib-2.0.so",
                "libpixman-1.so",
                "libcompat-SDL2-addons.so",
                "libSDL2.so",
                "libcompat-SDL2-ext.so",
                "liblimbo.so"
        };
        for (String lib : libs) {
            System.load(new File(nativeDir, lib).getAbsolutePath());
        }
        loaded = true;
    }

    public native String start(String storageDir,
                               String baseDir,
                               String libFilename,
                               String libPath,
                               int sdlScaleHint,
                               Object[] params);

    public native String stop(int restart);

    // Called by Limbo's Android storage compatibility layer when it needs an fd.
    public int get_fd(String path) {
        if (path == null) return -1;
        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                    new File(path), ParcelFileDescriptor.MODE_READ_WRITE);
            return pfd.detachFd();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public int close_fd(int fd) {
        if (fd < 0) return -1;
        try {
            ParcelFileDescriptor.adoptFd(fd).close();
            return 0;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // SDL compatibility code may call this static hook. VNC rendering does not
    // need to resize the Android View here, so a no-op is sufficient for now.
    public static void onVMResolutionChanged(int width, int height) {
    }
}
''')

# --- Run QEMU through Limbo's official VMExecutor.start(), not custom qemu_init. ---
service.write_text(r'''package com.pocketdroid.vm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import com.max2idea.android.limbo.jni.VMExecutor;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Runs Limbo/QEMU in PocketDroid's isolated :qemu process. */
public class QemuProcessService extends Service {
    private volatile boolean started;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (started || intent == null) return START_NOT_STICKY;
        started = true;

        final String qemuLibPath = intent.getStringExtra("qemuLibPath");
        final String storageDir = intent.getStringExtra("storageDir");
        final String baseDir = intent.getStringExtra("baseDir");
        final String[] args = intent.getStringArrayExtra("args");
        final String statePath = intent.getStringExtra("statePath");

        writeState(statePath, "SERVICE_STARTED pid=" + Process.myPid());

        Thread worker = new Thread(() -> {
            try {
                File qemu = qemuLibPath == null ? null : new File(qemuLibPath);
                if (qemu == null || !qemu.isFile()) {
                    throw new IllegalStateException("QEMU kütüphanesi bulunamadı: " + qemuLibPath);
                }
                if (storageDir == null || baseDir == null) {
                    throw new IllegalStateException("QEMU storage/base yolu eksik");
                }

                String nativeDir = qemu.getParentFile().getAbsolutePath();
                VMExecutor.ensureLoaded(nativeDir);
                writeState(statePath, "LIMBO_LIBS_READY pid=" + Process.myPid());

                VMExecutor vm = new VMExecutor();
                writeState(statePath, "LIMBO_BRIDGE_READY pid=" + Process.myPid());
                writeState(statePath, "NATIVE_ENTER pid=" + Process.myPid());

                String result = vm.start(
                        storageDir,
                        baseDir,
                        qemu.getName(),
                        qemu.getAbsolutePath(),
                        1,
                        args == null ? new String[0] : args);
                writeState(statePath, "EXIT: " + result);
            } catch (Throwable t) {
                String message = t.getMessage();
                writeState(statePath,
                        "JAVA_ERROR: " + t.getClass().getSimpleName()
                                + (message == null ? "" : ": " + message));
            } finally {
                started = false;
                stopSelf(startId);
            }
        }, "PocketDroid-QEMU-Limbo");
        worker.setPriority(Thread.NORM_PRIORITY);
        worker.start();
        return START_NOT_STICKY;
    }

    private void writeState(String path, String value) {
        if (path == null) return;
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream out = new FileOutputStream(f, false)) {
                out.write((value + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
''')

# --- Known-good v1.13 boot recipe: AAVMF via -bios, VDA only, no pflash. ---
m = main.read_text()
start = m.index('    private void startAndroid() {')
end = m.index('    private static void add(ArrayList<String> list, String a, String b) {', start)
method = r'''    private void startAndroid() {
        booting = true;
        refreshUi();
        vncView.setVisibility(View.VISIBLE);
        logView.setText("");
        delete(qemuLog());
        delete(serialLog());
        File stateFile = vmFile("qemu-process-state.txt");
        delete(stateFile);
        acquireWakeLock();

        executor.execute(() -> {
            try {
                File firmware = ensureFirmware();
                File vda = vmFile(DISK_A);
                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");
                File qemuDataDir = ensureQemuDataDir();
                String storage = vmDir().getAbsolutePath() + File.separator;
                String base = qemuDataDir.getAbsolutePath() + File.separator;

                appendLog("=== v1.16 SOURCE BUILD • LIMBO PATH • TOUCHGUARD ===");
                appendLog("[✓] ARM64 motor yolu: " + qemu.getAbsolutePath());
                if (!qemu.isFile()) throw new Exception("ARM64 QEMU motoru APK içinde bulunamadı");
                if (!vda.isFile()) throw new Exception("vda.qcow2 bulunamadı");
                appendLog("[✓] AAVMF -bios: " + firmware.getAbsolutePath());
                appendLog("[✓] VDA: " + vda.getAbsolutePath());
                appendLog("[✓] Limbo base/-L: " + base);
                appendLog("[PocketDroid] PFLASH YOK -> AAVMF -bios + SADECE VDA disk ile gerçek boot");
                appendLog("[TouchGuard] Boot sırasında dokunma QEMU'ya gönderilmeyecek");

                ArrayList<String> a = new ArrayList<>();
                a.add(qemu.getName());
                add(a, "-machine", "virt");
                add(a, "-cpu", "cortex-a53");
                add(a, "-accel", "tcg,thread=single");
                add(a, "-smp", "1");
                add(a, "-m", "2048");
                a.add("-nodefaults");

                add(a, "-bios", firmware.getAbsolutePath());
                add(a, "-drive", "file=" + vda.getAbsolutePath()
                        + ",if=none,id=vda,format=qcow2,discard=unmap,detect-zeroes=unmap");
                add(a, "-device", "virtio-blk-pci,drive=vda,bootindex=0");
                add(a, "-device", "virtio-rng-pci");
                add(a, "-device", "virtio-gpu-pci");

                add(a, "-vnc", "127.0.0.1:1");
                add(a, "-serial", "file:" + serialLog().getAbsolutePath());
                add(a, "-monitor", "none");
                add(a, "-L", base);
                add(a, "-overcommit", "mem-lock=off");
                add(a, "-rtc", "base=localtime");

                appendLog("[ARGS] argc=" + a.size());
                for (int i = 0; i < a.size(); i++) {
                    appendLog(String.format(Locale.US, "[ARGV %02d] %s", i, a.get(i)));
                }

                Intent serviceIntent = new Intent(MainActivity.this, QemuProcessService.class);
                serviceIntent.putExtra("qemuLibPath", qemu.getAbsolutePath());
                serviceIntent.putExtra("storageDir", storage);
                serviceIntent.putExtra("baseDir", base);
                serviceIntent.putExtra("args", a.toArray(new String[0]));
                serviceIntent.putExtra("statePath", stateFile.getAbsolutePath());

                runOnUiThread(() -> {
                    startService(serviceIntent);
                    vncView.connectWithRetry("127.0.0.1", 5901);
                });
                appendLog("[→] Limbo VMExecutor başlatıldı; VNC 127.0.0.1:5901 bekleniyor.");

                boolean nativeEntered = false;
                String lastState = "";
                long launchTime = System.currentTimeMillis();
                while (booting) {
                    String state = readSmallFile(stateFile).trim();
                    if (!state.isEmpty() && !state.equals(lastState)) {
                        lastState = state;
                        if (state.startsWith("SERVICE_STARTED")) {
                            appendLog("[✓] QEMU servisi canlı: " + state);
                        } else if (state.startsWith("LIMBO_LIBS_READY")) {
                            appendLog("[✓] Limbo native runtime hazır");
                        } else if (state.startsWith("LIMBO_BRIDGE_READY")) {
                            appendLog("[✓] Gerçek VMExecutor nesnesi hazır");
                        } else if (state.startsWith("NATIVE_ENTER")) {
                            nativeEntered = true;
                            appendLog("[✓] Limbo VMExecutor.start() native boot'a girdi");
                        } else if (state.startsWith("EXIT:")) {
                            appendLog("[QEMU] " + state);
                            break;
                        } else if (state.startsWith("JAVA_ERROR:")) {
                            appendLog("[HATA] " + state);
                            break;
                        }
                    }

                    if (!nativeEntered && System.currentTimeMillis() - launchTime > 30000) {
                        appendLog("[HATA] Limbo native boot 30 saniye içinde başlamadı. Son durum: "
                                + (lastState.isEmpty() ? "durum dosyası oluşmadı" : lastState));
                        break;
                    }
                    Thread.sleep(350);
                }
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

'''
m = m[:start] + method + m[end:]
main.write_text(m)

# Build/version marker. The debug APK will be signed by Gradle/Android build tools,
# not by a hand-written APK signing block.
g = gradle.read_text()
g = g.replace('versionCode 100400', 'versionCode 100416')
g = g.replace("versionName '1.4-direct-bridge'", "versionName '1.16-source-touchguard'")
gradle.write_text(g)
