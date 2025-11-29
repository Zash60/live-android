package com.example.liveapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker

class StreamService : Service(), ConnectChecker {

    private var rtmpDisplay: RtmpDisplay? = null
    private var internalAudioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private var isStreaming = false

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
                val audioSource = intent.getIntExtra("audioSource", 0) // 0: mic, 1: internal, 2: both

                if (resultData != null) {
                    rtmpDisplay?.setIntentResult(resultCode, resultData)
                    
                    // Prepare audio based on selected source
                    when (audioSource) {
                        0 -> { // Microphone only
                            rtmpDisplay?.prepareAudio()
                        }
                        1 -> { // Internal audio only
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // Use internal audio capture for Android 10+
                                prepareInternalAudio()
                            } else {
                                // Fallback to microphone for older versions
                                rtmpDisplay?.prepareAudio()
                            }
                        }
                        2 -> { // Both microphone and internal audio
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                prepareInternalAudio()
                                // Also prepare microphone if the library supports it
                                rtmpDisplay?.prepareAudio()
                            } else {
                                // Fallback to microphone for older versions
                                rtmpDisplay?.prepareAudio()
                            }
                        }
                    }
                    
                    if (rtmpDisplay?.prepareVideo(width, height, 30, bitrate, 0, 320) == true) {
                        rtmpDisplay?.startStream(url)
                        isStreaming = true
                        
                        // Start internal audio thread if needed
                        if (audioSource == 1 || (audioSource == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)) {
                            startInternalAudioThread()
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun prepareInternalAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val sampleRate = 44100
                val channelConfig = AudioFormat.CHANNEL_IN_STEREO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                
                // Use MediaRecorder.AudioSource.REMOTE_SUBMIX for internal audio
                internalAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.REMOTE_SUBMIX,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startInternalAudioThread() {
        internalAudioRecord?.let { audioRecord ->
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return
            }
            
            audioThread = Thread {
                try {
                    audioRecord.startRecording()
                    val buffer = ByteArray(4096)
                    
                    while (isStreaming && !Thread.currentThread().isInterrupted) {
                        val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            // Send the audio data to the RTMP stream
                            // Note: This depends on the library's API for sending custom audio data
                            // You might need to use a different method based on the library
                            rtmpDisplay?.sendAudio(buffer, bytesRead)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        audioRecord.stop()
                        audioRecord.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            audioThread?.start()
        }
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
        isStreaming = false
        
        // Stop audio thread
        audioThread?.interrupt()
        try {
            audioThread?.join(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        
        // Release audio record
        internalAudioRecord?.let { audioRecord ->
            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                try {
                    audioRecord.stop()
                    audioRecord.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        internalAudioRecord = null
        
        // Stop RTMP stream
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
