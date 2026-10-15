from pathlib import Path

main = Path('pocketdroid-clean/app/src/main/java/com/pocketdroid/vm/MainActivity.java')
s = main.read_text()

old = '''                appendLog("[✓] UEFI CODE: " + firmware.length() / 1024 / 1024 + " MB");\n                appendLog("[✓] UEFI VARS: " + vars.length() / 1024 / 1024 + " MB");\n                if (firmware.length() != vars.length()) {\n                    throw new Exception("UEFI CODE/VARS boyutu eşleşmiyor: "\n                            + firmware.length() + " / " + vars.length());\n                }\n                appendLog("[✓] UEFI CODE/VARS eşleşti");\n                appendLog("[✓] Sistem diski bulundu: " + vda.length() / 1024 / 1024 + " MB");'''
new = '''                appendLog("[✓] UEFI CODE: " + firmware.length() / 1024 / 1024 + " MB");\n                appendLog("[✓] UEFI VARS: " + vars.length() + " bayt (" + vars.length() / 1024 + " KB)");\n                if (!vars.isFile() || vars.length() <= 0) {\n                    throw new Exception("UEFI VARS dosyası geçersiz");\n                }\n                appendLog("[✓] UTM EFI VARS dosyası hazır — CODE ile aynı boyutta olması gerekmiyor");\n                appendLog("[✓] Sistem diski bulundu: " + vda.length() / 1024 / 1024 + " MB");'''
assert old in s
s = s.replace(old, new, 1)

s = s.replace("[→] v1.1 final boot: 64 MB AAVMF + Limbo JNI + native hata kaydı",
              "[→] v1.2 boot: UTM EFI vars + AAVMF + Limbo JNI", 1)

main.write_text(s)
