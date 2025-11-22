package com.example.liveapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

// IMPORTS DO ROOTENCODER
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

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)

        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        btnStream = findViewById(R.id.btnStream)
        rb720 = findViewById(R.id.rb720)

        // Inicializa usando a estrutura nova
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
            
            // Na versao 2.6.6: width, height, fps, bitrate, rotation, dpi
            if (rtmpDisplay.prepareAudio() && rtmpDisplay.prepareVideo(width, height, 30, bitrate, 0, 320)) {
                rtmpDisplay.startStream("${etUrl.text}/${etKey.text}")
                btnStream.text = "PARAR LIVE"
            } else {
                Toast.makeText(this, "Erro: Resolução não suportada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- CALLBACKS DO ROOTENCODER (SEM Sufixo Rtmp) ---

    override fun onConnectionSuccess() {
        runOnUiThread { Toast.makeText(this, "Conectado!", Toast.LENGTH_SHORT).show() }
    }

    override fun onConnectionFailed(reason: String) {
        runOnUiThread {
            rtmpDisplay.stopStream()
            btnStream.text = "INICIAR LIVE"
            Toast.makeText(this, "Erro: $reason", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDisconnect() {
        runOnUiThread {
            btnStream.text = "INICIAR LIVE"
            Toast.makeText(this, "Desconectado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAuthError() {
        runOnUiThread { Toast.makeText(this, "Erro de Autenticação", Toast.LENGTH_SHORT).show() }
    }

    override fun onAuthSuccess() {
        runOnUiThread { Toast.makeText(this, "Autenticado", Toast.LENGTH_SHORT).show() }
    }
    
    // Necessário na nova versão
    override fun onNewBitrate(bitrate: Long) {
        
    }
}
