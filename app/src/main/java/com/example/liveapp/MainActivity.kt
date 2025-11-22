package com.example.liveapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

// IMPORTS CORRETOS PARA VERSÃO 2.6.6
import com.pedro.rtplibrary.rtmp.RtmpDisplay
import net.ossrs.rtmp.ConnectCheckerRtmp

class MainActivity : AppCompatActivity(), ConnectCheckerRtmp {
    
    private lateinit var rtmpDisplay: RtmpDisplay
    private lateinit var btnStream: Button
    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText
    private lateinit var rb720: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Pede permissão de áudio
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)

        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        btnStream = findViewById(R.id.btnStream)
        rb720 = findViewById(R.id.rb720)
        
        // Inicializa a biblioteca
        rtmpDisplay = RtmpDisplay(baseContext, true, this)

        btnStream.setOnClickListener {
            if (rtmpDisplay.isStreaming) {
                rtmpDisplay.stopStream()
                btnStream.text = "INICIAR LIVE"
            } else {
                // Inicia o pedido para gravar a tela
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
            
            // Configura a intenção de captura
            rtmpDisplay.setIntentResult(res, data)
            
            // Prepara e inicia (Resolução, FPS, Bitrate, Rotação, DPI)
            if (rtmpDisplay.prepareAudio() && rtmpDisplay.prepareVideo(width, height, 30, bitrate, 0, 320)) {
                val fullUrl = "${etUrl.text}/${etKey.text}"
                rtmpDisplay.startStream(fullUrl)
                btnStream.text = "PARAR LIVE"
            } else {
                Toast.makeText(this, "Erro: Resolução inválida para este celular", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    // --- CALLBACKS OBRIGATÓRIOS DA VERSÃO 2.6.6 ---

    override fun onConnectionSuccessRtmp() {
        runOnUiThread { Toast.makeText(this, "Conectado!", Toast.LENGTH_SHORT).show() }
    }

    override fun onConnectionFailedRtmp(reason: String) {
        runOnUiThread {
            rtmpDisplay.stopStream()
            btnStream.text = "INICIAR LIVE"
            Toast.makeText(this, "Erro: $reason", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDisconnectRtmp() {
        runOnUiThread { 
            btnStream.text = "INICIAR LIVE" 
            Toast.makeText(this, "Desconectado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAuthErrorRtmp() {
        runOnUiThread { Toast.makeText(this, "Erro de Chave/Auth", Toast.LENGTH_SHORT).show() }
    }

    override fun onAuthSuccessRtmp() {
        runOnUiThread { Toast.makeText(this, "Autenticado", Toast.LENGTH_SHORT).show() }
    }
}
