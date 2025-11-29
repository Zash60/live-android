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

class MainActivity : AppCompatActivity() {

    private lateinit var btnStream: Button
    private lateinit var btnStop: Button
    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText
    private lateinit var rb720: RadioButton
    private lateinit var rgAudioSource: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Permissões
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1)

        // Vincular componentes da tela
        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        btnStream = findViewById(R.id.btnStream)
        btnStop = findViewById(R.id.btnStop)
        rb720 = findViewById(R.id.rb720)
        rgAudioSource = findViewById(R.id.rgAudioSource)

        btnStream.setOnClickListener {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), 100)
        }
        
        btnStop.setOnClickListener {
            val intent = Intent(this, StreamService::class.java)
            intent.action = "STOP"
            startService(intent)
            Toast.makeText(this, "Live Encerrada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 100 && res == Activity.RESULT_OK && data != null) {
            
            val width = if (rb720.isChecked) 1280 else 1920
            val height = if (rb720.isChecked) 720 else 1080
            val bitrate = if (rb720.isChecked) 2500 * 1024 else 4500 * 1024
            
            // Verifica se usuário selecionou áudio interno
            val useInternalAudio = rgAudioSource.checkedRadioButtonId == R.id.rbInternal
            
            // Aviso se selecionou áudio interno em Android < 10
            if (useInternalAudio && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Toast.makeText(
                    this, 
                    "Áudio interno requer Android 10+. Usando microfone.", 
                    Toast.LENGTH_LONG
                ).show()
            }
            
            val intent = Intent(this, StreamService::class.java)
            intent.putExtra("code", res)
            intent.putExtra("data", data)
            intent.putExtra("endpoint", "${etUrl.text}/${etKey.text}")
            intent.putExtra("width", width)
            intent.putExtra("height", height)
            intent.putExtra("bitrate", bitrate)
            intent.putExtra("useInternalAudio", useInternalAudio)
            
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Toast.makeText(this, "Live Iniciada em Background!", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }
}
