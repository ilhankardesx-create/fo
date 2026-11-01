from pathlib import Path
import re

p = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = p.read_text()

# Android 15/16 edge-to-edge + IME handling.
s = s.replace("import android.os.Bundle\n", "import android.os.Build\nimport android.os.Bundle\n")
s = s.replace("import android.view.WindowManager\n", "import android.view.WindowInsets\nimport android.view.WindowManager\n")

# Remove the top-right 'Derin Düşünme' badge completely.
s = re.sub(
    r'\n        val badge = TextView\(this\)\.apply \{.*?\n        top\.addView\(badge, LinearLayout\.LayoutParams\(-2, -2\)\)\n',
    '\n',
    s,
    flags=re.S,
)

# Install explicit IME insets after UI is created.
s = s.replace(
    "            buildUi()\n            restoreModelLabelOnly()",
    "            buildUi()\n            installKeyboardInsets()\n            restoreModelLabelOnly()",
)

marker = "\n    private fun showMenu(anchor: View) {"
keyboard_fix = '''
    private fun installKeyboardInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val ime = insets.getInsets(WindowInsets.Type.ime())
                val bottom = maxOf(bars.bottom, ime.bottom)

                view.setPadding(
                    dp(18) + bars.left,
                    dp(8) + bars.top,
                    dp(18) + bars.right,
                    dp(16) + bottom
                )

                if (ime.bottom > bars.bottom) {
                    scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
                insets
            }
            root.requestApplyInsets()
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }
'''

if "private fun installKeyboardInsets()" not in s:
    s = s.replace(marker, "\n" + keyboard_fix + marker)

# Version text shown in About dialog.
s = s.replace("Prototype 0.6.2", "Prototype 0.6.3")

p.write_text(s)
print("0.6.3 UI patch applied")
