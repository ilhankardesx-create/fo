package com.pocketdroid.vm;

import java.io.File;

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
        return nativeStart(qemuLibPath, logPath, storageDir, baseDir, sanitize(args));
    }

    public String stop() {
        return nativeStop();
    }

    // Compatibility entry points for the clean v0.7 launcher. They are our own
    // thin bridge and do not go through Limbo's UI, settings or VM database.
    public static String nativeStart(String qemuLibPath, String logPath, String[] args) {
        File log = new File(logPath);
        File vmDir = log.getParentFile();
        String base = (vmDir == null ? new File(".") : vmDir).getAbsolutePath() + File.separator;
        return INSTANCE.nativeStart(qemuLibPath, logPath, base, base, sanitize(args));
    }

    // Object... keeps this overload distinct from the private zero-argument
    // native method while allowing existing QemuBridge.nativeStop() calls.
    public static String nativeStop(Object... ignored) {
        return INSTANCE.nativeStop();
    }

    private native String nativeStart(String qemuLibPath, String logPath,
                                      String storageDir, String baseDir, String[] args);
    private native String nativeStop();

    private static String[] sanitize(String[] source) {
        if (source == null) return new String[0];
        String[] out = source.clone();
        for (int i = 0; i < out.length; i++) {
            if ("max,pauth-impdef=on".equals(out[i])) {
                out[i] = "max";
            } else if ("tcg,tb-size=1024,thread=multi".equals(out[i])) {
                out[i] = "tcg,thread=multi";
            }
        }
        return out;
    }

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
