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
                appendLog("[→] v1.0 final boot: Limbo'nun kendi JNI köprüsü + QEMU 5.1");

                ArrayList<String> a = new ArrayList<>();
                // Limbo itself uses the absolute QEMU library path as argv[0].
                a.add(qemu.getAbsolutePath());
                add(a, "-machine", "virt");
                add(a, "-cpu", "max");
                add(a, "-accel", "tcg,thread=single");
                add(a, "-smp", "1");
                add(a, "-m", "2048");

                // UTM LineageOS ARM64 disks + UEFI.
                add(a, "-drive", "if=pflash,unit=0,file=" + firmware.getAbsolutePath() + ",format=raw,readonly=on");
                add(a, "-drive", "if=pflash,unit=1,file=" + vars.getAbsolutePath() + ",format=raw");
                add(a, "-drive", "file=" + vda.getAbsolutePath() + ",if=none,id=vda,format=qcow2,cache=none");
                add(a, "-drive", "file=" + vdb.getAbsolutePath() + ",if=none,id=vdb,format=qcow2,cache=none");
                add(a, "-device", "virtio-blk-pci,drive=vda,bootindex=0");
                add(a, "-device", "virtio-blk-pci,drive=vdb,bootindex=1");

                // Guest devices.
                add(a, "-device", "virtio-net-pci,netdev=net0");
                add(a, "-netdev", "user,id=net0,hostfwd=tcp:127.0.0.1:5555-:5555,hostfwd=tcp:127.0.0.1:5554-:5554");
                add(a, "-device", "virtio-rng-pci");
                add(a, "-device", "virtio-gpu-pci");
                add(a, "-device", "usb-ehci,id=usb-bus");
                add(a, "-device", "usb-tablet,bus=usb-bus.0");
                add(a, "-device", "usb-kbd,bus=usb-bus.0");

                // PocketDroid owns the UI. Never let QEMU wait for an Android
                // SDL surface; VNC is the only display backend we need.
                add(a, "-display", "none");
                add(a, "-vga", "none");
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
                appendLog("[→] :qemu process başlatıldı; VNC 127.0.0.1:5901 bekleniyor.");

                // Do not use ActivityManager.getRunningAppProcesses() here.
                // Modern Android can hide isolated/secondary app processes and
                // v0.9 therefore reported a false 8-second failure even while
                // native QEMU had already entered qemu_init(). The child process
                // now reports its own state through this app-private file.
                boolean nativeEntered = false;
                String lastState = "";
                long launchTime = System.currentTimeMillis();
                while (booting) {
                    String state = readSmallFile(stateFile).trim();
                    if (!state.isEmpty() && !state.equals(lastState)) {
                        lastState = state;
                        if (state.startsWith("SERVICE_STARTED")) {
                            appendLog("[✓] QEMU servisi canlı: " + state);
                        } else if (state.startsWith("LIMBO_BRIDGE_READY")) {
                            appendLog("[✓] Limbo native köprüsü hazır");
                        } else if (state.startsWith("NATIVE_ENTER")) {
                            nativeEntered = true;
                            appendLog("[✓] Limbo VMExecutor native boot'a girdi");
                        } else if (state.startsWith("EXIT:")) {
                            appendLog("[QEMU] " + state);
                            break;
                        } else if (state.startsWith("JAVA_ERROR:")) {
                            appendLog("[HATA] " + state);
                            break;
                        }
                    }

                    if (!nativeEntered && System.currentTimeMillis() - launchTime > 15000) {
                        appendLog("[HATA] Limbo native boot 15 saniye içinde başlamadı. Son durum: "
                                + (lastState.isEmpty() ? "durum dosyası oluşmadı" : lastState));
                        break;
                    }
                    Thread.sleep(350);
                }
            } catch (Throwable ex) {
                appendLog("[HATA] " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } finally {
                logTailRunning = false;
                releaseWakeLock();
                runOnUiThread(() -> {
                    booting = false;
                    primary.setEnabled(true);
                    refreshUi();
                });
            }
        });
    }

'''

s = s[:start] + method + s[end:]
main.write_text(s)
