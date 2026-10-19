from pathlib import Path

root = Path('pocketdroid-clean')
main = root / 'app/src/main/java/com/pocketdroid/vm/MainActivity.java'
gradle = root / 'app/build.gradle'

m = main.read_text()

# v1.19: the VM uses -nodefaults, so there is no keyboard device for VNC
# KeyEvent packets to reach. Add an xHCI controller + USB HID keyboard so
# UEFI/GRUB can receive Up/Down/Enter/Escape.
needle = '''                add(a, "-device", "virtio-rng-pci");\n                add(a, "-device", "virtio-gpu-pci");\n\n                add(a, "-vnc", "127.0.0.1:1");'''
replacement = '''                add(a, "-device", "virtio-rng-pci");\n                add(a, "-device", "virtio-gpu-pci");\n                add(a, "-device", "qemu-xhci,id=xhci");\n                add(a, "-device", "usb-kbd,bus=xhci.0");\n\n                add(a, "-vnc", "127.0.0.1:1");'''
assert needle in m
m = m.replace(needle, replacement, 1)

m = m.replace('=== v1.18 SOURCE BUILD • ASYNC BOOT KEYS ===',
              '=== v1.19 SOURCE BUILD • USB BOOT KEYBOARD ===', 1)
m = m.replace('[BootKeys] UI thread socket yazmıyor • tuşlar background VNC kuyruğunda',
              '[BootKeys] VNC async + qemu-xhci + usb-kbd aktif', 1)
main.write_text(m)

g = gradle.read_text()
g = g.replace('versionCode 100418', 'versionCode 100419')
g = g.replace("versionName '1.18-async-boot-keys'", "versionName '1.19-usb-boot-keyboard'")
gradle.write_text(g)
