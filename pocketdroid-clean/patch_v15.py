from pathlib import Path

main = Path('pocketdroid-clean/app/src/main/java/com/pocketdroid/vm/MainActivity.java')
s = main.read_text()

s = s.replace(
    "[→] v1.4 boot: doğrudan instrumented QEMU bridge + stage logu",
    "[→] v1.5 boot: kontrollü QEMU start + minimum cihaz seti + monitor continue",
    1,
)

# The LineageOS virtio image only needs the two block devices, network, RNG and
# a virtio GPU to reach Android. Remove the extra USB/virtio-serial devices for
# this boot pass so qemu_init has a much smaller device graph to construct.
old_devices = '''                // Guest devices.\n                add(a, "-device", "virtio-net-pci,netdev=net0");\n                add(a, "-netdev", "user,id=net0,hostfwd=tcp:127.0.0.1:5555-:5555,hostfwd=tcp:127.0.0.1:5554-:5554");\n                add(a, "-device", "usb-ehci,id=usb-bus");\n                add(a, "-device", "usb-tablet,bus=usb-bus.0");\n                add(a, "-device", "usb-mouse,bus=usb-bus.0");\n                add(a, "-device", "usb-kbd,bus=usb-bus.0");\n                add(a, "-device", "virtio-serial");\n                add(a, "-device", "virtio-rng-pci");\n                add(a, "-device", "virtio-gpu-pci");'''
new_devices = '''                // Minimal virtio device set for the LineageOS ARM64 UTM image.\n                // USB and virtio-serial are intentionally left out until the first\n                // successful Android boot; they are not required for VNC display.\n                add(a, "-netdev", "user,id=net0,hostfwd=tcp:127.0.0.1:5555-:5555");\n                add(a, "-device", "virtio-net-pci,netdev=net0");\n                add(a, "-device", "virtio-rng-pci");\n                add(a, "-device", "virtio-gpu-pci");'''
assert old_devices in s
s = s.replace(old_devices, new_devices, 1)

# Start QEMU paused. If qemu_init completes, the VNC + monitor backends exist
# before any guest CPU instruction executes. PocketDroid then sends `cont` over
# the local monitor as soon as the native bridge reaches qemu_main_loop.
old_monitor = '''                add(a, "-vnc", "127.0.0.1:1");\n                add(a, "-serial", "file:" + serialLog().getAbsolutePath());\n                add(a, "-monitor", "none");'''
new_monitor = '''                add(a, "-vnc", "127.0.0.1:1");\n                add(a, "-serial", "file:" + serialLog().getAbsolutePath());\n                a.add("-S");\n                add(a, "-monitor", "tcp:127.0.0.1:4444,server,nowait");'''
assert old_monitor in s
s = s.replace(old_monitor, new_monitor, 1)

marker = '    private void startAndroid() {'
assert marker in s
helper = r'''    private void resumeQemuFromMonitor() {
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + 15000;
            int attempt = 0;
            while (System.currentTimeMillis() < deadline && booting) {
                attempt++;
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress("127.0.0.1", 4444), 750);
                    java.io.OutputStream out = socket.getOutputStream();
                    out.write("cont\n".getBytes(StandardCharsets.US_ASCII));
                    out.flush();
                    appendLog("[✓] QEMU monitor bağlandı; sanal CPU devam ettirildi");
                    return;
                } catch (Exception ignored) {
                    if (attempt == 1 || attempt % 10 == 0) {
                        appendLog("[MONITOR] QEMU continue bekleniyor… (" + attempt + ")");
                    }
                    try { Thread.sleep(250); } catch (InterruptedException ignored2) { return; }
                }
            }
            appendLog("[HATA] QEMU monitor 15 saniye içinde açılamadı");
        }, "PocketDroid-QEMU-Continue").start();
    }

'''
s = s.replace(marker, helper + marker, 1)

old_stage = '''                    String stage = readSmallFile(crashStageFile()).trim();\n                    if (!stage.isEmpty() && !stage.equals(lastStage)) {\n                        lastStage = stage;\n                        appendLog("[STAGE] " + stage);\n                    }'''
new_stage = '''                    String stage = readSmallFile(crashStageFile()).trim();\n                    if (!stage.isEmpty() && !stage.equals(lastStage)) {\n                        lastStage = stage;\n                        appendLog("[STAGE] " + stage);\n                        if ("qemu-main-loop".equals(stage)) {\n                            appendLog("[✓] qemu_init tamamlandı; guest CPU başlatılıyor…");\n                            resumeQemuFromMonitor();\n                        }\n                    }'''
assert old_stage in s
s = s.replace(old_stage, new_stage, 1)
main.write_text(s)

bridge = Path('pocketdroid-clean/app/src/main/cpp/qemu_bridge.cpp')
b = bridge.read_text()

# Mirror Limbo's runtime load order more closely. liblimbo contains the JNI
# executor compatibility layer; loading it globally is harmless for the direct
# bridge and makes the runtime environment match the official app.
old_load = '''    loadGlobal(nativeDir, "libcompat-SDL2-addons.so", false);\n    loadGlobal(nativeDir, "libSDL2.so", false);\n    loadGlobal(nativeDir, "libcompat-SDL2-ext.so", false);\n    return true;'''
new_load = '''    loadGlobal(nativeDir, "libcompat-SDL2-addons.so", false);\n    loadGlobal(nativeDir, "libSDL2.so", false);\n    loadGlobal(nativeDir, "libcompat-SDL2-ext.so", false);\n    if (!loadGlobal(nativeDir, "liblimbo.so", true)) return false;\n    return true;'''
assert old_load in b
b = b.replace(old_load, new_load, 1)

# Limbo's VMExecutor writes this exported QEMU variable before qemu_init().
# The direct bridge in v1.4 omitted it. Restore the same contract.
needle = '''    writeStage(logFile, "qemu-loaded");\n\n    jsize argcJava = env->GetArrayLength(javaArgs);'''
replacement = '''    writeStage(logFile, "qemu-loaded");\n\n    dlerror();\n    auto scaleHint = reinterpret_cast<int*>(dlsym(g_qemu, "limbo_sdl_scale_hint"));\n    const char* scaleErr = dlerror();\n    if (!scaleErr && scaleHint) {\n        *scaleHint = 1;\n        fprintf(stderr, "[PocketDroid] ✓ limbo_sdl_scale_hint=1\\n");\n    } else {\n        fprintf(stderr, "[PocketDroid] ! limbo_sdl_scale_hint ayarlanamadı: %s\\n",\n                scaleErr ? scaleErr : "sembol yok");\n    }\n\n    jsize argcJava = env->GetArrayLength(javaArgs);'''
assert needle in b
b = b.replace(needle, replacement, 1)

# Native-side argv echo survives even if the :qemu process exits inside qemu_init.
needle2 = '''    for (int i = 0; i < argc; ++i) argv[i] = const_cast<char*>(strings[i].c_str());\n    argv[argc] = nullptr;'''
replacement2 = '''    for (int i = 0; i < argc; ++i) argv[i] = const_cast<char*>(strings[i].c_str());\n    argv[argc] = nullptr;\n    fprintf(stderr, "[PocketDroid] native argc=%d\\n", argc);\n    for (int i = 0; i < argc; ++i) {\n        fprintf(stderr, "[PocketDroid] argv[%02d]=%s\\n", i, argv[i] ? argv[i] : "(null)");\n    }\n    fflush(stderr);'''
assert needle2 in b
b = b.replace(needle2, replacement2, 1)

bridge.write_text(b)
