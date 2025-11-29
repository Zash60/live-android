package com.example.liveapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker

class StreamService : Service(), ConnectChecker {

    // Usamos nossa classe customizada em vez de RtmpDisplay direto
    private var rtmpDisplay: CustomRtmpDisplay? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNative()
        // Inicializa nossa classe customizada
        rtmpDisplay = CustomRtmpDisplay(baseContext, true, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == "STOP") {
            stopStream()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent != null && rtmpDisplay != null) {
            if (!rtmpDisplay!!.isStreaming) {
                val resultCode = intent.getIntExtra("code", -1)
                val resultData = intent.getParcelableExtra<Intent>("data")
                val url = intent.getStringExtra("endpoint")
                val width = intent.getIntExtra("width", 1280)
                val height = intent.getIntExtra("height", 720)
                val bitrate = intent.getIntExtra("bitrate", 2500 * 1024)
                val useInternalAudio = intent.getBooleanExtra("internal_audio", false)

                if (resultData != null) {
                    rtmpDisplay?.setIntentResult(resultCode, resultData)
                    
                    // CONFIGURAÇÃO DE ÁUDIO INTERNO
                    if (useInternalAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Define a fonte como PLAYBACK_CAPTURE (Áudio Interno)
                        rtmpDisplay?.setAudioSource(MediaRecorder.AudioSource.PLAYBACK_CAPTURE)
                    } else {
                        // Padrão Microfone
                        rtmpDisplay?.setAudioSource(MediaRecorder.AudioSource.MIC)
                    }

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
            .setContentText("Capturando tela e áudio...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
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

    override fun onConnectionStarted(url: String) {}
    override fun onConnectionSuccess() {}
    override fun onConnectionFailed(reason: String) { stopSelf() }
    override fun onDisconnect() {}
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
    override fun onNewBitrate(bitrate: Long) {}

    /**
     * Classe auxiliar para acessar a propriedade 'audioRecorder' que é protegida na biblioteca.
     * Isso permite mudar a fonte de áudio sem reescrever a lib inteira.
     */
    class CustomRtmpDisplay(context: Context, useOpengl: Boolean, connectChecker: ConnectChecker) 
        : RtmpDisplay(context, useOpengl, connectChecker) {
        
        fun setAudioSource(source: Int) {
            // Acessa a propriedade 'audioRecorder' da classe pai (DisplayBase/RtmpDisplay)
            this.audioRecorder.audioSource = source
        }
    }
}
