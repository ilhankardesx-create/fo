package com.pocketdroid.vm;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.system.Os;

import com.max2idea.android.limbo.jni.VMExecutor;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Runs the Limbo ARM64 QEMU runtime in PocketDroid's :qemu process.
 */
public class QemuProcessService extends Service {
    private volatile boolean started;
    private volatile VMExecutor vmExecutor;
    private FileOutputStream nativeLogStream;

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

        // QEMU frequently terminates the process directly on a native startup
        // error, so Java never gets a chance to report the exception. Mirror
        // stdout/stderr into qemu.log before loading any native code. The main
        // PocketDroid process already tails this file live.
        try {
            redirectNativeOutput(logPath);
            writeState(statePath, "NATIVE_LOG_READY pid=" + Process.myPid());
        } catch (Throwable t) {
            writeState(statePath, "JAVA_ERROR: log redirect: " + t);
            started = false;
            stopSelf(startId);
            return START_NOT_STICKY;
        }

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
            cleanupLog();
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
                cleanupLog();
                started = false;
                stopSelf(startId);
            }
        }, "PocketDroid-QEMU");
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();

        return START_NOT_STICKY;
    }

    private void redirectNativeOutput(String path) throws Exception {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("qemu.log yolu boş");
        }
        File f = new File(path);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("QEMU log klasörü oluşturulamadı");
        }
        nativeLogStream = new FileOutputStream(f, false);
        Os.dup2(nativeLogStream.getFD(), 1);
        Os.dup2(nativeLogStream.getFD(), 2);
        nativeLogStream.write(("PocketDroid native log ready; pid=" + Process.myPid() + "\n")
                .getBytes(StandardCharsets.UTF_8));
        nativeLogStream.flush();
    }

    private void cleanupLog() {
        try {
            if (nativeLogStream != null) nativeLogStream.flush();
        } catch (Exception ignored) {
        }
        try {
            if (nativeLogStream != null) nativeLogStream.close();
        } catch (Exception ignored) {
        }
        nativeLogStream = null;
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
