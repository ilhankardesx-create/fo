from pathlib import Path
import re

main = Path("project/app/src/main/java/com/offlineai/app/MainActivity.kt")
s = main.read_text()

# Device RAM check for warning users before choosing the heavier 4B model.
s = s.replace("import android.app.AlertDialog\n", "import android.app.ActivityManager\nimport android.app.AlertDialog\n")

# Persist which automatic model is currently being downloaded so interrupted
# downloads can be resumed and installed correctly after reopening the app.
s = s.replace(
    '        private const val KEY_DOWNLOAD_ID = "model_download_id"\n',
    '        private const val KEY_DOWNLOAD_ID = "model_download_id"\n'
    '        private const val KEY_DOWNLOAD_MODEL = "model_download_key"\n'
)

# The 0.7.0 auto model code is replaced with a two-tier model system.
region = re.compile(
    r'''    private data class DownloadState\(.*?\n    private fun chooseModel\(\) \{''',
    re.S,
)

replacement = r'''    private data class ModelOption(
        val key: String,
        val label: String,
        val name: String,
        val fileName: String,
        val url: String,
        val sizeLabel: String,
        val minFreeBytes: Long,
        val minValidBytes: Long,
        val powerful: Boolean
    )

    private data class DownloadState(
        val status: Int,
        val downloaded: Long,
        val total: Long,
        val reason: Int
    )

    private val fastModel = ModelOption(
        key = "fast",
        label = "Hızlı",
        name = "Qwen3 1.7B",
        fileName = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        sizeLabel = "~0,9 GB",
        minFreeBytes = 1_400_000_000L,
        minValidBytes = 700_000_000L,
        powerful = false
    )

    private val powerfulModel = ModelOption(
        key = "powerful",
        label = "Güçlü",
        name = "Qwen3 4B",
        fileName = "qwen3_4b_mixed_int4.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-4B/resolve/main/qwen3_4b_mixed_int4.litertlm",
        sizeLabel = "~2,5 GB",
        minFreeBytes = 3_500_000_000L,
        minValidBytes = 2_000_000_000L,
        powerful = true
    )

    private fun modelByKey(key: String?): ModelOption =
        if (key == powerfulModel.key) powerfulModel else fastModel

    private fun autoModelFile(model: ModelOption): File {
        val dir = getExternalFilesDir("models") ?: File(filesDir, "models")
        dir.mkdirs()
        return File(dir, model.fileName)
    }

    private fun totalRamGb(): Int {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L * 1024L)).toInt()
    }

    private fun showModelChooser() {
        val ramGb = totalRamGb()
        val recommendation = if (ramGb < 10) {
            "Bu cihaz yaklaşık $ramGb GB RAM. Hızlı model önerilir."
        } else {
            "Bu cihaz yaklaşık $ramGb GB RAM. Güçlü modeli kullanabilirsiniz."
        }

        val options = arrayOf(
            "⚡ Hızlı · Qwen3 1.7B · ~0,9 GB\nDaha az RAM, daha hızlı cevap",
            "🧠 Güçlü · Qwen3 4B · ~2,5 GB\nDaha iyi cevaplar, daha fazla RAM"
        )

        AlertDialog.Builder(this)
            .setTitle("AI modelini seç")
            .setMessage(recommendation)
            .setItems(options) { _, which ->
                confirmModelDownload(if (which == 0) fastModel else powerfulModel)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
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
            val model = modelByKey(prefs.getString(KEY_DOWNLOAD_MODEL, null))
            monitorModelDownload(downloadId, model, loadWhenDone = true)
        } else {
            showModelChooser()
        }
    }

    private fun confirmModelDownload(model: ModelOption) {
        val targetDir = getExternalFilesDir("models")
        if (targetDir == null) {
            Toast.makeText(this, "Model depolama alanı kullanılamıyor.", Toast.LENGTH_LONG).show()
            return
        }
        targetDir.mkdirs()
        if (targetDir.usableSpace < model.minFreeBytes) {
            val needed = if (model.powerful) "3,5 GB" else "1,4 GB"
            Toast.makeText(
                this,
                "${model.label} model için en az $needed boş alan gerekiyor.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val ramGb = totalRamGb()
        val message = if (model.powerful && ramGb < 10) {
            "⚠️ Bu cihaz yaklaşık $ramGb GB RAM. Güçlü Qwen3 4B modeli çalışabilir fakat düşük/orta güçlü cihazlarda cevaplar daha geç gelebilir, uygulama ağırlaşabilir veya bellek yetersiz kalabilir.\n\n" +
                "Model ${model.sizeLabel} indirilecek. İndirme bir kez yapılır; sonrasında internet gerekmez."
        } else if (model.powerful) {
            "Güçlü Qwen3 4B modeli daha iyi cevaplar verir fakat daha fazla RAM ve pil kullanır. " +
                "Model ${model.sizeLabel} indirilecek; sonrasında internet gerekmez."
        } else {
            "Hızlı Qwen3 1.7B modeli daha az RAM kullanır ve düşük/orta güçlü telefonlar için uygundur. " +
                "Model ${model.sizeLabel} indirilecek; sonrasında internet gerekmez."
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("${model.label} modeli indir")
            .setMessage(message)
            .setNegativeButton("Vazgeç", null)
            .setPositiveButton("İndir") { _, _ -> startModelDownload(model) }

        if (model.powerful && ramGb < 10) {
            builder.setNeutralButton("Hızlı modeli seç") { _, _ -> confirmModelDownload(fastModel) }
        }
        builder.show()
    }

    private fun startModelDownload(model: ModelOption) {
        val targetDir = getExternalFilesDir("models") ?: run {
            Toast.makeText(this, "Model depolama alanı kullanılamıyor.", Toast.LENGTH_LONG).show()
            return
        }
        targetDir.mkdirs()
        val target = autoModelFile(model)

        if (target.exists() && target.length() > model.minValidBytes) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_MODEL_PATH, target.absolutePath)
                .putString(KEY_MODEL_NAME, "${model.label} · ${model.name}")
                .remove(KEY_DOWNLOAD_ID)
                .remove(KEY_DOWNLOAD_MODEL)
                .apply()
            handleModelStatusTap()
            return
        }

        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val oldId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (oldId >= 0L) runCatching { manager.remove(oldId) }
        target.delete()

        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("Offline AI · ${model.label} model")
            .setDescription("${model.name} indiriliyor")
            .setMimeType("application/octet-stream")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(this, "models", model.fileName)

        val id = manager.enqueue(request)
        prefs.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_DOWNLOAD_MODEL, model.key)
            .apply()

        progress.visibility = View.VISIBLE
        progress.isIndeterminate = false
        progress.max = 100
        progress.progress = 0
        status.text = "${model.label} model indiriliyor · %0"
        monitorModelDownload(id, model, loadWhenDone = true)
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

    private fun monitorModelDownload(downloadId: Long, model: ModelOption, loadWhenDone: Boolean) {
        downloadMonitorJob?.cancel()
        downloadMonitorJob = launch {
            while (true) {
                val state = withContext(Dispatchers.IO) { queryDownload(downloadId) }
                when (state.status) {
                    DownloadManager.STATUS_PENDING -> {
                        progress.visibility = View.VISIBLE
                        progress.isIndeterminate = true
                        status.text = "${model.label} model hazırlanıyor…"
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        progress.visibility = View.VISIBLE
                        if (state.total > 0L) {
                            val percent = ((state.downloaded * 100L) / state.total).toInt().coerceIn(0, 100)
                            progress.isIndeterminate = false
                            progress.max = 100
                            progress.progress = percent
                            status.text = "${model.label} model indiriliyor · %$percent"
                        } else {
                            progress.isIndeterminate = true
                            status.text = "${model.label} model indiriliyor…"
                        }
                    }
                    DownloadManager.STATUS_PAUSED -> {
                        progress.visibility = View.VISIBLE
                        progress.isIndeterminate = true
                        status.text = "İndirme duraklatıldı · devam edecek"
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = autoModelFile(model)
                        if (!file.exists() || file.length() < model.minValidBytes) {
                            progress.visibility = View.GONE
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .remove(KEY_DOWNLOAD_ID)
                                .remove(KEY_DOWNLOAD_MODEL)
                                .apply()
                            status.text = "Model dosyası doğrulanamadı · tekrar dene"
                            return@launch
                        }

                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_MODEL_PATH, file.absolutePath)
                            .putString(KEY_MODEL_NAME, "${model.label} · ${model.name}")
                            .remove(KEY_DOWNLOAD_ID)
                            .remove(KEY_DOWNLOAD_MODEL)
                            .apply()

                        if (!loadWhenDone) {
                            progress.visibility = View.GONE
                            status.text = "✓ ${model.label} · ${model.name} · indirildi"
                            return@launch
                        }

                        progress.visibility = View.VISIBLE
                        progress.isIndeterminate = true
                        status.text = "${model.label} model kuruluyor…"
                        send.isEnabled = false
                        runCatching { getOrCreateEngine().load(file.absolutePath) }
                            .onSuccess { backend ->
                                progress.visibility = View.GONE
                                send.isEnabled = true
                                status.text = "✓ ${model.label} model hazır · $backend"
                                Toast.makeText(
                                    this@MainActivity,
                                    "${model.label} model hazır. Artık internet gerekmez.",
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
                            .remove(KEY_DOWNLOAD_MODEL)
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

    private fun chooseModel() {'''

