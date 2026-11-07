package com.example.interkom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val FlankBg = Color(0xFF10151F)
private val FlankPanel = Color(0xFF202B3B)
private val FlankBlue = Color(0xFF42C6F5)
private val FlankText = Color(0xFFF9FAFC)
private val FlankMuted = Color(0xFFB5C1D2)

private enum class Page { LOADING, WELCOME, PROFILE, HOME }

class MainActivity : ComponentActivity() {
    private var page by mutableStateOf(Page.LOADING)
    private var profile by mutableStateOf<FlankProfile?>(null)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var pendingIntent: Intent? = null

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val intent = pendingIntent
        pendingIntent = null
        if (intent != null && hasMicPermission()) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            message = "Konuşmak için mikrofon izni gerekli."
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backend = FlankSupabase.client
        FlankSupabase.handleRedirect(intent)
        lifecycleScope.launch {
            backend.auth.sessionStatus.collectLatest { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        page = Page.LOADING
                        try {
                            profile = FlankSupabase.loadProfile()
                            page = if (profile == null) Page.PROFILE else Page.HOME
                            message = ""
                        } catch (e: Exception) {
                            page = Page.LOADING
                            message = "Profil yüklenemedi: " + (e.message ?: "bağlantıyı kontrol et")
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        profile = null
                        page = Page.WELCOME
                    }
                    else -> Unit
                }
            }
        }

        setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().background(FlankBg)) {
                    when (page) {
                        Page.LOADING -> LoadingScreen(message, { reloadSessionProfile() })
                        Page.WELCOME -> WelcomeScreen(busy, message, ::signInGoogle, ::signInGuest)
                        Page.PROFILE -> ProfileScreen(profile, busy, message, ::saveProfile, ::returnHome)
                        Page.HOME -> HomeScreen(
                            profile = profile,
                            message = message,
                            onEdit = { page = Page.PROFILE; message = "" },
                            onJoin = ::joinRoom,
                            onMute = { act(InterkomService.MUTE) },
                            onLeave = { act(InterkomService.LEAVE) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        FlankSupabase.handleRedirect(intent)
    }

    private fun reloadSessionProfile() {
        lifecycleScope.launch {
            busy = true
            try {
                profile = FlankSupabase.loadProfile()
                page = if (FlankSupabase.client.auth.currentUserOrNull() == null) Page.WELCOME
                    else if (profile == null) Page.PROFILE else Page.HOME
                message = ""
            } catch (e: Exception) {
                message = e.message ?: "Sunucuya ulaşılamadı."
            } finally {
                busy = false
            }
        }
    }

    private fun signInGuest() {
        if (busy) return
        busy = true
        message = ""
        lifecycleScope.launch {
            try {
                FlankSupabase.signInGuest()
            } catch (e: Exception) {
                message = "Misafir girişi başarısız: " + (e.message ?: "Bilinmeyen hata")
            } finally {
                busy = false
            }
        }
    }

    private fun signInGoogle() {
        if (busy) return
        busy = true
        message = ""
        lifecycleScope.launch {
            try {
                FlankSupabase.signInGoogle()
                // Browser opens; the login completes after a redirect to this activity.
            } catch (e: Exception) {
                message = "Google giriş hatası: " + (e.message ?: "Bağlantı sorunu")
            } finally {
                busy = false
            }
        }
    }

    private fun saveProfile(nickname: String, avatar: Uri?) {
        if (busy) return
        busy = true
        message = ""
        lifecycleScope.launch {
            try {
                profile = FlankSupabase.saveProfile(this@MainActivity, nickname, avatar, profile)
                page = Page.HOME
                message = ""
            } catch (e: Exception) {
                message = "Profil kaydedilemedi: " + (e.message ?: "Tekrar dene")
            } finally {
                busy = false
            }
        }
    }

    private fun returnHome() {
        if (profile != null) {
            page = Page.HOME
            message = ""
        }
    }

    private fun joinRoom(create: Boolean, code: String) {
        val nickname = profile?.nickname ?: return
        if (!create && !Regex("[0-9]{6}").matches(code)) {
            message = "6 haneli oda kodu gir."
            return
        }
        val service = Intent(this, InterkomService::class.java).apply {
            action = InterkomService.JOIN
            putExtra("nickname", nickname)
            putExtra("code", code)
            putExtra("create", create)
        }
        val needs = mutableListOf<String>()
        if (!hasMicPermission()) needs.add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
}

@Composable
private fun LoadingScreen(message: String, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = FlankBlue)
        Spacer(Modifier.height(22.dp))
        Text(if (message.isBlank()) "Flank Interkom hazırlanıyor…" else message,
            color = FlankText, textAlign = TextAlign.Center)
        if (message.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = retry) { Text("Tekrar dene") }
        }
    }
}

