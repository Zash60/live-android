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
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnStream: Button
    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText
    private lateinit var rb720: RadioButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Pede permissões (Áudio e Notificação para Android 13+)
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)

        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        btnStream = findViewById(R.id.btnStream)
        rb720 = findViewById(R.id.rb720)

        btnStream.setOnClickListener {
            // Em vez de iniciar direto, abrimos o pedido de gravar tela
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), 100)
        }
        
        // Botão para parar (envia comando para o serviço)
        val btnStop = Button(this)
        btnStop.text = "PARAR E FECHAR"
        btnStop.setOnClickListener {
            val intent = Intent(this, StreamService::class.java)
            intent.action = "STOP"
            startService(intent)
        }
        (findViewById<LinearLayout>(0) ?: window.decorView.findViewById(android.R.id.content) as LinearLayout).addView(btnStop)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 100 && res == Activity.RESULT_OK && data != null) {
            
            val width = if (rb720.isChecked) 1280 else 1920
            val height = if (rb720.isChecked) 720 else 1080
            val bitrate = if (rb720.isChecked) 2500 * 1024 else 4500 * 1024
            
            // Inicia o SERVIÇO passando os dados da permissão de tela
            val intent = Intent(this, StreamService::class.java)
            intent.putExtra("code", res)
            intent.putExtra("data", data)
            intent.putExtra("endpoint", "${etUrl.text}/${etKey.text}")
            intent.putExtra("width", width)
            intent.putExtra("height", height)
            intent.putExtra("bitrate", bitrate)
            
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Toast.makeText(this, "Iniciando serviço de live...", Toast.LENGTH_LONG).show()
            // Pode minimizar o app agora
            moveTaskToBack(true)
        }
    }
}
