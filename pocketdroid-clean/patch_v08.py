from pathlib import Path

vnc = Path('pocketdroid-clean/app/src/main/java/com/pocketdroid/vm/VncView.java')
s = vnc.read_text()
old = '''        out.writeByte(32);\n        out.writeByte(24);\n        out.writeByte(1);\n        out.writeByte(1);'''
new = '''        out.writeByte(32);\n        out.writeByte(24);\n        out.writeByte(0);\n        out.writeByte(1);'''
if old in s:
    vnc.write_text(s.replace(old, new, 1))

main = Path('pocketdroid-clean/app/src/main/java/com/pocketdroid/vm/MainActivity.java')
s = main.read_text()

old = '''    private volatile boolean preparing;\n    private volatile boolean booting;\n    private volatile boolean logTailRunning;'''
new = '''    private volatile boolean preparing;\n    private volatile boolean booting;\n    private volatile boolean probing;\n    private volatile boolean probePassed;\n    private volatile boolean logTailRunning;'''
assert old in s
s = s.replace(old, new, 1)

old = '''        buildUi();\n        refreshUi();'''
new = '''        buildUi();\n        showPreviousNativeStage();\n        refreshUi();'''
assert old in s
s = s.replace(old, new, 1)

old = '''        primary.setOnClickListener(v -> {\n            if (preparing || booting) return;\n            if (isVmReady()) startAndroid(); else choosePackage();\n        });'''
new = '''        primary.setOnClickListener(v -> {\n            if (preparing || booting || probing) return;\n            if (!isVmReady()) {\n                choosePackage();\n            } else if (!probePassed) {\n                runMotorProbe();\n            } else {\n                startAndroid();\n            }\n        });'''
assert old in s
s = s.replace(old, new, 1)

old = '''        progress.setVisibility(preparing || booting ? View.VISIBLE : View.GONE);\n        if (booting) {'''
new = '''        progress.setVisibility(preparing || booting || probing ? View.VISIBLE : View.GONE);\n        if (probing) {\n            status.setText("QEMU motoru test ediliyor…");\n            detail.setText("Android henüz başlatılmıyor • güvenli native kontrol");\n            primary.setText("Motor test ediliyor…");\n            primary.setEnabled(false);\n            change.setVisibility(View.GONE);\n        } else if (booting) {'''
assert old in s
s = s.replace(old, new, 1)

old = '''        } else if (isVmReady()) {\n            status.setText("LineageOS ARM64 hazır");\n            detail.setText("vda.qcow2 + vdb.qcow2 + UEFI hazır");\n            primary.setText("Android’i Başlat");\n            primary.setEnabled(true);\n            change.setVisibility(View.VISIBLE);'''
new = '''        } else if (isVmReady()) {\n            status.setText(probePassed ? "QEMU motor testi geçti ✅" : "LineageOS ARM64 hazır");\n            detail.setText(probePassed\n                    ? "Motor sağlam görünüyor • şimdi gerçek bootu deneyebiliriz"\n                    : "Önce QEMU'yu Android başlatmadan güvenli test edeceğiz");\n            primary.setText(probePassed ? "Gerçek Bootu Dene" : "Motoru Test Et");\n            primary.setEnabled(true);\n            change.setVisibility(View.VISIBLE);'''
assert old in s
s = s.replace(old, new, 1)

marker = '''    private void startAndroid() {'''
assert marker in s
methods = r'''    private File crashStageFile() {
        return vmFile("crash-stage.txt");
    }

    private String readSmallFile(File file) {
        if (file == null || !file.isFile()) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            int amount = (int) Math.min(file.length(), 64 * 1024);
            byte[] data = new byte[amount];
            int off = 0;
            while (off < amount) {
                int n = in.read(data, off, amount - off);
                if (n < 0) break;
                off += n;
            }
            return new String(data, 0, off, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void showPreviousNativeStage() {
        File marker = crashStageFile();
        if (!marker.isFile()) return;
        String stage = readSmallFile(marker).trim();
        if (!stage.isEmpty()) {
            appendLog("[⚠] Önceki native çalıştırma bu aşamada kesildi: " + stage);
            String oldLog = readSmallFile(qemuLog()).trim();
            if (!oldLog.isEmpty()) appendLog("[ÖNCEKİ QEMU LOG]\n" + oldLog);
        }
    }

    private void runMotorProbe() {
        probing = true;
        probePassed = false;
        logView.setText("");
        delete(qemuLog());
        refreshUi();
        appendLog("[→] Güvenli QEMU motor testi başlıyor…");

        executor.execute(() -> {
            try {
                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");
                appendLog("[✓] ARM64 motor yolu: " + qemu.getAbsolutePath());
                if (!qemu.isFile()) throw new Exception("ARM64 QEMU motoru APK içinde bulunamadı");

                String result = QemuBridge.nativeProbe(qemu.getAbsolutePath(), qemuLog().getAbsolutePath());
                String nativeLog = readSmallFile(qemuLog()).trim();
                if (!nativeLog.isEmpty()) appendLog("[NATIVE]\n" + nativeLog);

                if ("PROBE_OK".equals(result)) {
                    probePassed = true;
                    appendLog("[✅] MOTOR TESTİ BAŞARILI — qemu_init çağrılmadan tüm kütüphaneler ve semboller doğrulandı.");
                } else {
                    appendLog("[HATA] " + result);
                }
            } catch (Throwable ex) {
                appendLog("[HATA] " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            } finally {
                runOnUiThread(() -> {
                    probing = false;
                    refreshUi();
                });
            }
        });
    }

'''
s = s.replace(marker, methods + marker, 1)
main.write_text(s)
