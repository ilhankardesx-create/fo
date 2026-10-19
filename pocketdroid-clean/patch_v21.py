from pathlib import Path

root = Path('pocketdroid-clean')
main = root / 'app/src/main/java/com/pocketdroid/vm/MainActivity.java'
gradle = root / 'app/build.gradle'

m = main.read_text()

# Add RandomAccessFile for incremental serial.log tailing.
needle = 'import java.io.InputStream;\n'
assert needle in m
m = m.replace(needle, needle + 'import java.io.RandomAccessFile;\n', 1)

# Add a serial offset next to the existing state/launch tracking.
needle = '''                boolean nativeEntered = false;\n                String lastState = "";\n                long launchTime = System.currentTimeMillis();\n                while (booting) {'''
replacement = '''                boolean nativeEntered = false;\n                String lastState = "";\n                long launchTime = System.currentTimeMillis();\n                long serialOffset = 0L;\n                appendLog("[SerialDiag] Android grafik/boot seri logu izleniyor...");\n                while (booting) {'''
assert needle in m
m = m.replace(needle, replacement, 1)

# Tail only useful graphics / boot-failure lines so the UI does not drown in kernel spam.
needle = '''                    if (!nativeEntered && System.currentTimeMillis() - launchTime > 30000) {\n                        appendLog("[HATA] Limbo native boot 30 saniye içinde başlamadı. Son durum: "\n                                + (lastState.isEmpty() ? "durum dosyası oluşmadı" : lastState));\n                        break;\n                    }\n                    Thread.sleep(350);'''
replacement = '''                    File serialFile = serialLog();\n                    if (serialFile.isFile() && serialFile.length() > serialOffset) {\n                        try (RandomAccessFile raf = new RandomAccessFile(serialFile, "r")) {\n                            raf.seek(serialOffset);\n                            long remaining = raf.length() - serialOffset;\n                            int count = (int) Math.min(32768L, remaining);\n                            byte[] chunk = new byte[count];\n                            int read = raf.read(chunk);\n                            if (read > 0) {\n                                serialOffset += read;\n                                String text = new String(chunk, 0, read, StandardCharsets.UTF_8);\n                                String[] lines = text.split("\\r?\\n");\n                                for (String line : lines) {\n                                    String low = line.toLowerCase(Locale.US);\n                                    if (low.contains("surfaceflinger")\n                                            || low.contains("gralloc")\n                                            || low.contains("hwcomposer")\n                                            || low.contains("composer")\n                                            || low.contains("virtio")\n                                            || low.contains("drm")\n                                            || low.contains("egl")\n                                            || low.contains("opengl")\n                                            || low.contains("vulkan")\n                                            || low.contains("gpu")\n                                            || low.contains("display")\n                                            || low.contains("bootanim")\n                                            || low.contains("fatal")\n                                            || low.contains("failed")\n                                            || low.contains("error")) {\n                                        appendLog("[SERIAL] " + line);\n                                    }\n                                }\n                            }\n                        } catch (Throwable serialError) {\n                            // Diagnostics must never disturb the VM.\n                        }\n                    }\n\n                    if (!nativeEntered && System.currentTimeMillis() - launchTime > 30000) {\n                        appendLog("[HATA] Limbo native boot 30 saniye içinde başlamadı. Son durum: "\n                                + (lastState.isEmpty() ? "durum dosyası oluşmadı" : lastState));\n                        break;\n                    }\n                    Thread.sleep(350);'''
assert needle in m
m = m.replace(needle, replacement, 1)

m = m.replace('=== v1.20 SOURCE BUILD • VDA + VDB ===',
              '=== v1.21 SOURCE BUILD • SERIAL GRAPHICS DIAG ===', 1)
main.write_text(m)

g = gradle.read_text()
g = g.replace('versionCode 100420', 'versionCode 100421')
g = g.replace("versionName '1.20-vda-vdb'", "versionName '1.21-serial-graphics-diag'")
gradle.write_text(g)
