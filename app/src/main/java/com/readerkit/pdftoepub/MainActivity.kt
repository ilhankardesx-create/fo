package com.readerkit.pdftoepub

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {

    companion object {
        private const val REQ_PICK_PDF = 1001
        private const val REQ_SAVE_EPUB = 1002
    }

    private lateinit var fileNameText: TextView
    private lateinit var titleInput: EditText
    private lateinit var coverCheck: CheckBox
    private lateinit var selectButton: Button
    private lateinit var convertButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var resultRow: LinearLayout
    private lateinit var openButton: Button
    private lateinit var shareButton: Button

    private var selectedPdfUri: Uri? = null
    private var selectedFileName = ""
    private var lastOutputUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContentView(buildUi())

        selectButton.setOnClickListener { pickPdf() }
        convertButton.setOnClickListener { chooseOutputLocation() }
        openButton.setOnClickListener { lastOutputUri?.let(::openEpub) }
        shareButton.setOnClickListener { lastOutputUri?.let(::shareEpub) }
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 247, 249))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(30), dp(22), dp(28))
        }
        scroll.addView(root, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(TextView(this).apply {
            text = "PDF to EPUB"
            textSize = 30f
            setTextColor(Color.rgb(23, 25, 28))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "Metin tabanlı PDF'leri internetsiz EPUB'a dönüştür."
            textSize = 15f
            setTextColor(Color.rgb(100, 106, 115))
        }, lpTop(LinearLayout.LayoutParams.WRAP_CONTENT, 6))

        fileNameText = TextView(this).apply {
            text = "PDF seçilmedi"
            textSize = 16f
            setTextColor(Color.rgb(23, 25, 28))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(fileNameText, lpTop(LinearLayout.LayoutParams.MATCH_PARENT, 28))

        selectButton = Button(this).apply {
            text = "PDF SEÇ"
            isAllCaps = false
        }
        root.addView(selectButton, lpTop(LinearLayout.LayoutParams.MATCH_PARENT, 12, 54))

        root.addView(TextView(this).apply {
            text = "Kitap adı"
            textSize = 14f
            setTextColor(Color.rgb(23, 25, 28))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, lpTop(LinearLayout.LayoutParams.WRAP_CONTENT, 22))

        titleInput = EditText(this).apply {
            hint = "Kitap adı"
            setSingleLine(true)
            textSize = 16f
        }
        root.addView(titleInput, lpTop(LinearLayout.LayoutParams.MATCH_PARENT, 7, 54))

        coverCheck = CheckBox(this).apply {
            text = "İlk sayfayı kapak olarak kullan"
            isChecked = true
        }
        root.addView(coverCheck, lpTop(LinearLayout.LayoutParams.WRAP_CONTENT, 10))

        convertButton = Button(this).apply {
            text = "EPUB'A DÖNÜŞTÜR"
            isAllCaps = false
            isEnabled = false
        }
        root.addView(convertButton, lpTop(LinearLayout.LayoutParams.MATCH_PARENT, 18, 58))

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }
        root.addView(progressBar, lpTop(LinearLayout.LayoutParams.MATCH_PARENT, 20, 10))

        statusText = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 106, 115))
        }
        root.addView(statusText, lpTop(LinearLayout.LayoutParams.MATCH_PARENT, 8))

        resultRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
        }
        openButton = Button(this).apply {
            text = "EPUB'U AÇ"
            isAllCaps = false
        }
        shareButton = Button(this).apply {
            text = "PAYLAŞ"
            isAllCaps = false
        }
        resultRow.addView(openButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        resultRow.addView(shareButton, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            marginStart = dp(8)
        })
        root.addView(resultRow, lpTop(LinearLayout.LayoutParams.MATCH_PARENT, 10))

        root.addView(TextView(this).apply {
            text = "Dosyalar cihazından dışarı gönderilmez."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(100, 106, 115))
        }, lpTop(LinearLayout.LayoutParams.MATCH_PARENT, 24))

        return scroll
    }

    private fun lpTop(width: Int, top: Int, heightDp: Int? = null): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            width,
            heightDp?.let(::dp) ?: LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun pickPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_PICK_PDF)
    }

    private fun chooseOutputLocation() {
        if (selectedPdfUri == null) return
        val title = titleInput.text.toString().trim().ifBlank {
            selectedFileName.replace(Regex("(?i)\\.pdf$"), "")
        }
        if (title.isBlank()) {
            Toast.makeText(this, "Kitap adı boş olamaz.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/epub+zip"
            putExtra(Intent.EXTRA_TITLE, sanitizeFileName(title) + ".epub")
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivityForResult(intent, REQ_SAVE_EPUB)
    }

    @Deprecated("Kept for a dependency-light first version")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQ_PICK_PDF -> {
                val uri = data?.data ?: return
                selectedPdfUri = uri
                lastOutputUri = null
                resultRow.visibility = View.GONE

                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                }

                selectedFileName = queryDisplayName(uri) ?: "kitap.pdf"
                fileNameText.text = selectedFileName
                titleInput.setText(selectedFileName.replace(Regex("(?i)\\.pdf$"), ""))
                convertButton.isEnabled = true
                statusText.text = "PDF hazır."
            }

            REQ_SAVE_EPUB -> {
                val outputUri = data?.data ?: return
                val inputUri = selectedPdfUri ?: return
                val title = titleInput.text.toString().trim().ifBlank { "Kitap" }
                convert(inputUri, outputUri, title, coverCheck.isChecked)
            }
        }
    }

    private fun convert(inputUri: Uri, outputUri: Uri, title: String, makeCover: Boolean) {
        setBusy(true)
        updateProgress(2, "PDF hazırlanıyor…")

        Thread {
            try {
                val pageTexts = mutableListOf<String>()

                contentResolver.openInputStream(inputUri)?.use { input ->
                    PDDocument.load(input).use { document ->
                        val pageCount = document.numberOfPages
                        if (pageCount <= 0) error("PDF boş görünüyor.")

                        val stripper = PDFTextStripper().apply {
                            sortByPosition = true
                            lineSeparator = "\n"
                            pageEnd = "\n"
                        }

                        for (page in 1..pageCount) {
                            stripper.startPage = page
                            stripper.endPage = page
                            pageTexts += stripper.getText(document)
                            val p = 5 + ((page.toFloat() / pageCount) * 55).toInt()
                            updateProgress(p, "Metin çıkarılıyor… " + page + "/" + pageCount)
                        }
                    }
                } ?: error("PDF açılamadı.")

                updateProgress(65, "Metin temizleniyor…")
                val paragraphs = cleanPages(pageTexts)
                if (paragraphs.sumOf { it.length } < 80) {
                    error("Bu PDF'de yeterli seçilebilir metin yok. Taranmış PDF'ler desteklenmiyor.")
                }

                val cover = if (makeCover) {
                    updateProgress(72, "Kapak hazırlanıyor…")
                    renderFirstPageAsJpeg(inputUri)
                } else {
                    null
                }

                updateProgress(82, "EPUB oluşturuluyor…")
                contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                    writeEpub(output, title, paragraphs, cover)
                } ?: error("EPUB dosyası oluşturulamadı.")

                lastOutputUri = outputUri
                updateProgress(100, "Tamamlandı ✓")
                runOnUiThread {
                    setBusy(false)
                    resultRow.visibility = View.VISIBLE
                    statusText.setTextColor(Color.rgb(31, 122, 77))
                    Toast.makeText(this, "EPUB başarıyla oluşturuldu.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setBusy(false)
                    resultRow.visibility = View.GONE
                    progressBar.progress = 0
                    statusText.setTextColor(Color.rgb(100, 106, 115))
                    statusText.text = e.message ?: "Dönüştürme sırasında hata oluştu."
                    Toast.makeText(this, statusText.text, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun cleanPages(pages: List<String>): List<String> {
        val paragraphs = mutableListOf<String>()

        for (page in pages) {
            val normalized = page
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u00AD'.toString(), "")
                .replace('\u00A0', ' ')
                .replace(Regex("(?<=\\p{L})-\\s*\\n\\s*(?=\\p{Ll})"), "")

            val blocks = normalized
                .split(Regex("\\n\\s*\\n+"))
                .map { block ->
                    block.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString(" ")
                        .replace(Regex("\\s{2,}"), " ")
                        .trim()
                }
                .filter { it.length > 1 }

            if (blocks.isEmpty()) {
                val fallback = normalized.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .replace(Regex("\\s{2,}"), " ")
                    .trim()
                if (fallback.isNotEmpty()) paragraphs += fallback
            } else {
                paragraphs += blocks
            }
        }

        return paragraphs
    }

    private fun renderFirstPageAsJpeg(uri: Uri): ByteArray? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null
                    renderer.openPage(0).use { page ->
                        val targetWidth = 1200
                        val scale = targetWidth.toFloat() / page.width.toFloat()
                        val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ByteArrayOutputStream().use { bytes ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, bytes)
                            bitmap.recycle()
                            bytes.toByteArray()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeEpub(
        outputStream: OutputStream,
        title: String,
        paragraphs: List<String>,
        coverJpeg: ByteArray?
    ) {
        ZipOutputStream(outputStream).use { zip ->
            writeStoredMimeType(zip)
            writeText(zip, "META-INF/container.xml", containerXml())
            writeText(zip, "EPUB/styles.css", styleCss())
            writeText(zip, "EPUB/nav.xhtml", navXhtml(title))
            writeText(zip, "EPUB/book.xhtml", bookXhtml(title, paragraphs))

            if (coverJpeg != null) {
                writeBytes(zip, "EPUB/cover.jpg", coverJpeg)
                writeText(zip, "EPUB/cover.xhtml", coverXhtml(title))
            }

            writeText(zip, "EPUB/package.opf", packageOpf(title, coverJpeg != null))
        }
    }

    private fun writeStoredMimeType(zip: ZipOutputStream) {
        val data = "application/epub+zip".toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32().apply { update(data) }
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            this.crc = crc.value
        }
        zip.putNextEntry(entry)
        zip.write(data)
        zip.closeEntry()
    }

    private fun writeText(zip: ZipOutputStream, path: String, text: String) {
        writeBytes(zip, path, text.toByteArray(StandardCharsets.UTF_8))
    }

    private fun writeBytes(zip: ZipOutputStream, path: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(data)
        zip.closeEntry()
    }

    private fun containerXml(): String = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
""".trimIndent()

    private fun packageOpf(title: String, hasCover: Boolean): String {
        val id = "urn:uuid:" + UUID.randomUUID().toString()
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val modified = formatter.format(Date())
        val safeTitle = xml(title)

        val coverManifest = if (hasCover) {
            """    <item id="cover-image" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>
    <item id="cover-page" href="cover.xhtml" media-type="application/xhtml+xml"/>"""
        } else ""

        val coverSpine = if (hasCover) "    <itemref idref=\"cover-page\" linear=\"no\"/>\n" else ""
        val legacyCover = if (hasCover) "    <meta name=\"cover\" content=\"cover-image\"/>\n" else ""

        return """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="und">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">$id</dc:identifier>
    <dc:title>$safeTitle</dc:title>
    <dc:language>und</dc:language>
    <meta property="dcterms:modified">$modified</meta>
$legacyCover  </metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="book" href="book.xhtml" media-type="application/xhtml+xml"/>
    <item id="css" href="styles.css" media-type="text/css"/>
$coverManifest
  </manifest>
  <spine>
$coverSpine    <itemref idref="book"/>
  </spine>
</package>
""".trimIndent()
    }

    private fun navXhtml(title: String): String = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><meta charset="utf-8"/><title>${xml(title)}</title></head>
<body>
<nav epub:type="toc" id="toc">
<h1>İçindekiler</h1>
<ol><li><a href="book.xhtml">${xml(title)}</a></li></ol>
</nav>
</body>
</html>
""".trimIndent()

    private fun bookXhtml(title: String, paragraphs: List<String>): String {
        val body = paragraphs.joinToString("\n") { "    <p>" + xml(it) + "</p>" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
<meta charset="utf-8"/>
<title>${xml(title)}</title>
<link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
<section epub:type="bodymatter">
<h1>${xml(title)}</h1>
$body
</section>
</body>
</html>
""".trimIndent()
    }

    private fun coverXhtml(title: String): String = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head>
<meta charset="utf-8"/>
<title>Kapak</title>
<style>html,body{margin:0;padding:0;text-align:center;background:#fff}img{max-width:100%;max-height:100vh;object-fit:contain}</style>
</head>
<body epub:type="cover">
<img src="cover.jpg" alt="${xml(title)} kapağı"/>
</body>
</html>
""".trimIndent()

    private fun styleCss(): String = """body {
  margin: 5%;
  line-height: 1.55;
  font-family: serif;
}
h1 {
  text-align: center;
  font-size: 1.5em;
  margin-bottom: 1.4em;
}
p {
  margin: 0 0 0.9em 0;
  text-indent: 1.2em;
  orphans: 2;
  widows: 2;
}
""".trimIndent()

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return null
    }

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(100)
        .ifBlank { "kitap" }

    private fun setBusy(busy: Boolean) {
        runOnUiThread {
            selectButton.isEnabled = !busy
            convertButton.isEnabled = !busy && selectedPdfUri != null
            titleInput.isEnabled = !busy
            coverCheck.isEnabled = !busy
            progressBar.visibility = View.VISIBLE
            if (busy) resultRow.visibility = View.GONE
        }
    }

    private fun updateProgress(progress: Int, message: String) {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = progress.coerceIn(0, 100)
            statusText.setTextColor(Color.rgb(100, 106, 115))
            statusText.text = message
        }
    }

    private fun openEpub(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/epub+zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "EPUB'u aç"))
        } catch (_: Exception) {
            Toast.makeText(this, "EPUB açabilecek uygulama bulunamadı.", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareEpub(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/epub+zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "EPUB'u paylaş"))
    }
}
