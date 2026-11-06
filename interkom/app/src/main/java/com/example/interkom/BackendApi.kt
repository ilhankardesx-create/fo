package com.example.interkom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Development-only LiveKit token server. No API secret is embedded in the APK.
 * For production replace with authenticated backend enforcing participant limits.
 */
internal object BackendApi {
    private const val DEV_TOKEN_SERVER_ID = "interkom-j8u9xp"
    private const val DEV_TOKEN_ENDPOINT =
        "https://cloud-api.livekit.io/api/sandbox/connection-details"
    private val random = SecureRandom()

    data class Ticket(val serverUrl: String, val token: String, val code: String)

    fun newRoomCode(): String = (100000 + random.nextInt(900000)).toString()

    suspend fun getTicket(create: Boolean, nickname: String, code: String): Ticket =
        withContext(Dispatchers.IO) {
            val roomCode = if (create) newRoomCode() else code.trim()
            require(Regex("[0-9]{6}").matches(roomCode)) { "6 haneli oda kodu gerekli" }
            val name = nickname.trim()
            require(name.isNotEmpty()) { "Takma ad gerekli" }

            val body = JSONObject()
                .put("room_name", "interkom-$roomCode")
                .put("participant_name", name)

            val connection = (URL(DEV_TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Sandbox-ID", DEV_TOKEN_SERVER_ID)
            }
            try {
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val response = (if (status < 400) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val detail = runCatching {
                        val json = JSONObject(response)
                        json.optString("message").ifBlank { json.optString("error") }
                    }.getOrDefault("")
                    throw IllegalStateException(detail.ifBlank { "LiveKit bağlantısı başarısız ($status)" })
                }
                val json = JSONObject(response)
                Ticket(
                    serverUrl = json.getString("serverUrl"),
                    token = json.getString("participantToken"),
                    code = roomCode
                )
            } finally {
                connection.disconnect()
            }
        }
}