@Composable
private fun WelcomeScreen(
    busy: Boolean, message: String, google: () -> Unit, guest: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1.3f))
        Box(
            Modifier.size(94.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF183047)),
            contentAlignment = Alignment.Center
        ) { Text("◉", color = FlankBlue, style = MaterialTheme.typography.displayLarge) }
        Spacer(Modifier.height(20.dp))
        Text("FLANK", style = MaterialTheme.typography.headlineLarge,
            color = FlankText, fontWeight = FontWeight.Black)
        Text("INTERKOM", color = FlankBlue, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text("Arkadaşlarınla anında bağlan, birlikte konuş.",
            color = FlankMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1.6f))
        Button(
            onClick = google, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FlankBg),
            shape = RoundedCornerShape(15.dp)
        ) { Text("G   Google ile devam et", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = guest, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp),
            border = BorderStroke(1.dp, Color(0xFF526278)), shape = RoundedCornerShape(15.dp)
        ) { Text("Misafir olarak devam et", color = FlankText) }
        if (busy) { Spacer(Modifier.height(10.dp)); CircularProgressIndicator(color = FlankBlue) }
        if (message.isNotBlank()) ErrorText(message)
        Spacer(Modifier.height(18.dp))
        Text("Devam ederek kullanım koşullarını kabul edersin.",
            style = MaterialTheme.typography.bodySmall, color = FlankMuted,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfileAvatar(url: Any?, large: Boolean = false) {
    val d = if (large) 102.dp else 64.dp
    Box(Modifier.size(d).clip(CircleShape).background(FlankPanel), contentAlignment = Alignment.Center) {
        if (url == null) Text("◉", color = FlankBlue, style = MaterialTheme.typography.headlineLarge)
        else AsyncImage(model = url, contentDescription = "Profil fotoğrafı",
            modifier = Modifier.fillMaxSize().clip(CircleShape))
    }
}

@Composable
private fun ProfileScreen(
    existing: FlankProfile?, busy: Boolean, message: String,
    save: (String, Uri?) -> Unit, back: () -> Unit
) {
    var nick by remember(existing?.id, existing?.nickname) {
        mutableStateOf(existing?.nickname.orEmpty())
    }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photo = uri
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        Text(if (existing == null) "Profilini oluştur" else "Profili düzenle",
            style = MaterialTheme.typography.headlineSmall, color = FlankText, fontWeight = FontWeight.Bold)
        Text("Diğer kullanıcılar seni bu isimle görecek.",
            color = FlankMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        ProfileAvatar(photo ?: FlankSupabase.avatarUrl(existing?.avatarPath), true)
        TextButton(onClick = { picker.launch("image/*") }) {
            Text("Fotoğraf seç", color = FlankBlue)
        }
        OutlinedTextField(
            value = nick, onValueChange = { nick = it.take(24) },
            label = { Text("Adın veya takma adın") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedTextColor = FlankText, unfocusedTextColor = FlankText,
                focusedContainerColor = FlankPanel, unfocusedContainerColor = FlankPanel,
                focusedLabelColor = FlankBlue, unfocusedLabelColor = FlankMuted,
                focusedIndicatorColor = FlankBlue, unfocusedIndicatorColor = FlankMuted
            )
        )
        Text("Nick 2–24 karakter. Profil fotoğrafı isteğe bağlı.",
            color = FlankMuted, style = MaterialTheme.typography.bodySmall)
        if (message.isNotBlank()) ErrorText(message)
        Button(
            onClick = { save(nick, photo) }, enabled = !busy && nick.trim().length in 2..24,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FlankBlue, contentColor = FlankBg),
            shape = RoundedCornerShape(15.dp)
        ) { Text(if (busy) "Kaydediliyor…" else "Devam et", fontWeight = FontWeight.Bold) }
        if (existing != null) TextButton(onClick = back) { Text("Vazgeç", color = FlankMuted) }
    }
}

@Composable
private fun HomeScreen(
    profile: FlankProfile?, message: String,
    onEdit: () -> Unit, onJoin: (Boolean, String) -> Unit,
    onMute: () -> Unit, onLeave: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    val live by SessionState.live.collectAsState()
    val code by SessionState.code.collectAsState()
    val count by SessionState.count.collectAsState()
    val mic by SessionState.mic.collectAsState()
    val status by SessionState.status.collectAsState()
    val error by SessionState.error.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text("FLANK  INTERKOM", color = FlankBlue, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FlankPanel).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProfileAvatar(FlankSupabase.avatarUrl(profile?.avatarPath))
            Column(Modifier.weight(1f)) {
                Text(profile?.nickname.orEmpty(), color = FlankText, fontWeight = FontWeight.SemiBold)
                Text("Profilin hazır", color = FlankMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onEdit, enabled = !live) { Text("Düzenle", color = FlankBlue) }
        }
        if (live) {
            Text("Canlı konuşma", color = FlankText, style = MaterialTheme.typography.headlineSmall)
            Text("Oda kodu: $code", color = FlankBlue, style = MaterialTheme.typography.titleLarge)
            Text("Katılımcılar: $count / 16", color = FlankText)
            Text("Durum: $status", color = FlankMuted)
            Button(
                onClick = onMute, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlankBlue, contentColor = FlankBg)
            ) { Text(if (mic) "Mikrofonu kapat" else "Mikrofonu aç") }
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text("Odadan ayrıl", color = FlankText)
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text("Arkadaşlarınla bağlantıya geç.", color = FlankText,
                style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = { onJoin(true, "") },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlankBlue, contentColor = FlankBg),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Yeni oda oluştur", fontWeight = FontWeight.Bold) }
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it.filter(Char::isDigit).take(6) },
                label = { Text("6 haneli oda kodu") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = FlankText, unfocusedTextColor = FlankText,
                    focusedContainerColor = FlankPanel, unfocusedContainerColor = FlankPanel,
                    focusedLabelColor = FlankBlue, unfocusedLabelColor = FlankMuted
                )
            )
            OutlinedButton(onClick = { onJoin(false, codeInput) }, modifier = Modifier.fillMaxWidth(),
                enabled = codeInput.length == 6) {
                Text("Odaya katıl", color = FlankText)
            }
            Text("Kulaklık gerekmez. Telefon mikrofonu ve hoparlörüyle konuşabilirsin.",
                color = FlankMuted, style = MaterialTheme.typography.bodySmall)
        }
        if (message.isNotBlank()) ErrorText(message)
        if (error.isNotBlank()) ErrorText(error)
        Text("Test sürümü • Sürüş sırasında ekrana dokunma.",
            color = FlankMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = Color(0xFFFF938E), style = MaterialTheme.typography.bodySmall)
}
