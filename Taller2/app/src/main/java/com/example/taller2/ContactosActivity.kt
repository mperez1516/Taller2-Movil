package com.example.taller2

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ContactosActivity : AppCompatActivity() {

    private val REQUEST_READ_CONTACTS = 100
    private lateinit var listaContactos: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contactos)

        listaContactos = findViewById(R.id.listaContactos)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                REQUEST_READ_CONTACTS
            )
        } else {
            cargarContactos()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_CONTACTS && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cargarContactos()
        }
    }

    private fun cargarContactos() {
        val contactos = ArrayList<String>()
        val cr: ContentResolver = contentResolver
        val cursor: Cursor? = cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            null, null, null, null
        )

        var i = 1
        cursor?.use {
            while (it.moveToNext()) {
                val nombre = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                )
                contactos.add("$i. $nombre")
                i++
            }
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, contactos)
        listaContactos.adapter = adapter
    }
}
