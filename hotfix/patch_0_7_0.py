from pathlib import Path
import re

main = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = main.read_text()

# System DownloadManager keeps the multi-GB model download alive across temporary
# app interruptions and handles redirects/retries for the public Hugging Face file.
s = s.replace("import android.app.AlertDialog\n", "import android.app.AlertDialog\nimport android.app.DownloadManager\n")

# Persistent download state + the recommended public model.
s = s.replace(
    '        private const val KEY_MODEL_NAME = "model_name"\n',
    '        private const val KEY_MODEL_NAME = "model_name"\n'
    '        private const val KEY_DOWNLOAD_ID = "model_download_id"\n'
    '        private const val DEFAULT_MODEL_NAME = "Qwen3 4B"\n'
    '        private const val DEFAULT_MODEL_FILE = "qwen3_4b_mixed_int4.litertlm"\n'
    '        private const val DEFAULT_MODEL_URL = "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm"\n'
    '        private const val DEFAULT_MODEL_MIN_FREE_BYTES = 3_500_000_000L\n'
)

s = s.replace(
    '    private var engine: ModelBridge? = null\n',
    '    private var engine: ModelBridge? = null\n    private var downloadMonitorJob: Job? = null\n'
)

# The model status chip becomes the one-tap entry point for normal users.
needle = '        welcome.addView(status, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(26) })\n'
if needle not in s:
    raise SystemExit("status addView block not found")
s = s.replace(needle, needle + '        status.setOnClickListener { handleModelStatusTap() }\n')

# Keep manual import for power users, but put automatic download first.
s = s.replace(
    '            menu.add("Model seç / içe aktar")\n',
    '            menu.add("Önerilen modeli indir")\n            menu.add("Model seç / içe aktar")\n'
)
s = s.replace(
    '                when (item.title.toString()) {\n                    "Model seç / içe aktar" -> chooseModel()\n',
    '                when (item.title.toString()) {\n                    "Önerilen modeli indir" -> confirmDefaultModelDownload()\n                    "Model seç / içe aktar" -> chooseModel()\n'
)

insert_marker = '    private fun chooseModel() {'
if insert_marker not in s:
    raise SystemExit("chooseModel marker not found")

