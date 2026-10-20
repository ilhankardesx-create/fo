from pathlib import Path

root = Path('pocketdroid-clean')
main = root / 'app/src/main/java/com/pocketdroid/vm/MainActivity.java'
gradle = root / 'app/build.gradle'

m = main.read_text()

# v1.23: the Lineage 6.12 kernel panics while finalizing a loadable module
# inside arm64 dynamic-SCS parsing (__pi_scs_handle_fde_frame -> module_finalize).
# QEMU 5.1's cortex-a53 model advertises neither PAC nor BTI, which makes the
# kernel choose dynamic SCS. QEMU 5.1's "max" CPU advertises FEAT_BTI, so the
# kernel should keep PAC/BTI protection and skip that dynamic-SCS patch path.
old = '                add(a, "-cpu", "cortex-a53");\n'
new = '                add(a, "-cpu", "max");\n'
assert old in m
m = m.replace(old, new, 1)

m = m.replace('=== v1.22 SOURCE BUILD • FULL PANIC TRACE ===',
              '=== v1.23 SOURCE BUILD • QEMU MAX / BTI ===', 1)

needle = '                appendLog("[✓] ARM64 motor yolu: " + qemu.getAbsolutePath());\n'
assert needle in m
m = m.replace(needle,
              needle + '                appendLog("[CPU] QEMU max • BTI expose • dynamic-SCS panic bypass testi");\n',
              1)
main.write_text(m)

g = gradle.read_text()
g = g.replace('versionCode 100422', 'versionCode 100423')
g = g.replace("versionName '1.22-full-panic-trace'", "versionName '1.23-qemu-max-bti'")
gradle.write_text(g)
