package com.example.interkom

import io.livekit.android.token.TokenRequestOptions
import io.livekit.android.token.TokenSource
import java.security.SecureRandom

/**
 * Development-only LiveKit token server. Never release this configuration
 * to production; it creates tokens without user authentication or limits.
 */
internal object BackendApi {
    private const val DEV_TOKEN_SERVER_ID = "interkom-j8u9xp"
    private val random = SecureRandom()

    data class Ticket(val serverUrl: String, val token: String, val code: String)

    fun newRoomCode(): String = (100000 + random.nextInt(900000)).toString()

    suspend fun getTicket(create: Boolean, nickname: String, code: String): Ticket {
        val roomCode = if (create) newRoomCode() else code.trim()
        require(Regex("[0-9]{6}").matches(roomCode)) { "6 haneli oda kodu gerekli" }
        val name = nickname.trim()
        require(name.isNotEmpty()) { "Takma ad gerekli" }

        val source = TokenSource.fromDevelopmentTokenServer(DEV_TOKEN_SERVER_ID)
        val response = source.fetch(
            TokenRequestOptions(
                roomName = "interkom-$roomCode",
                participantName = name,
                participantIdentity = java.util.UUID.randomUUID().toString()
            )
        ).getOrThrow()
        return Ticket(
            serverUrl = response.serverUrl,
            token = response.participantToken,
            code = roomCode
        )
    }
}