auto_code = r'''    private data class DownloadState(
        val status: Int,
        val downloaded: Long,
        val total: Long,
        val reason: Int
    )

    private fun autoModelFile(): File {
        val dir = getExternalFilesDir("models") ?: File(filesDir, "models")
        dir.mkdirs()
        return File(dir, DEFAULT_MODEL_FILE)
    }

    private fun handleModelStatusTap() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val path = prefs.getString(KEY_MODEL_PATH, null)
        if (!path.isNullOrBlank() && File(path).exists()) {
            if (engine?.isLoaded == true) {
                status.text = "✓ Model hazır"
                return
            }
            progress.visibility = View.VISIBLE
            progress.isIndeterminate = true
            status.text = "Model hazırlanıyor…"
            send.isEnabled = false
            launch {
                runCatching { ensureSavedModelLoaded() }
                    .onSuccess { backend ->
                        progress.visibility = View.GONE
                        send.isEnabled = true
                        status.text = "✓ Model hazır · $backend"
                    }
                    .onFailure { error ->
                        progress.visibility = View.GONE
                        send.isEnabled = true
                        status.text = "Model başlatılamadı · tekrar dene"
                        Toast.makeText(
                            this@MainActivity,
                            "Model açılamadı: ${error.message ?: error.javaClass.simpleName}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            return
        }

        val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId >= 0L) {
            monitorModelDownload(downloadId, loadWhenDone = true)
        } else {
            confirmDefaultModelDownload()
        }
    }

    private fun confirmDefaultModelDownload() {
        val targetDir = getExternalFilesDir("models")
        if (targetDir == null) {
            Toast.makeText(this, "Model depolama alanı kullanılamıyor.", Toast.LENGTH_LONG).show()
            return
        }
        targetDir.mkdirs()
        if (targetDir.usableSpace < DEFAULT_MODEL_MIN_FREE_BYTES) {
            Toast.makeText(
                this,
                "Model için en az 3,5 GB boş alan gerekiyor.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("AI modelini indir")
            .setMessage(
                "Offline AI, önerilen Qwen3 4B modelini yaklaşık 2,5 GB olarak indirecek. " +
                    "İndirme bir kez yapılır; sonrasında sohbet için internet gerekmez. " +
                    "Wi‑Fi kullanmanız önerilir."
            )
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("İndir") { _, _ -> startDefaultModelDownload() }
            .show()
    }

    private fun startDefaultModelDownload() {
        val targetDir = getExternalFilesDir("models") ?: run {
            Toast.makeText(this, "Model depolama alanı kullanılamıyor.", Toast.LENGTH_LONG).show()
            return
        }
        targetDir.mkdirs()
        val target = File(targetDir, DEFAULT_MODEL_FILE)

        // A previously completed automatic download can be reused immediately.
        if (target.exists() && target.length() > 2_000_000_000L) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_MODEL_PATH, target.absolutePath)
                .putString(KEY_MODEL_NAME, DEFAULT_MODEL_NAME)
                .remove(KEY_DOWNLOAD_ID)
                .apply()
            handleModelStatusTap()
            return
        }

        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val oldId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (oldId >= 0L) runCatching { manager.remove(oldId) }
        target.delete()

        val request = DownloadManager.Request(Uri.parse(DEFAULT_MODEL_URL))
            .setTitle("Offline AI modeli")
            .setDescription("Qwen3 4B indiriliyor")
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(this, "models", DEFAULT_MODEL_FILE)

        val id = manager.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()

        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.max = 100
        progress.progress = 0
        status.text = "Model indiriliyor · %0"
        monitorModelDownload(id, loadWhenDone = true)
    }

    private fun queryDownload(downloadId: Long): DownloadState {
        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
            if (!cursor.moveToFirst()) {
                return DownloadState(DownloadManager.STATUS_FAILED, 0L, -1L, DownloadManager.ERROR_UNKNOWN)
            }
            val statusIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val downloadedIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val reasonIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            return DownloadState(
                status = cursor.getInt(statusIndex),
                downloaded = cursor.getLong(downloadedIndex),
                total = cursor.getLong(totalIndex),
                reason = cursor.getInt(reasonIndex)
            )
        }
    }

    private fun monitorModelDownload(downloadId: Long, loadWhenDone: Boolean) {
        downloadMonitorJob?.cancel()
        downloadMonitorJob = launch {
            while (true) {
                val state = withContext(Dispatchers.IO) { queryDownload(downloadId) }
                when (state.status) {
                    DownloadManager.STATUS_PENDING -> {
                        progress.visibility = View.VISIBLE
                        progress.isIndeterminate = true
                        status.text = "Model indirmesi hazırlanıyor…"
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        progress.visibility = View.VISIBLE
                        if (state.total > 0L) {
                            val percent = ((state.downloaded * 100L) / state.total).toInt().coerceIn(0, 100)
                            progress.isIndeterminate = false
                            progress.max = 100
                            progress.progress = percent
                            status.text = "Model indiriliyor · %$percent"
                        } else {
                            progress.isIndeterminate = true
                            status.text = "Model indiriliyor…"
                        }
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        progress.visibility = View.VISIBLE
                        progress.isIndeterminate = true
                        status.text = "İndirme duraklatıldı · devam edecek"
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = autoModelFile()
                        if (!file.exists() || file.length() < 100_000_000L) {
                            progress.visibility = View.GONE
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .remove(KEY_DOWNLOAD_ID)
                                .apply()
                            status.text = "Model dosyası doğrulanamadı · tekrar dene"
                            return@launch
                        }

                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_MODEL_PATH, file.absolutePath)
                            .putString(KEY_MODEL_NAME, DEFAULT_MODEL_NAME)
                            .remove(KEY_DOWNLOAD_ID)
                            .apply()

                        if (!loadWhenDone) {
                            progress.visibility = View.GONE
                            status.text = "✓ $DEFAULT_MODEL_NAME · indirildi"
                            return@launch
                        }

                        progress.visibility = View.VISIBLE
                        progress.isIndeterminate = true
                        status.text = "Model kuruluyor…"
                        send.isEnabled = false
                        runCatching { getOrCreateEngine().load(file.absolutePath) }
                            .onSuccess { backend ->
                                progress.visibility = View.GONE
                                send.isEnabled = true
                                status.text = "✓ Model hazır · $backend"
                                Toast.makeText(
                                    this@MainActivity,
                                    "Model hazır. Artık internet gerekmez.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            .onFailure { error ->
                                progress.visibility = View.GONE
                                send.isEnabled = true
                                status.text = "Model indirildi · başlatmak için dokun"
                                Toast.makeText(
                                    this@MainActivity,
                                    "Model indirildi ancak başlatılamadı: ${error.message ?: error.javaClass.simpleName}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        progress.visibility = View.GONE
                        progress.isIndeterminate = true
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .remove(KEY_DOWNLOAD_ID)
                            .apply()
                        status.text = "İndirme başarısız · tekrar dene"
                        Toast.makeText(
                            this@MainActivity,
                            "Model indirilemedi (kod ${state.reason}). Durum yazısına dokunup tekrar deneyin.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                }
                delay(800)
            }
        }
    }

'''
s = s.replace(insert_marker, auto_code + insert_marker)

