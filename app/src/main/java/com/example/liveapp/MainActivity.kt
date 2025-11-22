package com.example.liveapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker

class MainActivity : AppCompatActivity(), ConnectChecker {
    private lateinit var rtmpDisplay: RtmpDisplay
    private lateinit var btnStream: Button
    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText
    private lateinit var rb720: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Solicita permissões básicas ao abrir
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)

        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        btnStream = findViewById(R.id.btnStream)
        rb720 = findViewById(R.id.rb720)
        rtmpDisplay = RtmpDisplay(baseContext, true, this)

        btnStream.setOnClickListener {
            if (rtmpDisplay.isStreaming) {
                rtmpDisplay.stopStream()
                btnStream.text = "INICIAR LIVE"
            } else {
                val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(), 100)
            }
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 100 && res == Activity.RESULT_OK && data != null) {
            val width = if (rb720.isChecked) 1280 else 1920
            val height = if (rb720.isChecked) 720 else 1080
            val bitrate = if (rb720.isChecked) 2500 * 1024 else 4500 * 1024
            
            rtmpDisplay.setIntentResult(res, data)
            if (rtmpDisplay.prepareAudio() && rtmpDisplay.prepareVideo(width, height, 30, bitrate, 2, 320)) {
                rtmpDisplay.startStream("${etUrl.text}/${etKey.text}")
                btnStream.text = "PARAR LIVE"
            }
        }
    }
    
    // Callbacks obrigatórios
    override fun onConnectionSuccess() { runOnUiThread { Toast.makeText(this, "Conectado!", Toast.LENGTH_SHORT).show() } }
    override fun onConnectionFailed(reason: String) { runOnUiThread { rtmpDisplay.stopStream(); btnStream.text = "INICIAR"; Toast.makeText(this, "Erro: $reason", Toast.LENGTH_SHORT).show() } }
    override fun onDisconnect() { runOnUiThread { btnStream.text = "INICIAR" } }
    override fun onAuthError() {}
    override fun onAuthSuccess() {}
    override fun onNewBitrate(bitrate: Long) {}
}
