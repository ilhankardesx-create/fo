package id.or.oo.pr

import android.content.Intent
import android.net.Uri
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
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

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

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        selectedName = queryDisplayName(context, uri) ?: "linux-rootfs.tar.gz"
        busy = true
        status = "Dosya hazırlanıyor…"
        logs = listOf("Seçildi: $selectedName")

        scope.launch {
            val localArchive = copyArchiveToPrivateCache(
                context,
                uri,
                selectedName ?: "linux-rootfs.tar.gz"
            ) { mb ->
                withContext(Dispatchers.Main) { status = "Rootfs kopyalanıyor… $mb MB" }
            }

            if (localArchive == null) {
                addLog("HATA: Dosya okunamadı.")
                status = "Dosya okunamadı"
                busy = false
                return@launch
            }

            status = "Linux kuruluyor…"
            addLog("Ubuntu rootfs açılıyor. Bu işlem birkaç dakika sürebilir.")
            val exitCode = installLocalRootfs(app, localArchive) { addLog(it) }
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🐧", fontSize = 46.sp, modifier = Modifier.padding(top = 8.dp))
            Text("FoldLinux", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                if (installed) "PC gibi açılan Linux ortamın" else "Telefonuna root gerekmeden Linux kur",
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
                        status,
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
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UBUNTU_BASE_URL))) },
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
                                context.startActivity(
                                    Intent(context, TerminalActivity::class.java).putExtra("distro", DISTRO_ALIAS)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Text("Linux'u Başlat")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "v0.2 uyumluluk testi: Ubuntu yerel rootfs arşivinden kurulur. İlk açılış terminale düşer; XFCE sonraki aşamada eklenecek.",
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
                        logs.joinToString("\n"),
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
                "FoldLinux Prototype v0.2 • ARM64",
                color = Color(0xFF5F6977),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

private fun isFoldLinuxInstalled(app: App): Boolean {
    val rootfs = File(app.prefixDir, "var/lib/pr/installed-rootfs/$DISTRO_ALIAS")
    return rootfs.isDirectory && File(rootfs, "bin/sh").exists() && File(rootfs, "etc/passwd").exists()
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

private suspend fun installLocalRootfs(
    app: App,
    archive: File,
    onLine: suspend (String) -> Unit
): Int = withContext(Dispatchers.IO) {
    val rootfs = File(app.prefixDir, "var/lib/pr/installed-rootfs/$DISTRO_ALIAS")
    try {
        if (rootfs.exists()) rootfs.deleteRecursively()
        if (!rootfs.mkdirs() && !rootfs.isDirectory) {
            withContext(Dispatchers.Main) { onLine("HATA: rootfs klasörü oluşturulamadı.") }
            return@withContext 10
        }

        val busybox = File(app.prefixDir, "bin/busybox")
        if (!busybox.exists()) {
            withContext(Dispatchers.Main) { onLine("HATA: BusyBox bulunamadı.") }
            rootfs.deleteRecursively()
            return@withContext 11
        }

        withContext(Dispatchers.Main) { onLine("Rootfs çıkartılıyor…") }
        val process = ProcessBuilder(
            busybox.absolutePath,
            "tar",
            "-xzf",
            archive.absolutePath,
            "-C",
            rootfs.absolutePath
        ).redirectErrorStream(true).start()

        process.inputStream.close()
        val code = process.waitFor()
        if (code != 0) {
            withContext(Dispatchers.Main) { onLine("Arşiv açılamadı (tar çıkış kodu $code).") }
            rootfs.deleteRecursively()
            return@withContext code
        }

        val shell = File(rootfs, "bin/sh")
        val passwd = File(rootfs, "etc/passwd")
        if (!shell.exists() || !passwd.exists()) {
            withContext(Dispatchers.Main) { onLine("HATA: Geçerli Linux rootfs bulunamadı (bin/sh veya etc/passwd eksik).") }
            rootfs.deleteRecursively()
            return@withContext 12
        }

        File(rootfs, "tmp").mkdirs()
        File(rootfs, "root").mkdirs()
        val etc = File(rootfs, "etc").apply { mkdirs() }
        val resolv = File(etc, "resolv.conf")
        if (!resolv.exists()) resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        val hosts = File(etc, "hosts")
        if (!hosts.exists()) hosts.writeText("127.0.0.1 localhost\n::1 localhost\n")

        withContext(Dispatchers.Main) {
            onLine("Ubuntu rootfs doğrulandı ✓")
            onLine("/bin/sh ve /etc/passwd hazır ✓")
        }
        0
    } catch (t: Throwable) {
        rootfs.deleteRecursively()
        withContext(Dispatchers.Main) { onLine("HATA: ${t.message}") }
        -1
    }
}
