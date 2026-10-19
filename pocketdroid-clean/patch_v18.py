from pathlib import Path

root = Path('pocketdroid-clean')
vnc = root / 'app/src/main/java/com/pocketdroid/vm/VncView.java'
main = root / 'app/src/main/java/com/pocketdroid/vm/MainActivity.java'
gradle = root / 'app/build.gradle'

# v1.18: never write RFB input packets from Android's main/UI thread.
# v1.17 button clicks called OutputStream.write() directly, which can raise
# NetworkOnMainThreadException and kill the Activity. Queue input on one
# dedicated background writer instead.
v = vnc.read_text()

imports = 'import java.nio.charset.StandardCharsets;\n'
assert imports in v
v = v.replace(imports, imports + 'import java.util.concurrent.ExecutorService;\nimport java.util.concurrent.Executors;\n', 1)

field = '    private int fbHeight;\n'
assert field in v
v = v.replace(field, field + '''    private final ExecutorService inputExecutor = Executors.newSingleThreadExecutor(r -> {\n        Thread t = new Thread(r, "PocketDroid-VNC-Input");\n        t.setDaemon(true);\n        return t;\n    });\n''', 1)

old = '''    public void sendKeyPress(int keysym) {\n        try {\n            sendKeyEvent(true, keysym);\n            sendKeyEvent(false, keysym);\n        } catch (IOException ignored) {\n        }\n    }\n'''
assert old in v
new = '''    public void sendKeyPress(int keysym) {\n        // Button callbacks run on the Android UI thread. Never touch a Socket\n        // from that thread; enqueue the whole key press on our VNC writer.\n        inputExecutor.execute(() -> {\n            try {\n                sendKeyEvent(true, keysym);\n                sendKeyEvent(false, keysym);\n            } catch (Throwable ex) {\n                String msg = ex.getMessage();\n                notifyMessage("VNC tuş gönderilemedi: "\n                        + ex.getClass().getSimpleName()\n                        + (msg == null ? "" : ": " + msg));\n            }\n        });\n    }\n'''
v = v.replace(old, new, 1)
vnc.write_text(v)

m = main.read_text()
m = m.replace('=== v1.17 SOURCE BUILD • SAFE BOOT KEYS ===',
              '=== v1.18 SOURCE BUILD • ASYNC BOOT KEYS ===', 1)
m = m.replace('[BootKeys] VNC touch kapalı • ↑ ↓ Enter Geri güvenli klavye açık',
              '[BootKeys] UI thread socket yazmıyor • tuşlar background VNC kuyruğunda', 1)
main.write_text(m)

g = gradle.read_text()
g = g.replace('versionCode 100417', 'versionCode 100418')
g = g.replace("versionName '1.17-safe-boot-keys'", "versionName '1.18-async-boot-keys'")
gradle.write_text(g)
