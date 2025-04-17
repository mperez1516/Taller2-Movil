package com.example.taller2

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Botón para contactos
        val btnContactos: ImageButton = findViewById(R.id.btnContactos)
        btnContactos.setOnClickListener {
            val intent = Intent(this, ContactosActivity::class.java)
            startActivity(intent)
        }

        // Botón para imágenes 
        val btnImagenes: ImageButton = findViewById(R.id.btnImagen)
        btnImagenes.setOnClickListener {
            val intent = Intent(this, ImagePickerActivity::class.java)
            startActivity(intent)
        }
    }
}