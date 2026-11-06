package com.example.interkom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var pendingIntent: Intent? = null
    private fun permitted(name: String) =
        ContextCompat.checkSelfPermission(this, name) == PackageManager.PERMISSION_GRANTED

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val intent = pendingIntent
        pendingIntent = null
        val mic = permitted(Manifest.permission.RECORD_AUDIO)
        if (intent != null && mic) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            SessionState.error.value = "Mikrofon izni gerekli."
        }
    }

    private fun join(create: Boolean, name: String, code: String) {
        if (name.trim().length !in 1..24) {
            SessionState.error.value = "1–24 karakterlik takma ad yaz."
            return
        }
        if (!create && !Regex("[0-9]{6}").matches(code)) {
            SessionState.error.value = "6 haneli oda kodu yaz."
            return
        }
        SessionState.error.value = ""
        getSharedPreferences("interkom", MODE_PRIVATE).edit()
            .putString("name", name.trim()).apply()

        val service = Intent(this, InterkomService::class.java).apply {
            action = InterkomService.JOIN
            putExtra("nickname", name.trim())
            putExtra("code", code)
            putExtra("create", create)
        }
        val needs = mutableListOf<String>()
        if (!permitted(Manifest.permission.RECORD_AUDIO)) needs.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permitted(Manifest.permission.POST_NOTIFICATIONS)) {
            needs.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needs.isEmpty()) ContextCompat.startForegroundService(this, service)
        else {
            pendingIntent = service
            permissions.launch(needs.toTypedArray())
        }
    }

    private fun act(action: String) =
        startService(Intent(this, InterkomService::class.java).setAction(action))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("interkom", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    InterkomScreen(
                        prefs.getString("name", "").orEmpty(),
                        ::join,
                        { act(InterkomService.MUTE) },
                        { act(InterkomService.LEAVE) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InterkomScreen(
    firstName: String,
    onJoin: (Boolean, String, String) -> Unit,
    onMute: () -> Unit,
    onLeave: () -> Unit
) {
    var name by remember { mutableStateOf(firstName) }
    var codeInput by remember { mutableStateOf("") }
    val live by SessionState.live.collectAsState()
    val code by SessionState.code.collectAsState()
    val status by SessionState.status.collectAsState()
    val count by SessionState.count.collectAsState()
    val mic by SessionState.mic.collectAsState()
    val error by SessionState.error.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Text("Interkom", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("16 kişiye kadar internet üzerinden canlı konuşma.")
        Spacer(Modifier.height(12.dp))
        if (live) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Canlı konuşma", style = MaterialTheme.typography.titleLarge)
                    Text("Oda kodu: " + code, fontWeight = FontWeight.Bold)
                    Text("Katılımcılar: " + count + " / 16")
                    Text("Durum: " + status)
                    Button(onClick = onMute, modifier = Modifier.fillMaxWidth()) {
                        Text(if (mic) "Mikrofonu kapat" else "Mikrofonu aç")
                    }
                    OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                        Text("Odadan ayrıl")
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(24) },
                label = { Text("Takma ad") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onJoin(true, name, "") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Yeni oda oluştur") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.filter(Char::isDigit).take(6) },
                    label = { Text("Oda kodu") },
                    modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedButton(
                    onClick = { onJoin(false, name, codeInput) },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Katıl") }
            }
            Text("Kulaklık gerekmiyor: telefonun mikrofonu ve hoparlörüyle konuşabilirsin. Bluetooth kulaklık isteğe bağlı.")
            if (status != "Bağlı değil") Text("Durum: " + status)
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text(
            "LiveKit test sunucusu kullanılıyor. İlk denemede iki telefonu yan yana tutma; yankı oluşabilir. Sürüş sırasında telefona dokunma.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
