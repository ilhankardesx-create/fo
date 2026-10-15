from pathlib import Path

main = Path('pocketdroid-clean/app/src/main/java/com/pocketdroid/vm/MainActivity.java')
s = main.read_text()

# The previous workflow could accidentally pick Ubuntu's compact 2 MiB
# QEMU_EFI.fd because it searched AAVMF_CODE.fd and QEMU_EFI.fd together and
# took the first result. UTM's efi_vars.fd is a 64 MiB pflash image, so that
# mismatch makes QEMU abort during machine initialization before VNC can bind.
# Force the packaged 64 MiB AAVMF image to replace any stale 2 MiB copy left
# in app-private storage from an older PocketDroid install.
old = r'''    private File ensureFirmware() throws Exception {
        File f = vmFile(EFI_CODE);
        if (f.isFile() && f.length() > 0) return f;
        if (!vmDir().exists() && !vmDir().mkdirs()) throw new Exception("VM klasörü açılamadı");
        File tmp = new File(vmDir(), EFI_CODE + ".tmp");
        delete(tmp);
        try (InputStream in = getAssets().open(EFI_CODE);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp), 1024 * 1024)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        replace(tmp, f);
        return f;
    }
'''
new = r'''    private File ensureFirmware() throws Exception {
        final long expectedFlashSize = 64L * 1024L * 1024L;
        File f = vmFile(EFI_CODE);
        if (f.isFile() && f.length() == expectedFlashSize) return f;

        if (f.exists()) {
            appendLog("[→] Eski/uyumsuz UEFI siliniyor: " + f.length() / 1024 + " KB");
            delete(f);
        }
        if (!vmDir().exists() && !vmDir().mkdirs()) throw new Exception("VM klasörü açılamadı");

        File tmp = new File(vmDir(), EFI_CODE + ".tmp");
        delete(tmp);
        try (InputStream in = getAssets().open(EFI_CODE);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(tmp), 1024 * 1024)) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }

        if (tmp.length() != expectedFlashSize) {
            long actual = tmp.length();
            delete(tmp);
            throw new Exception("Paket içindeki UEFI 64 MB değil: " + actual + " bayt");
        }
        replace(tmp, f);
        return f;
    }
'''
assert old in s
s = s.replace(old, new, 1)

old = '''                appendLog("[✓] UEFI bulundu: " + firmware.length() / 1024 + " KB");\n                appendLog("[✓] Sistem diski bulundu: " + vda.length() / 1024 / 1024 + " MB");'''
new = '''                appendLog("[✓] UEFI CODE: " + firmware.length() / 1024 / 1024 + " MB");\n                appendLog("[✓] UEFI VARS: " + vars.length() / 1024 / 1024 + " MB");\n                if (firmware.length() != vars.length()) {\n                    throw new Exception("UEFI CODE/VARS boyutu eşleşmiyor: "\n                            + firmware.length() + " / " + vars.length());\n                }\n                appendLog("[✓] UEFI CODE/VARS eşleşti");\n                appendLog("[✓] Sistem diski bulundu: " + vda.length() / 1024 / 1024 + " MB");'''
assert old in s
s = s.replace(old, new, 1)

s = s.replace("[→] v1.0 final boot: Limbo 6.0.1'in kendi JNI yolu",
              "[→] v1.1 final boot: 64 MB AAVMF + Limbo JNI + native hata kaydı", 1)

main.write_text(s)
