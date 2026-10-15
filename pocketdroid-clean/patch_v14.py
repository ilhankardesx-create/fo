from pathlib import Path

main = Path('pocketdroid-clean/app/src/main/java/com/pocketdroid/vm/MainActivity.java')
s = main.read_text()

s = s.replace(
    "[→] v1.3 boot: Limbo ROM data + ayrı storage/base + tam argv logu",
    "[→] v1.4 boot: doğrudan instrumented QEMU bridge + stage logu",
    1,
)

old = '''                    if (!state.isEmpty() && !state.equals(lastState)) {
                        lastState = state;
                        if (state.startsWith("SERVICE_STARTED")) {
                            appendLog("[✓] QEMU servisi canlı: " + state);
                        } else if (state.startsWith("LIMBO_LIBS_READY")) {
                            appendLog("[✓] Limbo native kütüphaneleri ana thread'de hazır");
                        } else if (state.startsWith("LIMBO_BRIDGE_READY")) {
                            appendLog("[✓] Limbo VMExecutor hazır");
                        } else if (state.startsWith("NATIVE_ENTER")) {
                            nativeEntered = true;
                            appendLog("[✓] Limbo VMExecutor native boot'a girdi");
                        } else if (state.startsWith("EXIT:")) {
                            appendLog("[QEMU] " + state);
                            break;
                        } else if (state.startsWith("JAVA_ERROR:")) {
                            appendLog("[HATA] " + state);
                            break;
                        }
                    }

                    if (!nativeEntered && System.currentTimeMillis() - launchTime > 15000) {
                        appendLog("[HATA] Limbo native boot 15 saniye içinde başlamadı. Son durum: "
                                + (lastState.isEmpty() ? "durum dosyası oluşmadı" : lastState));
                        break;
                    }
                    Thread.sleep(350);'''

new = '''                    if (!state.isEmpty() && !state.equals(lastState)) {
                        lastState = state;
                        if (state.startsWith("SERVICE_STARTED")) {
                            appendLog("[✓] QEMU servisi canlı: " + state);
                        } else if (state.startsWith("BRIDGE_READY")) {
                            appendLog("[✓] PocketDroid native bridge hazır");
                        } else if (state.startsWith("NATIVE_ENTER")) {
                            nativeEntered = true;
                            appendLog("[✓] Doğrudan QEMU native boot'a girdi");
                        } else if (state.startsWith("EXIT:")) {
                            appendLog("[QEMU] " + state);
                            break;
                        } else if (state.startsWith("JAVA_ERROR:")) {
                            appendLog("[HATA] " + state);
                            break;
                        }
                    }

                    String stage = readSmallFile(crashStageFile()).trim();
                    if (!stage.isEmpty() && !stage.equals(lastStage)) {
                        lastStage = stage;
                        appendLog("[STAGE] " + stage);
                    }

                    if (!nativeEntered && System.currentTimeMillis() - launchTime > 15000) {
                        appendLog("[HATA] Doğrudan native boot 15 saniye içinde başlamadı. Son durum: "
                                + (lastState.isEmpty() ? "durum dosyası oluşmadı" : lastState));
                        break;
                    }
                    Thread.sleep(350);'''

assert old in s
s = s.replace(old, new, 1)

old = '''                boolean nativeEntered = false;
                String lastState = "";
                long launchTime = System.currentTimeMillis();'''
new = '''                boolean nativeEntered = false;
                String lastState = "";
                String lastStage = "";
                long launchTime = System.currentTimeMillis();'''
assert old in s
s = s.replace(old, new, 1)

main.write_text(s)
