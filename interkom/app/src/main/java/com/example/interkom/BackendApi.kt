package com.example.interkom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The mobile APK only receives short-lived session tokens; never store the server API secret here. */
internal object BackendApi {
    data class Ticket(val serverUrl: String, val token: String, val code: String)

    suspend fun getTicket(baseUrl: String, create: Boolean, nickname: String, code: String): Ticket =
        withContext(Dispatchers.IO) {
            val base = baseUrl.trim().trimEnd('/')
            require(base.startsWith("https://")) { "Oda sunucusu HTTPS olmalı" }
            val endpoint = if (create) "$base/rooms" else "$base/rooms/join"
            val body = JSONObject().put("nickname", nickname.trim())
            if (!create) body.put("code", code.trim())
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val response = (if (status < 400) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val detail = runCatching { JSONObject(response).optString("detail") }.getOrDefault("")
                    throw IllegalStateException(detail.ifBlank { "Sunucu hatası: $status" })
                }
                val json = JSONObject(response)
                Ticket(json.getString("server_url"), json.getString("token"), json.getString("code"))
            } finally { connection.disconnect() }
        }
}
