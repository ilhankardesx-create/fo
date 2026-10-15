package com.pocketdroid.vm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Runs the ARM64 QEMU runtime in PocketDroid's isolated :qemu process.
 *
 * v1.4 deliberately uses PocketDroid's own instrumented JNI bridge instead of
 * Limbo's opaque VMExecutor.start(). The bridge writes crash-stage.txt before
 * every native milestone and mirrors QEMU stdout/stderr into qemu.log, so a
 * failed boot can no longer hide behind an endless VNC retry loop.
 */
public class QemuProcessService extends Service {
    private volatile boolean started;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (started || intent == null) return START_NOT_STICKY;
        started = true;

        final String qemuLibPath = intent.getStringExtra("qemuLibPath");
        final String logPath = intent.getStringExtra("logPath");
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
                if (logPath == null || logPath.trim().isEmpty()) {
                    throw new IllegalStateException("QEMU log yolu boş");
                }
                if (storageDir == null || baseDir == null) {
                    throw new IllegalStateException("QEMU storage/base yolu eksik");
                }

                // Force QemuBridge class/JNI loading while Java can still report
                // a useful error through the state file.
                QemuBridge bridge = QemuBridge.get();
                writeState(statePath, "BRIDGE_READY pid=" + Process.myPid());
                writeState(statePath, "NATIVE_ENTER pid=" + Process.myPid());

                String result = bridge.start(
                        qemu.getAbsolutePath(),
                        logPath,
                        storageDir,
                        baseDir,
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
        }, "PocketDroid-QEMU-Direct");
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
