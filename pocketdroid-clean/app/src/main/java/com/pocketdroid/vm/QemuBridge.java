package com.pocketdroid.vm;

public final class QemuBridge {
    static {
        System.loadLibrary("pocketdroid_bridge");
    }

    private QemuBridge() {}

    public static native String nativeStart(String qemuLibPath, String logPath, String[] args);
    public static native String nativeStop();
}
