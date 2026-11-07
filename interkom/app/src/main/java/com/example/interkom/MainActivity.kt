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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
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

private val FlankBg = Color(0xFF0A0D12)
private val FlankPanel = Color(0xFF1A2025)
private val FlankOrange = Color(0xFFFF841C)
private val FlankOrangeBright = Color(0xFFFFA13E)
private val FlankTeal = Color(0xFF28D8D0)
private val FlankText = Color(0xFFF7F8FA)
private val FlankMuted = Color(0xFFA9B1BA)

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
        Modifier.fillMaxSize().background(FlankBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = FlankOrange)
        Spacer(Modifier.height(20.dp))
        Text(
            if (message.isBlank()) "Flank Interkom hazırlanıyor…" else message,
            color = FlankText, textAlign = TextAlign.Center
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = retry, border = BorderStroke(1.dp, FlankOrange)) {
                Text("Tekrar dene", color = FlankOrange)
            }
        }
    }
}

@Composable
private fun RadioWaveMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(37.dp)) {
        val step = size.width / 7f
        val center = size.height / 2f
        val lengths = listOf(.33f, .67f, 1f, .62f, .42f)
        lengths.forEachIndexed { index, factor ->
            val x = step * (index + 1.3f)
            val half = size.height * factor * .43f
            drawLine(
                color = if (index == 2) FlankOrange else FlankTeal,
                start = Offset(x, center - half),
                end = Offset(x, center + half),
                strokeWidth = step * .75f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun FlankHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RadioWaveMark()
            Box(Modifier.width(2.dp).height(42.dp).background(FlankOrange))
            Column {
                Text(
                    "FLANK",
                    color = FlankText,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 27.sp,
                    letterSpacing = 1.sp,
                    lineHeight = 29.sp
                )
                Text(
                    "INTERKOM",
                    color = FlankOrange,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 21.sp,
                    letterSpacing = 1.2.sp,
                    lineHeight = 23.sp
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(
                Brush.horizontalGradient(
                    listOf(FlankOrange, FlankOrange.copy(alpha = .55f), Color.Transparent)
                )
            )
        )
    }
}

/** Background decoration is paint-only: no network image, no large file, no interaction. */
@Composable
private fun OrangeRouteDecoration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(-w * .05f, h * .90f)
            cubicTo(w * .16f, h * .62f, w * .36f, h * .99f, w * .54f, h * .66f)
            cubicTo(w * .68f, h * .37f, w * .80f, h * .78f, w * 1.07f, h * .34f)
        }
        drawPath(
            path, color = FlankOrange.copy(alpha = .27f),
            style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path, color = FlankOrange.copy(alpha = .72f),
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = FlankOrange.copy(alpha = .22f),
            radius = 70.dp.toPx(),
            center = Offset(w * .85f, h * .94f)
        )
    }
}

@Composable
private fun FlankBackdrop(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xFF101419), FlankBg, Color(0xFF151A21))
            )
        )
    ) {
        OrangeRouteDecoration(
            Modifier.fillMaxWidth().height(160.dp).align(Alignment.BottomCenter)
        )
        content()
    }
}

