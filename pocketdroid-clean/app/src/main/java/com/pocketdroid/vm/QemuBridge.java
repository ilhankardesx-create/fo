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

    public String probe(String qemuLibPath, String logPath) {
        return nativeProbeInternal(qemuLibPath, logPath);
    }

    public String start(String qemuLibPath, String logPath,
                        String storageDir, String baseDir, String[] args) {
        return nativeStartInternal(qemuLibPath, logPath, storageDir, baseDir, sanitize(args));
    }

    public String stop() {
        return nativeStopInternal();
    }

    public static String nativeProbe(String qemuLibPath, String logPath) {
        return INSTANCE.nativeProbeInternal(qemuLibPath, logPath);
    }

    // Compatibility entry points used by older clean-launcher builds.
    public static String nativeStart(String qemuLibPath, String logPath, String[] args) {
        File log = new File(logPath);
        File vmDir = log.getParentFile();
        String base = (vmDir == null ? new File(".") : vmDir).getAbsolutePath() + File.separator;
        return INSTANCE.nativeStartInternal(qemuLibPath, logPath, base, base, sanitize(args));
    }

    public static String nativeStop() {
        return INSTANCE.nativeStopInternal();
    }

    private native String nativeProbeInternal(String qemuLibPath, String logPath);
    private native String nativeStartInternal(String qemuLibPath, String logPath,
                                              String storageDir, String baseDir, String[] args);
    private native String nativeStopInternal();

    private static String[] sanitize(String[] source) {
        ArrayList<String> out = new ArrayList<>();
        boolean hasSmp = false;

        if (source != null) {
            for (String original : source) {
                String value = original;
                if ("max,pauth-impdef=on".equals(value)) {
                    value = "max";
                } else if ("tcg,tb-size=1024,thread=multi".equals(value)) {
                    value = "tcg,thread=multi";
                }
                if ("-smp".equals(value)) hasSmp = true;
                out.add(value);
            }
        }

        // Do not silently inject -display none or -vga none here. The caller
        // explicitly configures virtio-gpu + VNC, and hidden display overrides
        // make native startup failures much harder to reason about.
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