s, n = region.subn(replacement, s, count=1)
if n != 1:
    raise SystemExit("0.7.0 automatic model region not found")

# Normal menu entry now opens the two-model selector.
s = s.replace('menu.add("Önerilen modeli indir")', 'menu.add("AI modeli indir / değiştir")')
s = s.replace('"Önerilen modeli indir" -> confirmDefaultModelDownload()', '"AI modeli indir / değiştir" -> showModelChooser()')

# Startup state: resume the exact model download, otherwise offer both choices.
restore_pattern = re.compile(
    r'''    // Startup safety: display model/download state only\. LiteRT itself is not\n    // initialized here, so a downloaded model can never break Activity startup\.\n    private fun restoreModelLabelOnly\(\) \{.*?\n    \}\n\n    private fun getOrCreateEngine''',
    re.S,
)
restore_replacement = '''    // Startup safety: display model/download state only. LiteRT itself is not
    // initialized here, so a downloaded model can never break Activity startup.
    private fun restoreModelLabelOnly() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val path = prefs.getString(KEY_MODEL_PATH, null)
        val name = prefs.getString(KEY_MODEL_NAME, "Yerel model") ?: "Yerel model"
        if (!path.isNullOrBlank() && File(path).exists()) {
            status.text = "✓ $name · cihazda hazır"
            return
        }

        val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId >= 0L) {
            val model = modelByKey(prefs.getString(KEY_DOWNLOAD_MODEL, null))
            status.text = "${model.label} model indiriliyor…"
            monitorModelDownload(downloadId, model, loadWhenDone = false)
        } else {
            status.text = "Model seç · Hızlı 1.7B / Güçlü 4B"
        }
    }

    private fun getOrCreateEngine'''