@Composable
private fun SolidOrangeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: String = "✚",
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(23.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .alpha(if (enabled) 1f else .52f)
            .shadow(5.dp, shape)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(FlankOrangeBright, FlankOrange, Color(0xFFFF7205))
                )
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(leading, color = Color(0xFF19140F), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(13.dp))
        Box(Modifier.width(1.dp).height(29.dp).background(Color(0xFF7D3E0C)))
        Spacer(Modifier.width(13.dp))
        Text(
            text,
            color = Color(0xFF18110A),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TealOutlineButton(
    text: String, onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(23.dp)
    Row(
        modifier.fillMaxWidth()
            .height(62.dp)
            .alpha(if (enabled) 1f else .47f)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xFF152A2A), Color(0xFF122124))))
            .border(BorderStroke(1.5.dp, FlankTeal), shape)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("➜", color = FlankTeal, fontWeight = FontWeight.Black, fontSize = 23.sp)
        Spacer(Modifier.width(14.dp))
        Text(text, color = FlankText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
private fun WelcomeScreen(
    busy: Boolean, message: String, google: () -> Unit, guest: () -> Unit
) {
    FlankBackdrop {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 25.dp, vertical = 25.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(.7f))
            Box(
                Modifier.size(108.dp).clip(RoundedCornerShape(29.dp))
                    .background(Color(0xFF1D2328))
                    .border(1.dp, FlankOrange.copy(alpha = .85f), RoundedCornerShape(29.dp)),
                contentAlignment = Alignment.Center
            ) {
                RadioWaveMark(Modifier.size(66.dp))
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "FLANK",
                color = FlankText,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp
            )
            Text(
                "INTERKOM",
                color = FlankOrange,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Black,
                fontSize = 24.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Arkadaşlarınla anında bağlan,\nbirlikte konuş.",
                color = FlankMuted,
                textAlign = TextAlign.Center,
                lineHeight = 23.sp
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = google,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(59.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlankText,
                    contentColor = Color(0xFF181A1E)
                ),
                shape = RoundedCornerShape(19.dp)
            ) {
                Text("G", fontWeight = FontWeight.Bold, color = Color(0xFF4285F4), fontSize = 21.sp)
                Spacer(Modifier.width(15.dp))
                Text("Google ile devam et", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(13.dp))
            OutlinedButton(
                onClick = guest, enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(59.dp),
                border = BorderStroke(1.2.dp, FlankOrange),
                shape = RoundedCornerShape(19.dp)
            ) {
                Text("Misafir olarak devam et", color = FlankText, fontWeight = FontWeight.Bold)
            }
            if (busy) {
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator(color = FlankOrange)
            }
            if (message.isNotBlank()) ErrorText(message)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileAvatar(url: Any?, large: Boolean = false) {
    val diameter = if (large) 116.dp else 67.dp
    // Circle + ContentScale.Crop: square, landscape and portrait photos are always circular.
    Box(
        Modifier.size(diameter)
            .border(2.dp, FlankOrange, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Color(0xFF303840)),
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            Text("●", color = FlankOrange, fontSize = 39.sp)
        } else {
            AsyncImage(
                model = url,
                contentDescription = "Profil fotoğrafı",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
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
        if (uri != null) photo = uri
    }

    FlankBackdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FlankHeader()
            Spacer(Modifier.height(19.dp))
            Text(
                if (existing == null) "PROFİLİNİ OLUŞTUR" else "PROFİLİNİ DÜZENLE",
                color = FlankText,
                fontWeight = FontWeight.ExtraBold,
                fontStyle = FontStyle.Italic,
                fontSize = 23.sp
            )
            Spacer(Modifier.height(18.dp))
            ProfileAvatar(photo ?: FlankSupabase.avatarUrl(existing?.avatarPath), true)
            TextButton(onClick = { picker.launch("image/*") }) {
                Text(
                    if (photo == null && existing?.avatarPath == null) "Fotoğraf ekle" else "Fotoğrafı değiştir",
                    color = FlankOrange, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(15.dp))
            OutlinedTextField(
                value = nick, onValueChange = { nick = it.take(24) },
                label = { Text("Adın veya takma adın") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(17.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = FlankText,
                    unfocusedTextColor = FlankText,
                    focusedContainerColor = FlankPanel,
                    unfocusedContainerColor = FlankPanel,
                    focusedLabelColor = FlankOrange,
                    unfocusedLabelColor = FlankMuted,
                    focusedIndicatorColor = FlankOrange,
                    unfocusedIndicatorColor = FlankMuted
                )
            )
            if (message.isNotBlank()) ErrorText(message)
            Spacer(Modifier.height(18.dp))
            SolidOrangeButton(
                text = if (busy) "Kaydediliyor…" else "DEVAM ET",
                onClick = { save(nick, photo) },
                enabled = !busy && nick.trim().length in 2..24,
                leading = "➜"
            )
            if (existing != null) {
                TextButton(onClick = back) { Text("Vazgeç", color = FlankMuted) }
            }
        }
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

    FlankBackdrop {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FlankHeader()

            val cardShape = RoundedCornerShape(24.dp)
            Row(
                Modifier.fillMaxWidth().clip(cardShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF25282C), Color(0xFF161B20))
                        )
                    )
                    .border(1.dp, FlankOrange.copy(alpha = .5f), cardShape)
                    .padding(horizontal = 15.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileAvatar(FlankSupabase.avatarUrl(profile?.avatarPath))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.nickname.orEmpty(),
                        color = FlankText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("Profilin hazır", color = FlankMuted, fontSize = 13.sp)
                }
                TextButton(
                    onClick = onEdit, enabled = !live,
                    modifier = Modifier.border(
                        1.dp, FlankOrange.copy(alpha = .7f), RoundedCornerShape(16.dp)
                    )
                ) { Text("✎  Düzenle", color = FlankOrange, fontSize = 12.sp) }
            }

            Spacer(Modifier.height(7.dp))

            if (live) {
                Text(
                    "CANLI\nKONUŞMA",
                    color = FlankOrange,
                    fontSize = 29.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
                        .background(FlankPanel).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("ODA KODU", color = FlankMuted, fontSize = 12.sp)
                    Text(code, color = FlankText, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    Text("Bağlı kişi: $count / 16", color = FlankTeal)
                    Text("Durum: $status", color = FlankMuted, fontSize = 13.sp)
                }
                SolidOrangeButton(
                    text = if (mic) "MİKROFONU KAPAT" else "MİKROFONU AÇ",
                    onClick = onMute,
                    leading = if (mic) "◉" else "⊘"
                )
                TealOutlineButton("ODADAN AYRIL", onClick = onLeave)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("»", color = FlankOrange, fontSize = 39.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "ARKADAŞLARINLA",
                            color = FlankText,
                            fontSize = 25.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                        Text(
                            "BAĞLANTIYA GEÇ.",
                            color = FlankOrange,
                            fontSize = 26.sp,
                            lineHeight = 30.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
                Spacer(Modifier.height(7.dp))
                SolidOrangeButton(
                    text = "YENİ ODA OLUŞTUR",
                    onClick = { onJoin(true, "") }
                )
                Spacer(Modifier.height(2.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.filter(Char::isDigit).take(6) },
                    placeholder = { Text("6 haneli oda kodu", color = FlankMuted) },
                    prefix = { Text("#", color = FlankTeal, fontSize = 23.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().height(66.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = FlankText,
                        unfocusedTextColor = FlankText,
                        focusedContainerColor = FlankPanel,
                        unfocusedContainerColor = FlankPanel,
                        focusedIndicatorColor = FlankTeal,
                        unfocusedIndicatorColor = FlankTeal.copy(alpha = .65f),
                        cursorColor = FlankTeal
                    )
                )
                TealOutlineButton(
                    text = "ODAYA KATIL",
                    onClick = { onJoin(false, codeInput) },
                    enabled = codeInput.length == 6
                )
            }

            if (message.isNotBlank()) ErrorText(message)
            if (error.isNotBlank()) ErrorText(error)
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        color = Color(0xFFFF9690),
        style = MaterialTheme.typography.bodySmall
    )
}
