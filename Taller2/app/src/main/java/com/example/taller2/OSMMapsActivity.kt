package com.example.taller2

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

class OSMMapsActivity : AppCompatActivity() {
    private lateinit var mapView: MapView
    private lateinit var mapController: MapController
    private val startPoint = GeoPoint(4.76224175, -74.0464092365852) // Santafé
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private val lightThreshold = 20000.0f

    private val lightSensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val lightValue = it.values[0]
                if (lightValue < lightThreshold) {
                    Toast.makeText(
                        this@OSMMapsActivity,
                        "Luminosidad baja detectada: $lightValue lx",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_osmmaps)

        Configuration.getInstance().userAgentValue = packageName

        mapView = findViewById(R.id.osmMap)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        mapController = mapView.controller as MapController
        mapController.setZoom(18.0)
        mapController.setCenter(startPoint)

        checkAndRequestPermissions()

        // Sensor de luz
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor == null) {
            Toast.makeText(this, "Sensor de luz no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            if (missingPermissions.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }) {
                showPermissionExplanationDialog()
            } else {
                requestPermissions(missingPermissions.toTypedArray())
            }
        } else {
            setupMapWithLocation()
        }
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permisos requeridos")
            .setMessage("La app necesita permisos de ubicación y almacenamiento para funcionar correctamente.")
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
        ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE)
    }

    private fun setupMapWithLocation() {
        Toast.makeText(this, "Permisos concedidos, mostrando mapa", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
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

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        // Controlador del mapa
        val controller = mapView.controller
        controller.setZoom(18.0)
        controller.setCenter(startPoint)

        // Modo oscuro: invertir colores si es necesario
        val uiManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiManager.nightMode == UiModeManager.MODE_NIGHT_YES) {
            mapView.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }

        // Registrar sensor
        lightSensor?.let {
            sensorManager.registerListener(lightSensorListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        sensorManager.unregisterListener(lightSensorListener)
    }
}