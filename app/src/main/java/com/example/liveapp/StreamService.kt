package com.example.liveapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.media.projection.MediaProjection // <-- Importação adicionada
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker

class StreamService : Service(), ConnectChecker {

    private var rtmpDisplay: RtmpDisplay? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNative()
        rtmpDisplay = RtmpDisplay(baseContext, true, this)
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
                val audioSourceChoice = intent.getIntExtra("audioSource", 0) // 0: mic, 1: internal

                if (resultData != null) {
                    rtmpDisplay?.setIntentResult(resultCode, resultData)
                    
                    // Determine the correct audio source constant.
                    // REMOTE_SUBMIX is for internal audio and requires Android 10 (API 29).
                    // We fallback to MIC if the user selects internal audio on an older OS.
                    val source = if (audioSourceChoice == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaRecorder.AudioSource.REMOTE_SUBMIX
                    } else {
                        MediaRecorder.AudioSource.MIC
                    }
                    
                    // Set the MediaProjection for internal audio capture <-- CÓDIGO ADICIONADO
                    if (audioSourceChoice == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val mediaProjection = rtmpDisplay?.mediaProjection
                        if (mediaProjection != null) {
                            rtmpDisplay?.setMediaProjection(mediaProjection)
                        }
                    }

                    // Use the library's prepareAudio method to set the source.
                    // Parameters: audioSource, sampleRate, isStereo, echoCanceler, noiseSuppressor
                    // FIX: Changed the last two parameters from Int (0) to Boolean (false)
                    val audioPrepared = rtmpDisplay?.prepareAudio(source, 44100, true, false, false) == true
                    
                    if (audioPrepared && rtmpDisplay?.prepareVideo(width, height, 30, bitrate, 0, 320) == true) {
                        rtmpDisplay?.startStream(url)
                    } else if (!audioPrepared) {
                        // Handle case where audio preparation fails (e.g., unsupported source)
                        // This is a good place to log an error or notify the user
                        // For now, we'll just proceed with video if possible, but the user reported audio issue
                        // Log.e("StreamService", "Audio preparation failed with source: $source")
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
