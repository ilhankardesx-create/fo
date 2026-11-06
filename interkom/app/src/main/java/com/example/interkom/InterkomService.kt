package com.example.interkom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class InterkomService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var room: Room? = null
    private var sessionJob: Job? = null
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Interkom görüşmesi", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            JOIN -> {
                val notification = notification("Bağlanıyor…")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(ID, notification)
                }
                sessionJob?.cancel()
                room?.disconnect()
                room = null
                val backend = intent.getStringExtra("backend").orEmpty()
                val nickname = intent.getStringExtra("nickname").orEmpty()
                val code = intent.getStringExtra("code").orEmpty()
                val create = intent.getBooleanExtra("create", false)
                sessionJob = scope.launch {
                    SessionState.error.value = ""
                    SessionState.status.value = "Bağlanıyor…"
                    try {
                        val ticket = BackendApi.getTicket(backend, create, nickname, code)
                        val joined = LiveKit.create(applicationContext)
                        room = joined
                        joined.connect(ticket.serverUrl, ticket.token)
                        if (!joined.localParticipant.setMicrophoneEnabled(true)) {
                            throw IllegalStateException("Mikrofon açılamadı")
                        }
                        SessionState.code.value = ticket.code
                        SessionState.mic.value = true
                        SessionState.live.value = true
                        SessionState.status.value = "Bağlı"
                        getSystemService(NotificationManager::class.java)
                            .notify(ID, notification("Oda: " + ticket.code))
                        while (isActive) {
                            SessionState.count.value = joined.remoteParticipants.size + 1
                            delay(1000)
                        }
                    } catch (t: Exception) {
                        SessionState.error.value = t.message ?: "Bağlantı kurulamadı"
                        SessionState.status.value = "Bağlantı hatası"
                        SessionState.live.value = false
                        SessionState.mic.value = false
                        room?.disconnect()
                        room = null
                        stopSelf()
                    }
                }
            }
            MUTE -> scope.launch {
                room?.let {
                    val next = !SessionState.mic.value
                    if (it.localParticipant.setMicrophoneEnabled(next)) SessionState.mic.value = next
                }
            }
            LEAVE -> {
                sessionJob?.cancel()
                room?.disconnect()
                room = null
                SessionState.reset()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val leave = PendingIntent.getService(this, 1, Intent(this, InterkomService::class.java).setAction(LEAVE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Interkom")
            .setContentText(message)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ayrıl", leave)
            .build()
    }

    override fun onDestroy() {
        sessionJob?.cancel()
        room?.disconnect()
        room = null
        scope.cancel()
        SessionState.reset()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "interkom_live"
        private const val ID = 201
        const val JOIN = "com.example.interkom.JOIN"
        const val MUTE = "com.example.interkom.MUTE"
        const val LEAVE = "com.example.interkom.LEAVE"
    }
}
