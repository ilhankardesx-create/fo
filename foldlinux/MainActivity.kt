package id.or.oo.pr

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val DISTRO_ALIAS = "foldlinux"
private const val UBUNTU_BASE_URL = "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/ubuntu-base-24.04.5-base-arm64.tar.gz"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val app = application as App
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF090B0F),
                    surface = Color(0xFF11151C),
                    primary = Color(0xFF7BD88F),
                    secondary = Color(0xFF9DB7FF)
                )
            ) {
                FoldLinuxScreen(app)
            }
        }
    }
}

@Composable
private fun FoldLinuxScreen(app: App) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var installed by remember { mutableStateOf(isFoldLinuxInstalled(app)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (installed) "Ubuntu ortamı hazır" else "Kurulum için ARM64 rootfs seç") }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var logs by remember { mutableStateOf(listOf<String>()) }

    fun addLog(line: String) {
        logs = (logs + line).takeLast(220)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        selectedName = queryDisplayName(context, uri) ?: "linux-rootfs.tar.gz"
        busy = true
        status = "Dosya hazırlanıyor…"
        logs = listOf("Seçildi: ${selectedName}")

        scope.launch {
            val localArchive = withContext(Dispatchers.IO) {
                copyArchiveToPrivateCache(context, uri, selectedName ?: "linux-rootfs.tar.gz") { copiedMb ->
                    withContext(Dispatchers.Main) {
                        status = "Rootfs kopyalanıyor… ${copiedMb} MB"
                    }
                }
            }

            if (localArchive == null) {
                addLog("HATA: Dosya okunamadı.")
                status = "Dosya okunamadı"
                busy = false
                return@launch
            }

            status = "Linux kuruluyor…"
            addLog("Kurulum başlatıldı. Bu işlem birkaç dakika sürebilir.")
            val exitCode = runPrCli(
                app = app,
                args = listOf("install", localArchive.absolutePath, "--override-alias", DISTRO_ALIAS),
                onLine = { addLog(it) }
            )
            withContext(Dispatchers.IO) { localArchive.delete() }

            installed = isFoldLinuxInstalled(app)
            busy = false
            if (exitCode == 0 && installed) {
                status = "Kurulum tamamlandı ✓"
                addLog("FoldLinux hazır. Linux'u Başlat'a basabilirsin.")
            } else {
                status = "Kurulum tamamlanamadı"
                addLog("Çıkış kodu: $exitCode")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B0F))
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🐧",
                fontSize = 46.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "FoldLinux",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (installed) "PC gibi açılan Linux ortamın" else "Telefonuna root gerekmeden Linux kur",
                color = Color(0xFF9CA8B8),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 5.dp, bottom = 20.dp)
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF11151C)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = status,
                        color = if (installed) Color(0xFF7BD88F) else Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp
                    )
                    selectedName?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = Color(0xFF9CA8B8), fontSize = 12.sp)
                    }

                    if (busy) {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    Spacer(Modifier.height(18.dp))

                    if (!installed) {
                        Button(
                            onClick = {
                                filePicker.launch(arrayOf(
                                    "application/gzip",
                                    "application/x-gzip",
                                    "application/x-tar",
                                    "application/octet-stream"
                                ))
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Linux dosyasını seç ve kur")
                        }

                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UBUNTU_BASE_URL)))
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Ubuntu Base ARM64 indir")
                        }
                    } else {
                        Button(
                            onClick = {
                                val intent = Intent(context, TerminalActivity::class.java)
                                intent.putExtra("distro", DISTRO_ALIAS)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Linux'u Başlat")
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "v0.1 uyumluluk testi: ilk açılış terminale düşer. XFCE tam ekran masaüstü bir sonraki aşamada bu çalışan Linux motorunun üstüne bağlanacak.",
                            color = Color(0xFF8995A5),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            if (logs.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF0C1016)
                ) {
                    val scroll = rememberScrollState()
                    LaunchedEffect(logs.size) { scroll.scrollTo(scroll.maxValue) }
                    Text(
                        text = logs.joinToString("\n"),
                        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(14.dp),
                        color = Color(0xFFB8C4D3),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Text(
                text = "FoldLinux Prototype v0.1 • ARM64",
                color = Color(0xFF5F6977),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private fun isFoldLinuxInstalled(app: App): Boolean {
    val oci = File(app.prefixDir, "var/lib/pr/containers/$DISTRO_ALIAS/rootfs")
    val legacy = File(app.prefixDir, "var/lib/pr/installed-rootfs/$DISTRO_ALIAS")
    return oci.isDirectory || legacy.isDirectory
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return null
}

private suspend fun copyArchiveToPrivateCache(
    context: android.content.Context,
    uri: Uri,
    displayName: String,
    onProgressMb: suspend (Long) -> Unit
): File? = withContext(Dispatchers.IO) {
    try {
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._+-]"), "_")
        val outFile = File(context.cacheDir, "foldlinux-${System.currentTimeMillis()}-$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(256 * 1024)
                var total = 0L
                var lastReported = -1L
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                    total += count
                    val mb = total / (1024 * 1024)
                    if (mb != lastReported) {
                        lastReported = mb
                        onProgressMb(mb)
                    }
                }
            }
        } ?: return@withContext null
        outFile
    } catch (_: Throwable) {
        null
    }
}

private suspend fun runPrCli(
    app: App,
    args: List<String>,
    onLine: suspend (String) -> Unit
): Int = withContext(Dispatchers.IO) {
    val binDir = File(app.prefixDir, "bin")
    val prCli = File(binDir, "pr-cli").absolutePath
    val cmd = arrayOf(prCli, *args.toTypedArray())

    val pb = ProcessBuilder(*cmd)
    pb.environment().putAll(
        mapOf(
            "APP_PREFIX" to app.prefixDir.absolutePath,
            "APP_HOME" to app.homeDir.absolutePath,
            "APP_PACKAGE" to app.packageName,
            "PATH" to "${binDir.absolutePath}:/system/bin:/system/xbin",
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_TMP_DIR" to app.cacheDir.absolutePath,
            "HOME" to app.homeDir.absolutePath,
            "TERM" to "xterm-256color",
            "LANG" to "en_US.UTF-8",
            "TMPDIR" to app.cacheDir.absolutePath
        )
    )
    pb.redirectErrorStream(true)

    try {
        val process = pb.start()
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { raw ->
                val cleaned = raw
                    .replace(Regex("\\x1B\\[[0-9;]*[mGKHJ]"), "")
                    .replace("\r", "")
                    .trim()
                if (cleaned.isNotEmpty()) {
                    withContext(Dispatchers.Main) { onLine(cleaned) }
                }
            }
        }
        process.waitFor()
    } catch (t: Throwable) {
        withContext(Dispatchers.Main) { onLine("HATA: ${t.message}") }
        -1
    }
}
