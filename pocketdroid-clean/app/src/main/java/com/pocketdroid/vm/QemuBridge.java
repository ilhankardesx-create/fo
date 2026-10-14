package com.pocketdroid.vm;

import java.io.File;
import java.util.ArrayList;

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
        ArrayList<String> out = new ArrayList<>();
        boolean hasDisplay = false;
        boolean hasVga = false;
        boolean hasSmp = false;

        if (source != null) {
            for (int i = 0; i < source.length; i++) {
                String value = source[i];
                if ("max,pauth-impdef=on".equals(value)) {
                    value = "max";
                } else if ("tcg,tb-size=1024,thread=multi".equals(value)) {
                    value = "tcg,thread=multi";
                }
                if ("-display".equals(value)) hasDisplay = true;
                if ("-vga".equals(value)) hasVga = true;
                if ("-smp".equals(value)) hasSmp = true;
                out.add(value);
            }
        }

        // PocketDroid renders QEMU through its built-in localhost VNC view.
        // Disable any implicit SDL/default VGA path so Android never needs
        // Limbo's SDL Activity just to create the guest display.
        if (!hasDisplay) {
            out.add("-display");
            out.add("none");
        }
        if (!hasVga) {
            out.add("-vga");
            out.add("none");
        }
        if (!hasSmp) {
            out.add("-smp");
            out.add("2");
        }
        return out.toArray(new String[0]);
    }

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
