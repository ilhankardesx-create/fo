package id.or.oo.pr

import android.content.ClipboardManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.or.oo.pr.engine.ProotLauncher
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import kotlin.concurrent.thread

class TerminalActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FoldLinux"
    }

    private var session: ProotLauncher.Session? = null
    private var emulator: TerminalEmulator? = null
    private var readerThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val distroName = intent.getStringExtra("distro") ?: run {
            finish()
            return
        }
        val autoCommand = intent.getStringExtra("autoCommand")
        val autoClose = intent.getBooleanExtra("autoClose", false)
        val showKeyboard = intent.getBooleanExtra("showKeyboard", true)

        val app = application as App
        val launcher = ProotLauncher(app)

        val em = TerminalEmulatorFactory.create(
            initialRows = 30,
            initialCols = 100,
            defaultForeground = Color.White,
            defaultBackground = Color.Black,
            onKeyboardInput = { data -> session?.write(data) ?: -1 },
            onResize = { dims -> session?.resize(dims.rows, dims.columns) }
        )
        emulator = em

        val sess = launcher.startSession(distroName)
        if (sess == null) {
            Log.e(TAG, "Failed to start session for $distroName")
            finish()
            return
        }
        session = sess

        readerThread = thread(name = "foldlinux-pty-reader") {
            val buf = ByteArray(8192)
            while (!sess.closed) {
                try {
                    val n = sess.read(buf)
                    if (n < 0) break
                    if (n > 0) em.writeInput(buf, 0, n)
                } catch (e: Exception) {
                    if (!sess.closed) Log.e(TAG, "PTY read error", e)
                    break
                }
            }
            if (autoClose && !isFinishing) {
                runOnUiThread { finish() }
            }
        }

        if (!autoCommand.isNullOrBlank()) {
            Handler(Looper.getMainLooper()).postDelayed({
                session?.write((autoCommand + "\n").toByteArray(Charsets.UTF_8))
            }, 1200)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                cleanup()
                finish()
            }
        })

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        TerminalToolbar(
                            onPaste = { pasteClipboard() },
                            onEsc = { session?.write(byteArrayOf(0x1B)) },
                            onTab = { session?.write(byteArrayOf(0x09)) },
                            onCtrlC = { session?.write(byteArrayOf(0x03)) },
                            onClose = {
                                cleanup()
                                finish()
                            }
                        )
                        HorizontalDivider(color = Color(0xFF252A33))
                        Terminal(
                            terminalEmulator = em,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            initialFontSize = 12.sp,
                            backgroundColor = Color.Black,
                            foregroundColor = Color.White,
                            keyboardEnabled = true,
                            showSoftKeyboard = showKeyboard
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TerminalToolbar(
        onPaste: () -> Unit,
        onEsc: () -> Unit,
        onTab: () -> Unit,
        onCtrlC: () -> Unit,
        onClose: () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(onClick = onClose) { Text("←") }
            TextButton(onClick = onPaste) { Text("Yapıştır") }
            TextButton(onClick = onEsc) { Text("Esc") }
            TextButton(onClick = onTab) { Text("Tab") }
            TextButton(onClick = onCtrlC) { Text("Ctrl+C") }
        }
    }

    private fun pasteClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        session?.write(text.toByteArray(Charsets.UTF_8))
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        session?.close()
        session = null
        readerThread?.interrupt()
        readerThread = null
    }
}