# New installs advertise the automatic model; interrupted DownloadManager jobs are
# reattached without touching LiteRT during Activity startup.
restore_pattern = re.compile(
    r'''    // Startup safety: only display the saved-model label\. Do NOT touch LiteRT,\n    // GPU, CPU, native libraries or the model file during Activity startup\.\n    private fun restoreModelLabelOnly\(\) \{.*?\n    \}\n\n    private fun getOrCreateEngine''',
    re.S,
)
restore_replacement = '''    // Startup safety: display model/download state only. LiteRT itself is not
    // initialized here, so a downloaded model can never break Activity startup.
    private fun restoreModelLabelOnly() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val path = prefs.getString(KEY_MODEL_PATH, null)
        val name = prefs.getString(KEY_MODEL_NAME, DEFAULT_MODEL_NAME) ?: DEFAULT_MODEL_NAME
        if (!path.isNullOrBlank() && File(path).exists()) {
            status.text = "✓ $name · cihazda hazır"
            return
        }

        val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId >= 0L) {
            status.text = "Model indiriliyor…"
            monitorModelDownload(downloadId, loadWhenDone = false)
        } else {
            status.text = "Modeli indir · Qwen3 4B · ~2,5 GB"
        }
    }

    private fun getOrCreateEngine'''
s, n = restore_pattern.subn(restore_replacement, s, count=1)
if n != 1:
    raise SystemExit("restoreModelLabelOnly block not found")

# If the user tries to chat before setup, lead them into the one-tap setup instead
# of asking them to understand .litertlm files.
s = s.replace(
    '            Toast.makeText(this, "Önce sol üst menüden bir .litertlm model seç.", Toast.LENGTH_LONG).show()\n            return\n',
    '            Toast.makeText(this, "Önce AI modelini indir.", Toast.LENGTH_LONG).show()\n            confirmDefaultModelDownload()\n            return\n'
)

# Removing the model also cancels an active system download and deletes whichever
# model path is actually registered (manual or automatic).
remove_pattern = re.compile(
    r'''    private fun removeModel\(\) \{\n        val active = engine\n        launch \{.*?\n        \}\n    \}\n\n    private fun showConversation''',
    re.S,
)
remove_replacement = '''    private fun removeModel() {
        val active = engine
        launch {
            if (active != null) runCatching { active.closeModel() }
            engine = null
            downloadMonitorJob?.cancel()
            downloadMonitorJob = null

            val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
            val savedPath = prefs.getString(KEY_MODEL_PATH, null)
            val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
            if (downloadId >= 0L) {
                val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                runCatching { manager.remove(downloadId) }
            }

            savedPath?.let { runCatching { File(it).delete() } }
            runCatching { autoModelFile().delete() }
            runCatching { File(filesDir, "models/model.litertlm").delete() }

            prefs.edit()
                .remove(KEY_MODEL_PATH)
                .remove(KEY_MODEL_NAME)
                .remove(KEY_DOWNLOAD_ID)
                .apply()

            progress.visibility = View.GONE
            status.text = "Modeli indir · Qwen3 4B · ~2,5 GB"
            newChat()
        }
    }

    private fun showConversation'''
s, n = remove_pattern.subn(remove_replacement, s, count=1)
if n != 1:
    raise SystemExit("removeModel block not found")

s = s.replace("Prototype 0.6.4", "Prototype 0.7.0")
s = s.replace(
    '"Güvenli açılış: model motoru uygulama başlangıcında yüklenmez.\\n" +',
    '"Model tek dokunuşla otomatik indirilip kurulur.\\n" +'
)

main.write_text(s)

manifest = Path("project/app/src/main/AndroidManifest.xml")
m = manifest.read_text()
if 'android.permission.INTERNET' not in m:
    m = m.replace(
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
        '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.INTERNET" />'
    )
manifest.write_text(m)

print("0.7.0 automatic model downloader patch applied")
