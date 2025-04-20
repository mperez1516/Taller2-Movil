package com.example.taller2

// Asegúrate de que todos los imports necesarios están presentes
import android.Manifest
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener // Importar SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.internal.ViewUtils.hideKeyboard
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
// import org.osmdroid.views.MapController // No es necesario importar si no hay ambigüedad
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt


class OSMMapsActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var mapView: MapView
    // No necesitas especificar MapController si no hay conflicto
    private lateinit var mapController: org.osmdroid.views.MapController
    private lateinit var editTextAddress: EditText // *** CORRECCIÓN: Nombre de variable consistente ***
    //Para la ubicacion actual
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder

    //Para permisos
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    //Para sensor de la luz punto 5
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    //Umbral de la luz para estandarizar la baja luminosidad
    private val LIGHT_THRESHOLD = 10.0f

    //Ubicacion inicial y ultima conocida
    // Ajusta el punto inicial si prefieres Javeriana (4.6283, -74.0659) o Santafé
    private val startPoint = GeoPoint(4.6283, -74.0659)
    //Guarda el punto para tu lógica original de JSON
    private var lastRecordedPoint: GeoPoint? = null

    //Variable para hallar la distancia al marcador (Punto 8)
    private var lastPlaceMarked: GeoPoint? = null // Tu nombre de variable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración OSMdroid ANTES de setContentView
        Configuration.getInstance().apply {
            load(applicationContext, getPreferences(Context.MODE_PRIVATE))
            userAgentValue = packageName
        }

        setContentView(R.layout.activity_osmmaps)



        //Inicializar vistas
        mapView = findViewById(R.id.osmMap)
        mapController = mapView.controller as org.osmdroid.views.MapController
        editTextAddress = findViewById(R.id.editTextAddress)


        //Inicializar Geocoder
        geocoder = Geocoder(this, Locale.getDefault())

        //Inicializar FusedLocation
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //Inicializar sensor manager y el sensor de luz
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)


        //Configuracion del mapa
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapController.setZoom(17.0) // Zoom inicial ajustado
        mapController.setCenter(startPoint)

        //Añadir marcador inicial
        addMarker(startPoint, "Ubicacion inicial", R.drawable.ic_start_marker)

        //Configurar barra de busqueda
        setupSearchBar()

        //Verificar permisos
        checkAndRequestPermissions()

        // Configurar sensor de luz
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: run {
            Toast.makeText(this, "Sensor de luz no disponible", Toast.LENGTH_SHORT).show()
        }

    }

    private fun setupSearchBar(){
        editTextAddress.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                val address = v.text.toString().trim()
                if (address.isNotEmpty()) {
                    searchAddress(address)
                    hideKeyboard(v)
                    true
                } else {
                    Toast.makeText(this, "Ingrese una dirección", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                false
            }
        }
    }

    private fun searchAddress(addressString: String){
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, "Geocoder no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Buscando dirección...", Toast.LENGTH_SHORT).show()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(addressString, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        runOnUiThread { handleGeocodeResult(addresses, addressString) }
                    }

                    override fun onError(errorMessage: String?) {
                        runOnUiThread {
                            Toast.makeText(
                                this@OSMMapsActivity,
                                "Error: ${errorMessage ?: "Desconocido"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                try {
                    val addresses = geocoder.getFromLocationName(addressString, 1)
                    runOnUiThread { handleGeocodeResult(addresses ?: mutableListOf(), addressString) }
                } catch (e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this, "Error de conexión", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGeocodeResult(addresses: List<Address>, originalQuery: String){
        // Eliminar todos los marcadores excepto el inicial
        mapView.overlays.removeAll { it is Marker && it.title != "Ubicación inicial" }

        if (addresses.isNotEmpty()) {
            val location = addresses[0]
            val point = GeoPoint(location.latitude, location.longitude)
            val addressName = location.getAddressLine(0) ?: originalQuery

            // Añadir nuevo marcador
            addMarker(point, addressName, R.drawable.ic_click_marker)

            // Centrar mapa y mostrar distancia
            mapController.setCenter(point)
            mapController.setZoom(18.0)
            showDistanceFromCurrentLocation(point)
        } else {
            Toast.makeText(this, "No se encontró '$originalQuery'", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDistanceFromCurrentLocation(destination: GeoPoint) {
        if (!hasLocationPermissions()) {
            checkAndRequestPermissions()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val currentPoint = GeoPoint(location.latitude, location.longitude)
                val distance = currentPoint.distanceToAsDouble(destination)

                val distanceText = if (distance < 1000) {
                    "${distance.roundToInt()} metros"
                } else {
                    "%.2f km".format(distance / 1000)
                }

                Toast.makeText(
                    this,
                    "Distancia: $distanceText",
                    Toast.LENGTH_LONG
                ).show()

                // Actualizar marcador de ubicación actual
                updateCurrentLocationMarker(currentPoint)
            } else {
                Toast.makeText(this, "No se pudo obtener ubicación actual", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error obteniendo ubicación", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCurrentLocationMarker(point: GeoPoint) {
        // Eliminar marcador de ubicación actual si existe
        mapView.overlays.removeAll { it is Marker && it.title == "Mi ubicación" }

        // Añadir nuevo marcador
        addMarker(point, "Mi ubicación", R.drawable.ic_click_marker)
    }

    private fun addMarker(point: GeoPoint, title: String, iconResId: Int) {
        val marker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.title = title
            try {
                icon = ContextCompat.getDrawable(this@OSMMapsActivity, iconResId)
            } catch (e: Exception) {
                Log.e("Marker", "Error cargando icono", e)
            }
        }
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun hideKeyboard(view: android.view.View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    // Permisos
    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Algunos permisos fueron denegados. La funcionalidad estará limitada",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Sensor de luz
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_LIGHT) {
                adjustMapTheme(it.values[0])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun adjustMapTheme(lightLevel: Float) {
        val isDark = lightLevel < LIGHT_THRESHOLD
        val tilesOverlay = mapView.overlayManager.tilesOverlay

        if (isDark) {
            tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        } else {
            tilesOverlay.setColorFilter(null)
        }
        mapView.invalidate()
    }

    private fun hasLocationPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

}