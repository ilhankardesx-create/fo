package com.max2idea.android.limbo.jni;

import android.os.Build;

/**
 * Minimal PocketDroid facade for the exact Limbo 6.0.1 JNI ABI bundled in the
 * imported ARM64 runtime.
 */
public final class VMExecutor {
    static {
        // Exact Limbo 6.0.1 load order. Limbo explicitly requires this setup to
        // happen on an Android main thread before the QEMU worker is started.
        System.loadLibrary("compat-limbo");
        System.loadLibrary("compat-musl");
        System.loadLibrary("glib-2.0");
        System.loadLibrary("pixman-1");

        if (Build.VERSION.SDK_INT >= 26) {
            System.loadLibrary("compat-SDL2-addons");
        }
        System.loadLibrary("SDL2");
        System.loadLibrary("compat-SDL2-ext");
        System.loadLibrary("limbo");
        System.loadLibrary("qemu-system-aarch64");
    }

    /** Force class/native initialization from QemuProcessService.onStartCommand(). */
    public static void ensureNativeLibrariesLoaded() {
        // Intentionally empty. Invoking this method initializes the class.
    }

    /**
     * Exact Java signature used by Limbo 6.0.1's vm-executor-jni.c.
     * libFilename is e.g. "libqemu-system-aarch64.so" while libPath is the
     * full path inside Android's nativeLibraryDir.
     */
    public native String start(String storageDir,
                               String baseDir,
                               String libFilename,
                               String libPath,
                               int sdlScaleHint,
                               Object[] params);

    public native String stop(int restart);

    public static void onVMResolutionChanged(int width, int height) {
        // VNC owns PocketDroid's framebuffer; no SDL-side resize work is needed.
    }

    // Limbo calls these only for /content/ pseudo-paths. PocketDroid copies the
    // selected VM package to app-private files and passes ordinary absolute paths.
    public int get_fd(String path) {
        return -1;
    }

    public int close_fd(int fd) {
        return -1;
    }
}
