package com.pocketdroid.vm;

public final class QemuBridge {
    static {
        System.loadLibrary("pocketdroid_bridge");
    }

    private static final QemuBridge INSTANCE = new QemuBridge();

    private QemuBridge() {}

    public static QemuBridge get() {
        return INSTANCE;
    }

    public String start(String qemuLibPath, String logPath,
                        String storageDir, String baseDir, String[] args) {
        return nativeStart(qemuLibPath, logPath, storageDir, baseDir, args);
    }

    public String stop() {
        return nativeStop();
    }

    private native String nativeStart(String qemuLibPath, String logPath,
                                      String storageDir, String baseDir, String[] args);
    private native String nativeStop();

    // The imported Android QEMU compatibility layer can call this when a
    // graphical display changes size. PocketDroid's actual display is VNC,
    // so the callback is intentionally lightweight.
    public static void onVMResolutionChanged(int width, int height) {
    }

    // These two hooks are only used by Limbo's optional Storage Access
    // Framework pseudo-paths (/content/...). PocketDroid extracts its disks
    // into app-private real files, so they should never be needed.
    public int get_fd(String path) {
        return -1;
    }

    public int close_fd(int fd) {
        return -1;
    }
}
