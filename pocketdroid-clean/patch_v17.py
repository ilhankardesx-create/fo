from pathlib import Path

root = Path('pocketdroid-clean')
main = root / 'app/src/main/java/com/pocketdroid/vm/MainActivity.java'
vnc = root / 'app/src/main/java/com/pocketdroid/vm/VncView.java'
gradle = root / 'app/build.gradle'

# Add safe RFB keyboard input while keeping pointer TouchGuard enabled.
v = vnc.read_text()
needle = '''    private synchronized void sendPointer(int mask, int x, int y) throws IOException {\n        if (out == null) return;\n        out.writeByte(5);\n        out.writeByte(mask);\n        out.writeShort(x);\n        out.writeShort(y);\n        out.flush();\n    }\n'''
assert needle in v
replacement = needle + r'''
    public void sendKeyPress(int keysym) {
        try {
            sendKeyEvent(true, keysym);
            sendKeyEvent(false, keysym);
        } catch (IOException ignored) {
        }
    }

    private synchronized void sendKeyEvent(boolean down, int keysym) throws IOException {
        if (out == null) return;
        // RFB 3.8 KeyEvent: type=4, down-flag, 2 bytes padding, 32-bit keysym.
        out.writeByte(4);
        out.writeByte(down ? 1 : 0);
        out.writeShort(0);
        out.writeInt(keysym);
        out.flush();
    }
'''
v = v.replace(needle, replacement, 1)
vnc.write_text(v)

m = main.read_text()

# Field for the boot-key row.
old_fields = '''    private Button primary;\n    private Button change;\n    private ProgressBar progress;\n    private VncView vncView;'''
new_fields = '''    private Button primary;\n    private Button change;\n    private ProgressBar progress;\n    private VncView vncView;\n    private LinearLayout bootKeys;'''
assert old_fields in m
m = m.replace(old_fields, new_fields, 1)

# Add compact boot-key row immediately below VNC.
old_ui = '''        root.addView(vncView, vncLp);\n\n        logScroll = new ScrollView(this);'''
new_ui = r'''        root.addView(vncView, vncLp);

        bootKeys = new LinearLayout(this);
        bootKeys.setOrientation(LinearLayout.HORIZONTAL);
        bootKeys.setGravity(Gravity.CENTER);
        bootKeys.setVisibility(View.GONE);

        Button keyUp = new Button(this);
        keyUp.setAllCaps(false);
        keyUp.setText("↑");
        keyUp.setTextSize(20);
        keyUp.setOnClickListener(v -> vncView.sendKeyPress(0xff52)); // XK_Up

        Button keyDown = new Button(this);
        keyDown.setAllCaps(false);
        keyDown.setText("↓");
        keyDown.setTextSize(20);
        keyDown.setOnClickListener(v -> vncView.sendKeyPress(0xff54)); // XK_Down

        Button keyEnter = new Button(this);
        keyEnter.setAllCaps(false);
        keyEnter.setText("Enter");
        keyEnter.setTextSize(14);
        keyEnter.setOnClickListener(v -> vncView.sendKeyPress(0xff0d)); // XK_Return

        Button keyBack = new Button(this);
        keyBack.setAllCaps(false);
        keyBack.setText("Geri");
        keyBack.setTextSize(14);
        keyBack.setOnClickListener(v -> vncView.sendKeyPress(0xff1b)); // XK_Escape

        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        keyLp.setMargins(dp(3), 0, dp(3), 0);
        bootKeys.addView(keyUp, keyLp);
        bootKeys.addView(keyDown, keyLp);
        bootKeys.addView(keyEnter, keyLp);
        bootKeys.addView(keyBack, keyLp);

        LinearLayout.LayoutParams bootKeysLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bootKeysLp.topMargin = dp(6);
        root.addView(bootKeys, bootKeysLp);

        logScroll = new ScrollView(this);'''
assert old_ui in m
m = m.replace(old_ui, new_ui, 1)

# Show controls only while a VM boot is active.
old_start = '''        vncView.setVisibility(View.VISIBLE);\n        logView.setText("");'''
new_start = '''        vncView.setVisibility(View.VISIBLE);\n        bootKeys.setVisibility(View.VISIBLE);\n        logView.setText("");'''
assert old_start in m
m = m.replace(old_start, new_start, 1)

# Update version/log marker.
m = m.replace('=== v1.16 SOURCE BUILD • LIMBO PATH • TOUCHGUARD ===',
              '=== v1.17 SOURCE BUILD • SAFE BOOT KEYS ===', 1)
m = m.replace('[TouchGuard] Boot sırasında dokunma QEMU\'ya gönderilmeyecek',
              '[BootKeys] VNC touch kapalı • ↑ ↓ Enter Geri güvenli klavye açık', 1)
main.write_text(m)

# Version bump.
g = gradle.read_text()
g = g.replace('versionCode 100416', 'versionCode 100417')
g = g.replace("versionName '1.16-source-touchguard'", "versionName '1.17-safe-boot-keys'")
gradle.write_text(g)
