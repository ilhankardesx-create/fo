package com.pocketdroid.vm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

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

        writeState(statePath, "STARTING");

        Thread t = new Thread(() -> {
            try {
                String result = QemuBridge.get().start(
                        qemuLibPath,
                        logPath,
                        storageDir,
                        baseDir,
                        args == null ? new String[0] : args);
                writeState(statePath, "EXIT: " + result);
            } catch (Throwable t1) {
                writeState(statePath, "JAVA_ERROR: " + t1.getClass().getSimpleName() + ": " + t1.getMessage());
            } finally {
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
