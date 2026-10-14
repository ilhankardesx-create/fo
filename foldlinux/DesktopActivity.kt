package id.or.oo.pr

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.or.oo.pr.engine.ProotLauncher
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class DesktopActivity : ComponentActivity() {

    companion object {
        private const val TAG = "FoldLinuxDesktop"
        private const val DISTRO = "foldlinux"
        private const val DESKTOP_URL = "http://127.0.0.1:6080/vnc.html?autoconnect=1&resize=remote&quality=6&compression=6"
    }

    private var session: ProotLauncher.Session? = null
    private var readerThread: Thread? = null
    private var webView: WebView? = null
    private var statusView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private var pageLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        buildUi()
        startLinuxDesktop()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                shutdownDesktop()
                finish()
            }
        })
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val browser = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            isFocusable = true
            isFocusableInTouchMode = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.contains("vnc.html") == true) {
                        pageLoaded = true
                        statusView?.visibility = View.GONE
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true && !pageLoaded) {
                        scheduleRetry()
                    }
                }
            }
        }
        webView = browser
        root.addView(browser, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val status = TextView(this).apply {
            text = "🐧 FoldLinux\nUbuntu masaüstü başlatılıyor…"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }
        statusView = status
        root.addView(status, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 8)
            setBackgroundColor(0x99000000.toInt())
        }

        val close = TextView(this).apply {
            text = "  ✕  "
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener {
                shutdownDesktop()
                finish()
            }
        }
        controls.addView(close, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            52
        ))

        val reload = TextView(this).apply {
            text = "  ↻  "
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener {
                pageLoaded = false
                statusView?.apply {
                    text = "🐧 FoldLinux\nMasaüstüne yeniden bağlanılıyor…"
                    visibility = View.VISIBLE
                }
                retryCount = 0
                scheduleRetry(250)
            }
        }
        controls.addView(reload, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            52
        ))

        root.addView(controls, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ))

        setContentView(root)
    }

    private fun startLinuxDesktop() {
        val app = application as App
        val launcher = ProotLauncher(app)
        val sess = launcher.startSession(DISTRO, rows = 30, cols = 120)
        if (sess == null) {
            statusView?.text = "Linux oturumu başlatılamadı."
            return
        }
        session = sess

        readerThread = thread(name = "foldlinux-desktop-pty-reader") {
            val buffer = ByteArray(8192)
            while (!sess.closed) {
                try {
                    val n = sess.read(buffer)
                    if (n < 0) break
                    if (n > 0) {
                        Log.d(TAG, String(buffer, 0, n))
                    }
                } catch (_: Throwable) {
                    break
                }
            }
        }

        val metrics = resources.displayMetrics
        var width = max(800, metrics.widthPixels)
        var height = max(700, metrics.heightPixels)
        val longest = max(width, height).toFloat()
        if (longest > 1600f) {
            val scale = 1600f / longest
            width = max(800, (width * scale).toInt())
            height = max(700, (height * scale).toInt())
        }
        width = min(width, 1920)
        height = min(height, 1600)

        val command = buildString {
            append("export DISPLAY=:1; ")
            append("export XDG_RUNTIME_DIR=/tmp/runtime-root; ")
            append("mkdir -p /tmp/runtime-root /tmp/.X11-unix; chmod 700 /tmp/runtime-root; ")
            append("dbus-uuidgen --ensure=/etc/machine-id >/dev/null 2>&1 || true; ")
            append("pkill -f 'websockify.*6080' >/dev/null 2>&1 || true; ")
            append("pkill -f Xtigervnc >/dev/null 2>&1 || true; ")
            append("rm -f /tmp/.X1-lock /tmp/.X11-unix/X1; ")
            append("Xtigervnc :1 -geometry ${width}x${height} -depth 24 -SecurityTypes None -localhost yes -ac >/tmp/foldlinux-vnc.log 2>&1 & ")
            append("sleep 2; ")
            append("DISPLAY=:1 dbus-launch --exit-with-session startxfce4 >/tmp/foldlinux-xfce.log 2>&1 & ")
            append("sleep 2; ")
            append("websockify --web=/usr/share/novnc 6080 localhost:5901 >/tmp/foldlinux-novnc.log 2>&1 & ")
            append("echo FOLDLINUX_DESKTOP_READY")
        }

        handler.postDelayed({
            session?.write((command + "\n").toByteArray(Charsets.UTF_8))
        }, 1000)

        statusView?.text = "🐧 FoldLinux\nXFCE ve görüntü sunucusu başlatılıyor…"
        scheduleRetry(5500)
    }

    private fun scheduleRetry(delayMs: Long = 1800) {
        handler.postDelayed({
            if (pageLoaded || isFinishing) return@postDelayed
            if (retryCount >= 10) {
                statusView?.text = "Masaüstüne bağlanılamadı.\n↻ düğmesiyle yeniden dene."
                return@postDelayed
            }
            retryCount++
            webView?.loadUrl(DESKTOP_URL)
        }, delayMs)
    }

    private fun shutdownDesktop() {
        handler.removeCallbacksAndMessages(null)
        try {
            session?.write(
                ("pkill -f 'websockify.*6080' >/dev/null 2>&1 || true; " +
                    "pkill -f Xtigervnc >/dev/null 2>&1 || true; " +
                    "pkill -f xfce4-session >/dev/null 2>&1 || true; exit\n")
                    .toByteArray(Charsets.UTF_8)
            )
        } catch (_: Throwable) {
        }
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = null
        readerThread?.interrupt()
        readerThread = null
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        webView = null
    }

    override fun onDestroy() {
        shutdownDesktop()
        super.onDestroy()
    }
}
