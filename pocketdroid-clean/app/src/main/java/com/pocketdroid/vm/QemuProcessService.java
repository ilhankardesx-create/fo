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
 * Runs QEMU in an isolated Android process.
 *
 * The actual VM bootstrap deliberately goes through Limbo's own liblimbo JNI
 * bridge because libqemu-system-aarch64.so is the Limbo 6.0.1 runtime. This
 * keeps the Java/native ABI and QEMU initialization path identical to the
 * environment the binary was built and tested for.
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

        Thread t = new Thread(() -> {
            try {
                File qemu = qemuLibPath == null ? null : new File(qemuLibPath);
                if (qemu == null || !qemu.isFile()) {
                    throw new IllegalStateException("QEMU kütüphanesi bulunamadı: " + qemuLibPath);
                }

                // Class initialization loads Limbo's compatibility libraries,
                // liblimbo.so and the ARM64 QEMU runtime in the original order.
                vmExecutor = new VMExecutor();
                writeState(statePath, "LIMBO_BRIDGE_READY pid=" + Process.myPid());

                Object[] nativeArgs = args == null ? new String[0] : args;
                writeState(statePath, "NATIVE_ENTER pid=" + Process.myPid());

                // -1 is Limbo's default SDL scale hint. PocketDroid uses VNC,
                // but the global must still be initialized by liblimbo before
                // qemu_init() just like in the original Limbo app.
                String result = vmExecutor.start(
                        storageDir,
                        baseDir,
                        qemuLibPath,
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