s, n = restore_pattern.subn(restore_replacement, s, count=1)
if n != 1:
    raise SystemExit("0.7.0 restore block not found")

# Starting a chat before model setup should open the model chooser.
s = s.replace('confirmDefaultModelDownload()\n            return', 'showModelChooser()\n            return')

# Removing the selected model clears both automatic model files and any active download.
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
            runCatching { autoModelFile(fastModel).delete() }
            runCatching { autoModelFile(powerfulModel).delete() }
            runCatching { File(filesDir, "models/model.litertlm").delete() }

            prefs.edit()
                .remove(KEY_MODEL_PATH)
                .remove(KEY_MODEL_NAME)
                .remove(KEY_DOWNLOAD_ID)
                .remove(KEY_DOWNLOAD_MODEL)
                .apply()

            progress.visibility = View.GONE
            status.text = "Model seç · Hızlı 1.7B / Güçlü 4B"
            newChat()
        }
    }

    private fun showConversation'''
s, n = remove_pattern.subn(remove_replacement, s, count=1)
if n != 1:
    raise SystemExit("0.7.0 removeModel block not found")

s = s.replace("Prototype 0.7.0", "Prototype 0.7.1")
s = s.replace(
    '"Model tek dokunuşla otomatik indirilip kurulur.\\n" +',
    '"Hızlı 1.7B veya Güçlü 4B model tek dokunuşla indirilip kurulur.\\n" +'
)

main.write_text(s)
print("0.7.1 two-model selector patch applied")
