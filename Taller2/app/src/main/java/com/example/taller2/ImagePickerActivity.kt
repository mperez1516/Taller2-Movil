package com.example.taller2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class ImagePickerActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var currentPhotoPath: String
    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_GALLERY = 2
    private val REQUEST_PERMISSION_CAMERA = 101
    private val REQUEST_PERMISSION_STORAGE = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_picker)

        imageView = findViewById(R.id.imageView)
        val btnGallery = findViewById<Button>(R.id.btnGallery)
        val btnCamera = findViewById<Button>(R.id.btnCamera)

        // Configurar tamaño fijo para la imagen
        val fixedSize = resources.getDimensionPixelSize(R.dimen.image_fixed_size)
        imageView.layoutParams.width = fixedSize
        imageView.layoutParams.height = fixedSize
        imageView.requestLayout()

        btnGallery.setOnClickListener {
            checkStoragePermissionAndOpenGallery()
        }

        btnCamera.setOnClickListener {
            checkCameraPermissionAndOpenCamera()
        }
    }

    private fun checkStoragePermissionAndOpenGallery() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_PERMISSION_STORAGE
            )
        } else {
            openGallery()
        }
    }

    private fun checkCameraPermissionAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_PERMISSION_CAMERA
            )
        } else {
            dispatchTakePictureIntent()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_GALLERY)
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    null
                }
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSION_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    dispatchTakePictureIntent()
                } else {
                    Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_PERMISSION_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openGallery()
                } else {
                    Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_GALLERY -> {
                    val selectedImageUri: Uri? = data?.data
                    selectedImageUri?.let {
                        val bitmap = decodeSampledBitmapFromUri(it,
                            imageView.width, imageView.height)
                        imageView.setImageBitmap(bitmap)
                    }
                }
                REQUEST_IMAGE_CAPTURE -> {
                    val bitmap = BitmapFactory.decodeFile(currentPhotoPath)
                    // Guardar la imagen en la galería
                    MediaStore.Images.Media.insertImage(
                        contentResolver,
                        bitmap,
                        "Foto_capturada",
                        "Descripción de la foto"
                    )
                    // Mostrar la imagen con tamaño fijo
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        imageView.width,
                        imageView.height,
                        true
                    )
                    imageView.setImageBitmap(scaledBitmap)
                }
            }
        }
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap {
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, this)
            }

            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

            inJustDecodeBounds = false
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, this)
            } ?: throw IOException("No se pudo cargar la imagen")
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }
}