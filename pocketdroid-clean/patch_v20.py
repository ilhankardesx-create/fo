from pathlib import Path

root = Path('pocketdroid-clean')
main = root / 'app/src/main/java/com/pocketdroid/vm/MainActivity.java'
gradle = root / 'app/build.gradle'

m = main.read_text()

old = '''                File vda = vmFile(DISK_A);\n                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");'''
new = '''                File vda = vmFile(DISK_A);\n                File vdb = vmFile(DISK_B);\n                File qemu = new File(getApplicationInfo().nativeLibraryDir, "libqemu-system-aarch64.so");'''
assert old in m
m = m.replace(old, new, 1)

old = '''                if (!vda.isFile()) throw new Exception("vda.qcow2 bulunamadı");\n                appendLog("[✓] AAVMF -bios: " + firmware.getAbsolutePath());\n                appendLog("[✓] VDA: " + vda.getAbsolutePath());'''
new = '''                if (!vda.isFile()) throw new Exception("vda.qcow2 bulunamadı");\n                if (!vdb.isFile()) throw new Exception("vdb.qcow2 bulunamadı");\n                appendLog("[✓] AAVMF -bios: " + firmware.getAbsolutePath());\n                appendLog("[✓] VDA: " + vda.getAbsolutePath());\n                appendLog("[✓] VDB: " + vdb.getAbsolutePath());'''
assert old in m
m = m.replace(old, new, 1)

m = m.replace('[PocketDroid] PFLASH YOK -> AAVMF -bios + SADECE VDA disk ile gerçek boot',
              '[PocketDroid] PFLASH YOK -> AAVMF -bios + VDA + VDB ile gerçek boot', 1)

old = '''                add(a, "-device", "virtio-blk-pci,drive=vda,bootindex=0");\n                add(a, "-device", "virtio-rng-pci");'''
new = '''                add(a, "-device", "virtio-blk-pci,drive=vda,bootindex=0");\n                add(a, "-drive", "file=" + vdb.getAbsolutePath()\n                        + ",if=none,id=vdb,format=qcow2,discard=unmap,detect-zeroes=unmap");\n                add(a, "-device", "virtio-blk-pci,drive=vdb,bootindex=1");\n                add(a, "-device", "virtio-rng-pci");'''
assert old in m
m = m.replace(old, new, 1)

m = m.replace('=== v1.19 SOURCE BUILD • USB BOOT KEYBOARD ===',
              '=== v1.20 SOURCE BUILD • VDA + VDB ===', 1)
m = m.replace('[BootKeys] USB keyboard device aktif • VNC async keys',
              '[BootKeys] USB keyboard aktif • VDA + VDB Lineage disk zinciri', 1)
main.write_text(m)

g = gradle.read_text()
g = g.replace('versionCode 100419', 'versionCode 100420')
g = g.replace("versionName '1.19-usb-boot-keyboard'", "versionName '1.20-vda-vdb'")
gradle.write_text(g)
