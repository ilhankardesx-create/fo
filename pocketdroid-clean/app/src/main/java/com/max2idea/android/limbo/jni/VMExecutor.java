package com.max2idea.android.limbo.jni;

/**
 * Minimal PocketDroid wrapper around Limbo's own native JNI bridge.
 *
 * The QEMU runtime bundled into PocketDroid comes directly from Limbo 6.0.1.
 * Using Limbo's original liblimbo JNI entry point keeps the Java/native
 * bootstrap, compatibility layer and QEMU globals exactly as that runtime
 * expects, instead of reimplementing that ABI in PocketDroid.
 */
public final class VMExecutor {
    static {
        // Match LimboActivity's native load order.
        System.loadLibrary("compat-limbo");
        System.loadLibrary("compat-musl");
        System.loadLibrary("glib-2.0");
        System.loadLibrary("pixman-1");

        // Limbo's ARM build is SDL-enabled even when PocketDroid renders via VNC.
        // Load the SDL compatibility pieces before liblimbo/QEMU, exactly as
        // the original app does. The addons library is present in some builds
        // but is not required by the original bootstrap.
        tryLoad("compat-SDL2-addons");
        System.loadLibrary("SDL2");
        System.loadLibrary("compat-SDL2-ext");

        System.loadLibrary("limbo");
        System.loadLibrary("qemu-system-aarch64");
    }

    private static void tryLoad(String name) {
        try {
            System.loadLibrary(name);
        } catch (UnsatisfiedLinkError ignored) {
            // Optional compatibility helper.
        }
    }

    /**
     * Signature intentionally matches Limbo's vm-executor-jni.cpp entry point.
     */
    public native String start(String storageDir,
                               String baseDir,
                               String qemuLibPath,
                               int sdlScaleHint,
                               Object[] params);

    public native String stop(int restart);

    // Called by Limbo's compatibility layer when SDL reports a guest mode.
    // PocketDroid renders through VNC, so no Java-side action is required.
    public static void onVMResolutionChanged(int width, int height) {
    }

    // These callbacks are only used for Limbo's /content/ pseudo-paths.
    // PocketDroid copies VM files into app-private storage and passes normal
    // absolute paths, therefore they should never be reached during boot.
    public int get_fd(String path) {
        return -1;
    }

    public int close_fd(int fd) {
        return -1;
    }
}
