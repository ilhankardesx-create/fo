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
        return nativeStartInternal(qemuLibPath, logPath, storageDir, baseDir, sanitize(args));
    }

    public String stop() {
        return nativeStopInternal();
    }

    // Compatibility entry points used by the clean v0.7 launcher. These go
    // straight to our JNI bridge; no Limbo UI/settings/VM database is involved.
    public static String nativeStart(String qemuLibPath, String logPath, String[] args) {
        File log = new File(logPath);
        File vmDir = log.getParentFile();
        String base = (vmDir == null ? new File(".") : vmDir).getAbsolutePath() + File.separator;
        return INSTANCE.nativeStartInternal(qemuLibPath, logPath, base, base, sanitize(args));
    }

    public static String nativeStop() {
        return INSTANCE.nativeStopInternal();
    }

    private native String nativeStartInternal(String qemuLibPath, String logPath,
                                              String storageDir, String baseDir, String[] args);
    private native String nativeStopInternal();

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

    // Only needed for /content/ pseudo-paths. PocketDroid uses real app-private
    // files, so these hooks should not be reached.
    public int get_fd(String path) {
        return -1;
    }

    public int close_fd(int fd) {
        return -1;
    }
}
