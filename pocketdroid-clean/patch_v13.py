from pathlib import Path

main = Path('pocketdroid-clean/app/src/main/java/com/pocketdroid/vm/MainActivity.java')
s = main.read_text()

# Limbo does not point -L at the VM disk directory. Its FileInstaller copies
# assets/roms into a separate base directory and QEMU uses that directory for
# keymaps/firmware/ROM data. PocketDroid v1.2 pointed both storage_dir and
# base_dir at vmDir(), while our APK did not package Limbo's assets/roms at all.
# v1.3 restores that part of Limbo's runtime contract and prints the complete
# argv before JNI entry so the next device log is fully actionable.

marker = '    private void startAndroid() {'
assert marker in s
helpers = r'''    private File ensureQemuDataDir() throws Exception {
        File dir = new File(getFilesDir(), "qemu-data");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("QEMU veri klasörü oluşturulamadı: " + dir.getAbsolutePath());
        }

        File ready = new File(dir, ".limbo-roms-v601-ready");
        if (!ready.isFile()) {
            appendLog("[→] Limbo ROM/keymap dosyaları kuruluyor…");
            int copied = copyAssetTree("roms", dir);
            if (copied <= 0) {
                throw new Exception("APK içinde Limbo assets/roms bulunamadı");
            }
            try (FileOutputStream out = new FileOutputStream(ready)) {
                out.write(("files=" + copied + "\n").getBytes(StandardCharsets.UTF_8));
            }
            appendLog("[✓] Limbo QEMU veri dosyaları kuruldu: " + copied + " dosya");
        } else {
            appendLog("[✓] Limbo QEMU veri klasörü hazır");
        }
        return dir;
    }

    private int copyAssetTree(String assetPath, File destination) throws Exception {
        String[] children = getAssets().list(assetPath);
        if (children != null && children.length > 0) {
            if (!destination.exists() && !destination.mkdirs()) {
                throw new Exception("Klasör oluşturulamadı: " + destination.getAbsolutePath());
            }
            int count = 0;
            for (String child : children) {
                String nextAsset = assetPath.isEmpty() ? child : assetPath + "/" + child;
                count += copyAssetTree(nextAsset, new File(destination, child));
            }
            return count;
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new Exception("Klasör oluşturulamadı: " + parent.getAbsolutePath());
        }
        try (InputStream in = getAssets().open(assetPath);
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destination), 64 * 1024)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        return 1;
    }

'''
s = s.replace(marker, helpers + marker, 1)

old = '''                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");\n                String base = vmDir().getAbsolutePath() + File.separator;'''
new = '''                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");\n                File qemuDataDir = ensureQemuDataDir();\n                String storage = vmDir().getAbsolutePath() + File.separator;\n                String base = qemuDataDir.getAbsolutePath() + File.separator;'''
assert old in s
s = s.replace(old, new, 1)

old = '''                add(a, "-L", base);\n                add(a, "-overcommit", "mem-lock=off");\n                add(a, "-rtc", "base=localtime");\n\n                startLogTail();'''
new = '''                add(a, "-L", base);\n                add(a, "-overcommit", "mem-lock=off");\n                add(a, "-rtc", "base=localtime");\n\n                appendLog("[✓] QEMU storage_dir: " + storage);\n                appendLog("[✓] QEMU base_dir/-L: " + base);\n                appendLog("[ARGS] argc=" + a.size());\n                for (int i = 0; i < a.size(); i++) {\n                    appendLog(String.format(Locale.US, "[ARGV %02d] %s", i, a.get(i)));\n                }\n\n                startLogTail();'''
assert old in s
s = s.replace(old, new, 1)

old = '''                service.putExtra("storageDir", base);\n                service.putExtra("baseDir", base);'''
new = '''                service.putExtra("storageDir", storage);\n                service.putExtra("baseDir", base);'''
assert old in s
s = s.replace(old, new, 1)

s = s.replace("[→] v1.2 boot: UTM EFI vars + AAVMF + Limbo JNI",
              "[→] v1.3 boot: Limbo ROM data + ayrı storage/base + tam argv logu", 1)

main.write_text(s)
