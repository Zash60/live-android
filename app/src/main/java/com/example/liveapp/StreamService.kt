package com.example.liveapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker

class StreamService : Service(), ConnectChecker {

    private var rtmpDisplay: RtmpDisplay? = null

    override fun onCreate() {
        super.onCreate()
        // Cria a notificação obrigatória para o Android não matar o app
        startForegroundServiceNative()
        // Inicializa a biblioteca no contexto do Serviço
        rtmpDisplay = RtmpDisplay(baseContext, true, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == "STOP") {
            stopStream()
            stopSelf()
            return START_NOT_STICKY
        }

        // Se recebeu os dados da Activity, começa a live
        if (intent != null && rtmpDisplay != null) {
            if (!rtmpDisplay!!.isStreaming) {
                val resultCode = intent.getIntExtra("code", -1)
                val resultData = intent.getParcelableExtra<Intent>("data")
                val url = intent.getStringExtra("endpoint")
                val width = intent.getIntExtra("width", 1280)
                val height = intent.getIntExtra("height", 720)
                val bitrate = intent.getIntExtra("bitrate", 2500 * 1024)

                if (resultData != null) {
                    rtmpDisplay?.setIntentResult(resultCode, resultData)
                    if (rtmpDisplay?.prepareAudio() == true && 
                        rtmpDisplay?.prepareVideo(width, height, 30, bitrate, 0, 320) == true) {
                        rtmpDisplay?.startStream(url)
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceNative() {
        val channelId = "live_stream_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Live Stream", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live em Andamento")
            .setContentText("Capturando tela...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        // Android 14 exige que especifique o tipo mediaProjection aqui
        if (Build.VERSION.SDK_INT >= 34) { // Android 14+
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun stopStream() {
        if (rtmpDisplay?.isStreaming == true) {
            rtmpDisplay?.stopStream()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStream()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Callbacks obrigatórios da biblioteca
    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onConnectionFailed(reason: String) { stopSelf() }
    override fun onDisconnect() {}
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
    override fun onNewBitrate(bitrate: Long) {}
}
