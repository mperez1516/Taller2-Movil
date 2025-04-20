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

// *** CORRECCIÓN 1: Implementar SensorEventListener ***
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
        Manifest.permission.WRITE_EXTERNAL_STORAGE
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

        //Configuracion inicial osmdroid ANTES de setContentView
        Configuration.getInstance().load(applicationContext, getPreferences(Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_osmmaps)

        //Inicializar vistas
        editTextAddress = findViewById(R.id.editTextAddress) // Usar el ID correcto del XML
        mapView = findViewById(R.id.osmMap)

        //Inicializar Geocoder
        geocoder = Geocoder(this, Locale.getDefault())

        //Inicializar FusedLocation
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        //Inicializar sensor manager y el sensor de luz
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            Toast.makeText(this, "Sensor de luminosidad no disponible", Toast.LENGTH_SHORT).show()
        }

        //Configuracion del mapa
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapController.setZoom(17.0) // Zoom inicial ajustado
        mapController.setCenter(startPoint)

        //Verificar permisos
        checkAndRequestPermissions()

        // Marcador inicial
        val startMarker = Marker(mapView)
        startMarker.position = startPoint
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        try { // Intentar cargar ícono real
            startMarker.icon = ContextCompat.getDrawable(this, R.drawable.ic_start_marker)
        } catch (e: Exception) {
            Log.w("MarkerIcon", "Drawable ic_start_marker no encontrado, usando default.")
            // Opcional: asignar un ícono de Android por defecto si falla
            // startMarker.icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_map)
        }
        startMarker.title = "Ubicacion inicial" // Título ajustado
        mapView.overlays.add(0, startMarker) // Añadir al principio

        // Overlay para eventos de mapa
        val mapEventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let {
                    handleUserClick(it) // Llama a tu función original
                }
                return true // Como en tu código
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { point ->
                    Log.d("OSMMapsActivity", "Long press detectado en: $point")
                    findAddressFromLocation(point) // Llama a la función para Punto 7
                    return true // Evento consumido
                }
                return false // Evento no consumido
            }
        })
        mapView.overlays.add(mapEventsOverlay) // Añadir overlay

        // Listener para el EditText
        editTextAddress.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val address = v.text.toString()
                if (address.isNotEmpty()) {
                    buscarDireccion(address) // Llama a la función ASÍNCRONA nueva
                    // Ocultar teclado y quitar foco
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    v.clearFocus()
                } else {
                    Toast.makeText(this, "Por favor ingrese una direccion", Toast.LENGTH_SHORT).show()
                }
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }


    // Funcion ASINCRONA para buscar direccion (Punto 6)
    private fun buscarDireccion(addressString: String) {
        Log.d("OSMMapsActivity", "Buscando dirección (Listener): $addressString")
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, "Geocoder no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(addressString, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        runOnUiThread { handleGeocoderResults(addresses, addressString) }
                    }

                    override fun onError(errorMessage: String?) {
                        runOnUiThread { handleGeocoderError("Error buscando dirección: ${errorMessage ?: "Desconocido"}") }
                    }
                })
            } else {
                // Ejecutar en background thread para versiones < 33 sería ideal,
                // pero por simplicidad mantenemos la llamada directa aquí con manejo de errores.
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(addressString, 1)
                    runOnUiThread { handleGeocoderResults(addresses ?: mutableListOf(), addressString) }
                } catch (e: IOException) {
                    runOnUiThread { handleGeocoderError("Error de red al buscar dirección.") }
                } catch (e: IllegalArgumentException) {
                    runOnUiThread { handleGeocoderError("Argumento inválido para la búsqueda.") }
                }
            }
        } catch (e: Exception) {
            runOnUiThread { handleGeocoderError("No se pudo iniciar la búsqueda: ${e.message}") }
        }
    }

    // *** CORRECCIÓN 2: Eliminar función buscarDireccionOriginal (no usada y obsoleta) ***
    /*
    private fun buscarDireccionOriginal(address: String){
        // ... código eliminado ...
    }
    */

    // Función auxiliar para manejar resultados de Geocoder (búsqueda)
    private fun handleGeocoderResults(addresses: List<Address>, originalQuery: String) {
        if (addresses.isNotEmpty()) {
            val location = addresses[0]
            val point = GeoPoint(location.latitude, location.longitude)
            Log.d("OSMMapsActivity", "Dirección encontrada: ${location.getAddressLine(0)} en $point")
            // Usar un ícono diferente para búsqueda
            addMarkerAndShowDistance(point, originalQuery, R.drawable.ic_click_marker)
            mapController.setZoom(18.0)
            mapController.animateTo(point)
        } else {
            Log.d("OSMMapsActivity", "No se encontraron resultados para: $originalQuery")
            Toast.makeText(this, "No se encontró la dirección '$originalQuery'", Toast.LENGTH_SHORT).show()
        }
    }

    // Función auxiliar para manejar errores de Geocoder
    private fun handleGeocoderError(message: String) {
        Log.e("OSMMapsActivity", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Función para geocoding inverso (Long Click - Punto 7)
    private fun findAddressFromLocation(point: GeoPoint) {
        if (!Geocoder.isPresent()) {
            handleGeocoderError("Geocoder no disponible.")
            // Fallback: mostrar marcador con coordenadas
            addMarkerAndShowDistance(point, "(${String.format("%.4f, %.4f", point.latitude, point.longitude)})", R.drawable.ic_click_marker)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(point.latitude, point.longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        runOnUiThread { handleReverseGeocodeResults(addresses, point) }
                    }
                    override fun onError(errorMessage: String?) {
                        runOnUiThread {
                            handleGeocoderError("Error obteniendo dirección: ${errorMessage ?: "Desconocido"}")
                            addMarkerAndShowDistance(point, "(${String.format("%.4f, %.4f", point.latitude, point.longitude)})", R.drawable.ic_click_marker)
                        }
                    }
                })
            } else {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                    runOnUiThread { handleReverseGeocodeResults(addresses ?: mutableListOf(), point) }
                } catch (e: IOException) {
                    runOnUiThread {
                        handleGeocoderError("Error de red al obtener dirección.")
                        addMarkerAndShowDistance(point, "(${String.format("%.4f, %.4f", point.latitude, point.longitude)})", R.drawable.ic_click_marker)
                    }
                } catch (e: IllegalArgumentException) {
                    runOnUiThread {
                        handleGeocoderError("Argumento inválido para geocoder inverso.")
                        addMarkerAndShowDistance(point, "(${String.format("%.4f, %.4f", point.latitude, point.longitude)})", R.drawable.ic_click_marker)
                    }
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                handleGeocoderError("No se pudo iniciar geocoder inverso: ${e.message}")
                addMarkerAndShowDistance(point, "(${String.format("%.4f, %.4f", point.latitude, point.longitude)})", R.drawable.ic_click_marker)
            }
        }
    }

    // Función auxiliar para manejar resultados de Geocoder (inverso)
    private fun handleReverseGeocodeResults(addresses: List<Address>, point: GeoPoint) {
        val addressText = if (addresses.isNotEmpty()) {
            addresses[0].getAddressLine(0)?.trim() ?: "Dirección desconocida"
        } else {
            Log.d("OSMMapsActivity", "No se encontró dirección para: $point")
            "Dirección desconocida"
        }
        Log.d("OSMMapsActivity", "Dirección encontrada para long press: $addressText")
        // Usar el ícono de click para este marcador
        addMarkerAndShowDistance(point, addressText, R.drawable.ic_click_marker)
    }

    // Helper para añadir marcador Y mostrar distancia (Puntos 6, 7, 8)
    private fun addMarkerAndShowDistance(point: GeoPoint, title: String, iconResId: Int?) {
        val marker = Marker(mapView)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title

        iconResId?.let { // Asignar icono si se proporciona y existe
            try {
                ContextCompat.getDrawable(this, it)?.let { drawable ->
                    marker.icon = drawable
                }
            } catch (e: Exception) {
                Log.w("MarkerIcon", "Drawable con ID $it no encontrado.")
                // Considera asignar un icono por defecto si falla la carga
            }
        }
        mapView.overlays.add(marker)
        mapView.invalidate()

        // Actualizar variable y mostrar distancia
        lastPlaceMarked = point // Actualiza tu variable para distancia
        showDistanceToLastMarker(point)
    }

    // Cálculo de Distancia (Punto 8)
    @SuppressLint("MissingPermission")
    private fun showDistanceToLastMarker(markerPoint: GeoPoint) {
        if (!hasLocationPermissions()) {
            Log.w("OSMMapsActivity", "showDistanceToLastMarker llamado sin permisos.")
            // No mostrar Toast aquí, ya que checkAndRequestPermissions debería manejarlo
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val currentLocationPoint = GeoPoint(location.latitude, location.longitude)
                    val distance = currentLocationPoint.distanceToAsDouble(markerPoint)
                    val distanceText = formatDistance(distance)
                    Toast.makeText(this, "Distancia al marcador: $distanceText", Toast.LENGTH_LONG).show()
                    Log.d("OSMMapsActivity", "Distancia calculada: $distanceText")
                } else {
                    Log.w("OSMMapsActivity", "No se pudo obtener ubicación actual para distancia.")
                    Toast.makeText(this, "No se pudo obtener ubicación actual.", Toast.LENGTH_SHORT).show()
                    // Considera un fallback si la ubicación es null (p.ej. distancia desde startPoint)
                }
            }
            .addOnFailureListener { e ->
                Log.e("OSMMapsActivity", "Error FusedLocationProvider: ${e.message}")
                Toast.makeText(this, "Error al obtener ubicación.", Toast.LENGTH_SHORT).show()
            }
    }

    // Helper para formatear distancia
    private fun formatDistance(distanceInMeters: Double): String {
        return if (distanceInMeters < 1000) {
            "${distanceInMeters.roundToInt()} metros"
        } else {
            // Asegurar Locale US para punto decimal
            String.format(Locale.US, "%.2f kilómetros", distanceInMeters / 1000.0)
        }
    }

    // Helper para verificar permisos de ubicación
    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // --- Sensor de Luminosidad (Punto 5) ---
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Registrar listener si el sensor existe
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        // Aplicar tema inicial basado en modo UI
        applyMapThemeBasedOnUiMode()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        // Desregistrar listener
        sensorManager.unregisterListener(this)
    }

    // *** CORRECCIÓN 1: Implementar métodos de SensorEventListener ***
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_LIGHT) {
                applyMapTheme(it.values[0]) // Llamar a la lógica de cambio de tema
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No necesitamos hacer nada aquí usualmente
    }

    private fun applyMapTheme(lightLevel: Float) {
        val isCurrentlyInverted = try {
            val colorFilterField = TilesOverlay::class.java.getDeclaredField("mColorFilter")
            colorFilterField.isAccessible = true
            colorFilterField.get(mapView.overlayManager.tilesOverlay) != null
        } catch (e: Exception) {
            false
        }

        if (lightLevel < LIGHT_THRESHOLD) {
            if (!isCurrentlyInverted) {
                mapView.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                mapView.invalidate()
            }
        } else {
            if (isCurrentlyInverted) {
                mapView.overlayManager.tilesOverlay.setColorFilter(null)
                mapView.invalidate()
            }
        }
    }

    private fun applyMapThemeBasedOnUiMode() {
        val uiManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isSystemNightMode = uiManager.nightMode == UiModeManager.MODE_NIGHT_YES

        val isCurrentlyInverted = try {
            val colorFilterField = TilesOverlay::class.java.getDeclaredField("mColorFilter")
            colorFilterField.isAccessible = true
            colorFilterField.get(mapView.overlayManager.tilesOverlay) != null
        } catch (e: Exception) {
            false
        }

        if (isSystemNightMode && !isCurrentlyInverted) {
            mapView.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            mapView.invalidate()
        } else if (!isSystemNightMode && isCurrentlyInverted) {
            mapView.overlayManager.tilesOverlay.setColorFilter(null)
            mapView.invalidate()
        }
    }

    // --- FUNCIONES ORIGINALES (EXISTENTES) ---
    // (handleUserClick, saveLocationToJson) - Sin cambios funcionales
    private fun handleUserClick(newPoint: GeoPoint) {
        // ... (Tu código original)
        Log.d("OriginalCode", "handleUserClick llamado para: $newPoint")
        val marker = Marker(mapView)
        marker.position = newPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        try {
            marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_click_marker)
        } catch (e: Exception) { Log.w("MarkerIcon", "Drawable ic_click_marker no encontrado.") }
        marker.title = "Nueva ubicación"
        mapView.overlays.add(marker)
        mapView.invalidate()
        lastRecordedPoint?.let {
            val distance = it.distanceToAsDouble(newPoint)
            if (distance > 30.0) {
                Log.d("OriginalCode", "Distancia > 30m, guardando en JSON.")
                saveLocationToJson(newPoint)
                lastRecordedPoint = newPoint // Actualiza el punto para la lógica JSON
            } else {
                Log.d("OriginalCode", "Distancia <= 30m, no guardando.")
            }
        } ?: run {
            Log.d("OriginalCode", "Primer punto registrado para JSON.")
            saveLocationToJson(newPoint)
            lastRecordedPoint = newPoint // Establece el punto inicial para la lógica JSON
        }
    }

    private fun saveLocationToJson(point: GeoPoint) {
        // ... (Tu código original con mejoras de manejo de errores/permisos)
        Log.d("OriginalCode", "Intentando guardar en JSON: $point")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.w("OriginalCode", "Permiso WRITE_EXTERNAL_STORAGE no concedido. No se puede guardar JSON.")
            Toast.makeText(this, "Permiso de almacenamiento necesario para guardar.", Toast.LENGTH_SHORT).show()
            return
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = sdf.format(Date())
        val newEntry = JSONObject().apply {
            put("latitud", point.latitude)
            put("longitud", point.longitude)
            put("fecha_hora", currentTime)
        }
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (dir == null) {
            Log.e("OriginalCode", "Directorio externo (Documents) no disponible.")
            Toast.makeText(this, "No se pudo acceder al directorio para guardar.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!dir.exists()) { dir.mkdirs() }
        val file = File(dir, "ubicaciones.json")
        Log.d("OriginalCode", "Ruta del archivo JSON: ${file.absolutePath}")
        val jsonArray: JSONArray = if (file.exists() && file.canRead()) {
            try { JSONArray(file.readText()) }
            catch (e: Exception) {
                Log.e("OriginalCode", "Error leyendo JSON existente: ${e.message}")
                JSONArray()
            }
        } else { JSONArray() }
        jsonArray.put(newEntry)
        try {
            val writer = FileWriter(file)
            writer.write(jsonArray.toString(4))
            writer.close()
            Log.d("OriginalCode", "Ubicación guardada correctamente.")
            Toast.makeText(this, "Ubicación guardada.", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e("OriginalCode", "Error escribiendo JSON: ${e.message}")
            Toast.makeText(this, "Error al guardar ubicación", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Código de Permisos (EXISTENTE) ---
    private fun checkAndRequestPermissions() {
        // ... (sin cambios)
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            if (missingPermissions.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
                showPermissionExplanationDialog(missingPermissions.toTypedArray())
            } else {
                ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
            }
        } else {
            onPermissionsGranted()
        }
    }

    private fun showPermissionExplanationDialog(permissionsToRequest: Array<String>) {
        // ... (sin cambios)
        AlertDialog.Builder(this)
            .setTitle("Permisos requeridos")
            .setMessage("La app necesita permisos de ubicación y almacenamiento para funcionar correctamente.")
            .setPositiveButton("Entendido") { _, _ ->
                ActivityCompat.requestPermissions(this, permissionsToRequest, LOCATION_PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Funcionalidad limitada sin permisos.", Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }

    private fun onPermissionsGranted() {
        // ... (sin cambios)
        Log.d("Permissions", "Todos los permisos requeridos han sido concedidos.")
        Toast.makeText(this, "Permisos concedidos.", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // ... (sin cambios)
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                onPermissionsGranted()
            } else {
                Log.w("Permissions", "Al menos un permiso fue denegado.")
                Toast.makeText(this, "Algunos permisos fueron denegados.", Toast.LENGTH_LONG).show()
                if (!hasLocationPermissions()){ Toast.makeText(this, "Funciones de ubicación deshabilitadas.", Toast.LENGTH_LONG).show() }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) { Toast.makeText(this, "Guardado de ubicación deshabilitado.", Toast.LENGTH_LONG).show() }
            }
        }
    }

    // Tu función original
    private fun setupMapWithLocation() {
        // ... (sin cambios)
        Log.d("Setup", "setupMapWithLocation llamado (originalmente desde checkAndRequestPermissions)")
        // Toast.makeText(this, "Permisos concedidos, mostrando mapa", Toast.LENGTH_SHORT).show() // Redundante con onPermissionsGranted
    }


}