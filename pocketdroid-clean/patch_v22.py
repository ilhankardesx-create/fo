from pathlib import Path

root = Path('pocketdroid-clean')
main = root / 'app/src/main/java/com/pocketdroid/vm/MainActivity.java'
gradle = root / 'app/build.gradle'

m = main.read_text()

# v1.22: keep a rolling serial history. Once an Oops / kernel panic appears,
# dump the recent unfiltered context and then stream every following line.
needle = '''                long launchTime = System.currentTimeMillis();
                long serialOffset = 0L;
                appendLog("[SerialDiag] Android grafik/boot seri logu izleniyor...");
                while (booting) {'''
replacement = '''                long launchTime = System.currentTimeMillis();
                long serialOffset = 0L;
                ArrayList<String> serialHistory = new ArrayList<>();
                boolean panicTraceMode = false;
                appendLog("[SerialDiag] Panic/Oops bekleniyor; son 160 satır hafızada tutuluyor...");
                while (booting) {'''
assert needle in m
m = m.replace(needle, replacement, 1)

old = '''                                String text = new String(chunk, 0, read, StandardCharsets.UTF_8);
                                String[] lines = text.split("\\r?\\n");
                                for (String line : lines) {
                                    String low = line.toLowerCase(Locale.US);
                                    if (low.contains("surfaceflinger")
                                            || low.contains("gralloc")
                                            || low.contains("hwcomposer")
                                            || low.contains("composer")
                                            || low.contains("virtio")
                                            || low.contains("drm")
                                            || low.contains("egl")
                                            || low.contains("opengl")
                                            || low.contains("vulkan")
                                            || low.contains("gpu")
                                            || low.contains("display")
                                            || low.contains("bootanim")
                                            || low.contains("fatal")
                                            || low.contains("failed")
                                            || low.contains("error")) {
                                        appendLog("[SERIAL] " + line);
                                    }
                                }'''
new = '''                                String text = new String(chunk, 0, read, StandardCharsets.UTF_8);
                                String[] lines = text.split("\\r?\\n");
                                for (String line : lines) {
                                    if (line.isEmpty()) continue;

                                    serialHistory.add(line);
                                    if (serialHistory.size() > 160) {
                                        serialHistory.remove(0);
                                    }

                                    String low = line.toLowerCase(Locale.US);
                                    boolean panicTrigger = low.contains("kernel panic")
                                            || low.contains("oops:")
                                            || low.contains("fatal exception")
                                            || low.contains("unable to handle kernel")
                                            || low.contains("internal error: oops");

                                    if (panicTrigger && !panicTraceMode) {
                                        panicTraceMode = true;
                                        appendLog("========== KERNEL PANIC TRACE BEGIN ==========");
                                        appendLog("[PANIC] Tetikleyici: " + line);
                                        appendLog("[PANIC] Öncesindeki son serial satırları:");
                                        for (String oldLine : serialHistory) {
                                            appendLog("[PANIC] " + oldLine);
                                        }
                                        appendLog("[PANIC] --- Bundan sonrası filtresiz canlı trace ---");
                                        continue;
                                    }

                                    if (panicTraceMode) {
                                        appendLog("[PANIC] " + line);
                                    } else if (low.contains("surfaceflinger")
                                            || low.contains("gralloc")
                                            || low.contains("hwcomposer")
                                            || low.contains("composer")
                                            || low.contains("virtio")
                                            || low.contains("drm")
                                            || low.contains("egl")
                                            || low.contains("opengl")
                                            || low.contains("vulkan")
                                            || low.contains("gpu")
                                            || low.contains("display")
                                            || low.contains("bootanim")
                                            || low.contains("fatal")
                                            || low.contains("failed")
                                            || low.contains("error")) {
                                        appendLog("[SERIAL] " + line);
                                    }
                                }'''
assert old in m
m = m.replace(old, new, 1)

m = m.replace('=== v1.21 SOURCE BUILD • SERIAL GRAPHICS DIAG ===',
              '=== v1.22 SOURCE BUILD • FULL PANIC TRACE ===', 1)
main.write_text(m)

g = gradle.read_text()
g = g.replace('versionCode 100421', 'versionCode 100422')
g = g.replace("versionName '1.21-serial-graphics-diag'", "versionName '1.22-full-panic-trace'")
gradle.write_text(g)
