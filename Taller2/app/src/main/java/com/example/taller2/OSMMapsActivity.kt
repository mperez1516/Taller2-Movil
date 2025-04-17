package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView

class OSMMapsActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var mapController: MapController
    private val startPoint = GeoPoint(4.62, -74.07) // Coordenadas iniciales (Bogotá)
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_osmmaps)

        // Configuración inicial de OSM
        Configuration.getInstance().userAgentValue = packageName

        // Configuración del mapa
        mapView = findViewById(R.id.osmMap)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Configuración del controlador del mapa
        mapController = mapView.controller as MapController
        mapController.setZoom(18.0)
        mapController.setCenter(startPoint)

        // Verificar y solicitar permisos
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            // Mostrar explicación si es necesario
            if (missingPermissions.any { permission ->
                    ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
                }) {
                showPermissionExplanationDialog()
            } else {
                requestPermissions(missingPermissions.toTypedArray())
            }
        } else {
            // Todos los permisos ya están concedidos
            setupMapWithLocation()
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permisos requeridos")
            .setMessage("La aplicación necesita permisos de ubicación y almacenamiento para funcionar correctamente")
            .setPositiveButton("Entendido") { _, _ ->
                requestPermissions(REQUIRED_PERMISSIONS)
            }
            .setNegativeButton("Cancelar") { _, _ ->
                Toast.makeText(
                    this,
                    "Algunas funciones pueden no estar disponibles sin los permisos",
                    Toast.LENGTH_LONG
                ).show()
            }
            .create()
            .show()
    }

    private fun requestPermissions(permissions: Array<String>) {
        ActivityCompat.requestPermissions(
            this,
            permissions,
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun setupMapWithLocation() {
        // Aquí puedes activar la capa de ubicación si lo deseas
        // mapView.overlays.add(LocationOverlay(mapView))
        Toast.makeText(this, "Permisos concedidos, mostrando mapa", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    setupMapWithLocation()
                } else {
                    Toast.makeText(
                        this,
                        "Algunos permisos fueron denegados, la funcionalidad puede estar limitada",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}