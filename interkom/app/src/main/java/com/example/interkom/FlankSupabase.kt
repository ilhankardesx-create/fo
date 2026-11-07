package com.example.interkom

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.UUID

@Serializable
data class FlankProfile(
    val id: String,
    val nickname: String,
    @SerialName("avatar_path") val avatarPath: String? = null
)

/** Only public client configuration belongs in the APK. Never add a service or OAuth secret. */
internal object FlankSupabase {
    private const val URL = "https://uvchtuqqwuewncuhwxjb.supabase.co"
    private const val PUBLISHABLE_KEY = "sb_publishable_FrbPohrxcXz0B0_cKTcVyg_MejnoQoF"

    val client: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = URL, supabaseKey = PUBLISHABLE_KEY) {
            install(Auth) {
                scheme = "flankinterkom"
                host = "auth-callback"
                flowType = FlowType.PKCE
            }
            install(Postgrest)
            install(Storage)
        }
    }

    fun handleRedirect(intent: Intent) {
        client.handleDeeplinks(intent)
    }

    suspend fun signInGuest() {
        client.auth.signInAnonymously()
    }

    suspend fun signInGoogle() {
        // Opens the browser and returns via flankinterkom://auth-callback (PKCE).
        client.auth.signInWith(Google)
    }

    suspend fun loadProfile(): FlankProfile? {
        val id = client.auth.currentUserOrNull()?.id ?: return null
        return client.from("flank_profiles").select {
            filter { eq("id", id) }
        }.decodeSingleOrNull<FlankProfile>()
    }

    suspend fun saveProfile(context: Context, nickname: String, newAvatar: Uri?, previous: FlankProfile?): FlankProfile {
        val id = client.auth.currentUserOrNull()?.id
            ?: error("Oturum bulunamadı. Yeniden giriş yap.")
        val name = nickname.trim()
        require(name.length in 2..24) { "Nick 2–24 karakter olmalı." }

        var avatarPath = previous?.avatarPath
        if (newAvatar != null) {
            val data = imageJpeg(context, newAvatar)
            val fileName = UUID.randomUUID().toString().replace("-", "") + ".jpg"
            avatarPath = "$id/$fileName"
            client.storage.from("flank-avatars").upload(avatarPath, data) {
                upsert = false
            }
        }

        val profile = FlankProfile(id = id, nickname = name, avatarPath = avatarPath)
        client.from("flank_profiles").upsert(profile)
        return profile
    }

    fun avatarUrl(path: String?): String? =
        path?.let { client.storage.from("flank-avatars").publicUrl(it) }

    private fun imageJpeg(context: Context, uri: Uri): ByteArray {
        val original = context.contentResolver.openInputStream(uri).use { stream ->
            requireNotNull(stream) { "Fotoğraf okunamadı." }
            BitmapFactory.decodeStream(stream)
        } ?: error("Fotoğraf açılamadı.")
        val w = original.width
        val h = original.height
        val scale = minOf(1f, 512f / maxOf(w, h))
        val bmp = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
        } else original
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (bmp !== original) bmp.recycle()
        original.recycle()
        return out.toByteArray().also {
            require(it.size <= 2 * 1024 * 1024) { "Fotoğraf 2 MB altında olmalı." }
        }
    }
}
