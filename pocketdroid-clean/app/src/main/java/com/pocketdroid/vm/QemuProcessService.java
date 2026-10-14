package com.pocketdroid.vm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import com.max2idea.android.limbo.jni.VMExecutor;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Runs the Limbo ARM64 QEMU runtime in PocketDroid's isolated :qemu process.
 */
public class QemuProcessService extends Service {
    private volatile boolean started;
    private volatile VMExecutor vmExecutor;

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

        // Limbo 6.0.1 explicitly initializes its native libraries from the
        // Android main thread before starting the QEMU worker. Do the same in
        // this process. Loading them from the worker can make QEMU fail later.
        try {
            VMExecutor.ensureNativeLibrariesLoaded();
            writeState(statePath, "LIMBO_LIBS_READY pid=" + Process.myPid());
        } catch (Throwable t) {
            String message = t.getMessage();
            writeState(statePath,
                    "JAVA_ERROR: native load: " + t.getClass().getSimpleName()
                            + (message == null ? "" : ": " + message));
            started = false;
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        Thread t = new Thread(() -> {
            try {
                File qemu = qemuLibPath == null ? null : new File(qemuLibPath);
                if (qemu == null || !qemu.isFile()) {
                    throw new IllegalStateException("QEMU kütüphanesi bulunamadı: " + qemuLibPath);
                }

                vmExecutor = new VMExecutor();
                writeState(statePath, "LIMBO_BRIDGE_READY pid=" + Process.myPid());

                Object[] nativeArgs = args == null ? new String[0] : args;
                writeState(statePath, "NATIVE_ENTER pid=" + Process.myPid());

                // This exactly matches Limbo 6.0.1's JNI ABI:
                // storageDir, baseDir, library filename, full library path,
                // SDL scale hint, argv[]. liblimbo then performs setup_jni(),
                // initializes the Limbo QEMU globals, calls qemu_init(), runs
                // qemu_main_loop() and finally qemu_cleanup().
                String result = vmExecutor.start(
                        storageDir,
                        baseDir,
                        qemu.getName(),
                        qemu.getAbsolutePath(),
                        -1,
                        nativeArgs);

                writeState(statePath, "EXIT: " + result);
            } catch (Throwable t1) {
                String message = t1.getMessage();
                writeState(statePath,
                        "JAVA_ERROR: " + t1.getClass().getSimpleName()
                                + (message == null ? "" : ": " + message));
            } finally {
                vmExecutor = null;
                started = false;
                stopSelf(startId);
            }
        }, "PocketDroid-QEMU");
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();

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
