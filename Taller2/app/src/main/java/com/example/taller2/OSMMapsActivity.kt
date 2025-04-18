package com.example.taller2

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

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

    private var lastRecordedPoint: GeoPoint? = null

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

        val startMarker = Marker(mapView)
        startMarker.position = startPoint
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        startMarker.icon = ContextCompat.getDrawable(this, R.drawable.ic_start_marker)
        startMarker.title = "Inicio - Santafé"
        mapView.overlays.add(startMarker)

        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    handleUserClick(it)
                }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                return false
            }
        })
        mapView.overlays.add(mapEventsOverlay)
    }

    private fun handleUserClick(newPoint: GeoPoint) {
        val marker = Marker(mapView)
        marker.position = newPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_click_marker)
        marker.title = "Nueva ubicación"
        mapView.overlays.add(marker)
        mapView.invalidate()

        mapController.animateTo(newPoint)

        lastRecordedPoint?.let {
            val distance = it.distanceToAsDouble(newPoint)
            if (distance > 30.0 && (it.latitude != newPoint.latitude || it.longitude != newPoint.longitude)) {
                saveLocationToJson(newPoint)
                lastRecordedPoint = newPoint
            }
        } ?: run {
            saveLocationToJson(newPoint)
            lastRecordedPoint = newPoint
        }
    }

    private fun saveLocationToJson(point: GeoPoint) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = sdf.format(Date())

        val newEntry = JSONObject().apply {
            put("latitud", point.latitude)
            put("longitud", point.longitude)
            put("fecha_hora", currentTime)
        }

        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, "ubicaciones.json")

        val jsonArray: JSONArray = if (file.exists()) {
            try {
                JSONArray(file.readText())
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }

        jsonArray.put(newEntry)

        try {
            val writer = FileWriter(file)
            writer.write(jsonArray.toString(4)) // formato indentado
            writer.close()
            Toast.makeText(this, "Ubicación guardada en:\n${file.absolutePath}", Toast.LENGTH_SHORT).show()

            // Mostrar contenido en AlertDialog
            AlertDialog.Builder(this)
                .setTitle("Contenido del archivo JSON")
                .setMessage(jsonArray.toString(4))
                .setPositiveButton("Cerrar", null)
                .show()

        } catch (e: IOException) {
            Toast.makeText(this, "Error al guardar ubicación", Toast.LENGTH_SHORT).show()
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
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()

        val controller = mapView.controller
        controller.setZoom(18.0)
        controller.setCenter(startPoint)

        val uiManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiManager.nightMode == UiModeManager.MODE_NIGHT_YES) {
            mapView.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}