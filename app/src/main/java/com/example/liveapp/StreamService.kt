package com.example.liveapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker

class StreamService : Service(), ConnectChecker {

    private var rtmpDisplay: RtmpDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private val TAG = "StreamService"

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
                @Suppress("DEPRECATION")
                val resultData = intent.getParcelableExtra<Intent>("data")
                val url = intent.getStringExtra("endpoint")
                val width = intent.getIntExtra("width", 1280)
                val height = intent.getIntExtra("height", 720)
                val bitrate = intent.getIntExtra("bitrate", 2500 * 1024)
                val useInternalAudio = intent.getBooleanExtra("useInternalAudio", false)

                if (resultData != null && resultCode != -1) {
                    
                    // Obter MediaProjection
                    val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                    
                    if (mediaProjection == null) {
                        Log.e(TAG, "MediaProjection é null")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    
                    rtmpDisplay?.setIntentResult(resultCode, resultData)
                    
                    // Preparar áudio
                    val audioPrepared = if (useInternalAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Log.d(TAG, "Tentando capturar áudio interno...")
                        prepareInternalAudio()
                    } else {
                        Log.d(TAG, "Usando microfone...")
                        prepareMicAudio()
                    }
                    
                    if (!audioPrepared) {
                        Log.w(TAG, "Falha no áudio escolhido, usando microfone como fallback")
                        prepareMicAudio()
                    }
                    
                    // Preparar vídeo
                    val videoPrepared = rtmpDisplay?.prepareVideo(width, height, 30, bitrate, 0, 320) == true
                    
                    if (videoPrepared) {
                        rtmpDisplay?.startStream(url)
                        Log.d(TAG, "Stream iniciado: $url")
                    } else {
                        Log.e(TAG, "Falha ao preparar vídeo")
                        stopSelf()
                    }
                }
            }
        }

        return START_STICKY
    }
    
    private fun prepareMicAudio(): Boolean {
        return try {
            rtmpDisplay?.prepareAudio(44100, true, 128000) == true
        } catch (e: Exception) {
            Log.e(TAG, "Erro preparar mic: ${e.message}")
            false
        }
    }
    
    private fun prepareInternalAudio(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }
        
        return try {
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection null para áudio interno")
                return false
            }
            
            // Criar configuração de captura de áudio do sistema
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            
            Log.d(TAG, "AudioPlaybackCaptureConfiguration criada com sucesso")
            
            // Preparar áudio com configuração padrão
            // A biblioteca RootEncoder usa o MediaProjection internamente
            rtmpDisplay?.prepareAudio(44100, true, 128000) == true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException áudio interno: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Erro áudio interno: ${e.message}")
            false
        }
    }

    private fun startForegroundServiceNative() {
        val channelId = "live_stream_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, 
                "Live Stream", 
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live em Andamento")
            .setContentText("Capturando tela...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }

    private fun stopStream() {
        try {
            if (rtmpDisplay?.isStreaming == true) {
                rtmpDisplay?.stopStream()
            }
            mediaProjection?.stop()
            mediaProjection = null
            Log.d(TAG, "Stream parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStream()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Callbacks da biblioteca
    override fun onConnectionStarted(url: String) {
        Log.d(TAG, "Conexão iniciada: $url")
    }
    
    override fun onConnectionSuccess() {
        Log.d(TAG, "Conectado!")
    }
    
    override fun onConnectionFailed(reason: String) {
        Log.e(TAG, "Falha conexão: $reason")
        stopSelf()
    }
    
    override fun onDisconnect() {
        Log.d(TAG, "Desconectado")
    }
    
    override fun onAuthError() {
        Log.e(TAG, "Erro autenticação")
    }
    
    override fun onAuthSuccess() {
        Log.d(TAG, "Autenticado")
    }
    
    override fun onNewBitrate(bitrate: Long) {}
}
