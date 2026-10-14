from pathlib import Path

main = Path('pocketdroid-clean/app/src/main/java/com/pocketdroid/vm/MainActivity.java')
s = main.read_text()

start = s.index('    private void startAndroid() {')
end = s.index('    private static void add(ArrayList<String> list, String a, String b) {')

method = r'''    private void startAndroid() {
        booting = true;
        refreshUi();
        vncView.setVisibility(View.VISIBLE);
        logView.setText("");
        delete(qemuLog());
        delete(serialLog());
        delete(crashStageFile());
        File stateFile = vmFile("qemu-process-state.txt");
        delete(stateFile);
        acquireWakeLock();

        executor.execute(() -> {
            try {
                File firmware = ensureFirmware();
                File vars = vmFile(EFI_VARS);
                File vda = vmFile(DISK_A);
                File vdb = vmFile(DISK_B);
                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");

                appendLog("[✓] ARM64 motor yolu: " + qemu.getAbsolutePath());
                if (!qemu.isFile()) throw new Exception("ARM64 QEMU motoru APK içinde bulunamadı");
                appendLog("[✓] ARM64 motor bulundu");
                appendLog("[✓] UEFI bulundu: " + firmware.length() / 1024 + " KB");
                appendLog("[✓] Sistem diski bulundu: " + vda.length() / 1024 / 1024 + " MB");
                appendLog("[✓] Veri diski bulundu: " + vdb.length() / 1024 / 1024 + " MB");
                appendLog("[→] v0.9 güvenli boot: 1 vCPU + single-thread TCG");

                ArrayList<String> a = new ArrayList<>();
                a.add("qemu-system-aarch64");
                add(a, "-machine", "virt");
                add(a, "-cpu", "max");
                add(a, "-accel", "tcg,thread=single");
                add(a, "-smp", "1");
                add(a, "-m", "2048");
                add(a, "-device", "virtio-blk-pci,drive=vda,bootindex=0");
                add(a, "-device", "virtio-blk-pci,drive=vdb,bootindex=1");
                add(a, "-drive", "if=pflash,unit=0,file=" + firmware.getAbsolutePath() + ",format=raw,readonly=on");
                add(a, "-drive", "if=pflash,unit=1,file=" + vars.getAbsolutePath() + ",format=raw");
                add(a, "-drive", "file=" + vda.getAbsolutePath() + ",if=none,id=vda,discard=unmap,detect-zeroes=unmap");
                add(a, "-drive", "file=" + vdb.getAbsolutePath() + ",if=none,id=vdb,discard=unmap,detect-zeroes=unmap");
                add(a, "-device", "virtio-net-pci,netdev=net0");
                add(a, "-netdev", "user,id=net0,hostfwd=tcp:127.0.0.1:5555-:5555,hostfwd=tcp:127.0.0.1:5554-:5554");
                add(a, "-device", "usb-ehci,id=usb-bus");
                add(a, "-device", "usb-tablet,bus=usb-bus.0");
                add(a, "-device", "usb-mouse,bus=usb-bus.0");
                add(a, "-device", "usb-kbd,bus=usb-bus.0");
                add(a, "-device", "virtio-serial");
                add(a, "-device", "virtio-rng-pci");
                add(a, "-device", "virtio-gpu-pci");
                add(a, "-vnc", "127.0.0.1:1");
                add(a, "-serial", "file:" + serialLog().getAbsolutePath());
                add(a, "-monitor", "none");

                startLogTail();

                Intent service = new Intent(MainActivity.this, QemuProcessService.class);
                service.putExtra("qemuLibPath", qemu.getAbsolutePath());
                service.putExtra("logPath", qemuLog().getAbsolutePath());
                String base = vmDir().getAbsolutePath() + File.separator;
                service.putExtra("storageDir", base);
                service.putExtra("baseDir", base);
                service.putExtra("args", a.toArray(new String[0]));
                service.putExtra("statePath", stateFile.getAbsolutePath());

                runOnUiThread(() -> {
                    startService(service);
                    vncView.connectWithRetry("127.0.0.1", 5901);
                });
                appendLog("[→] QEMU ayrı :qemu processinde başlatıldı — ana PocketDroid artık çökmez.");

                boolean sawQemuProcess = false;
                long launchTime = System.currentTimeMillis();
                while (booting) {
                    String state = readSmallFile(stateFile).trim();
                    if (state.startsWith("EXIT:") || state.startsWith("JAVA_ERROR:")) {
                        appendLog("[QEMU PROCESS] " + state);
                        break;
                    }

                    boolean alive = false;
                    try {
                        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                        java.util.List<android.app.ActivityManager.RunningAppProcessInfo> ps = am.getRunningAppProcesses();
                        if (ps != null) {
                            String wanted = getPackageName() + ":qemu";
                            for (android.app.ActivityManager.RunningAppProcessInfo p : ps) {
                                if (wanted.equals(p.processName)) {
                                    alive = true;
                                    break;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}

                    if (alive) sawQemuProcess = true;
                    if (sawQemuProcess && !alive) {
                        String stage = readSmallFile(crashStageFile()).trim();
                        appendLog("[💥] QEMU process çöktü ama PocketDroid ayakta kaldı.");
                        if (!stage.isEmpty()) appendLog("[SON NATIVE AŞAMA] " + stage);
                        String nativeLog = readSmallFile(qemuLog()).trim();
                        if (!nativeLog.isEmpty()) appendLog("[QEMU CRASH LOG]\n" + nativeLog);
                        break;
                    }

                    if (!sawQemuProcess && System.currentTimeMillis() - launchTime > 8000) {
                        appendLog("[HATA] :qemu process 8 saniye içinde başlayamadı");
                        break;
                    }
                    Thread.sleep(500);
                }
            } catch (Throwable ex) {
                appendLog("[HATA] " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } finally {
                logTailRunning = false;
                releaseWakeLock();
                runOnUiThread(() -> {
                    booting = false;
                    refreshUi();
                });
            }
        });
    }

'''

s = s[:start] + method + s[end:]
main.write_text(s)
