package com.example.testbarcode

import android.content.Context
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.testbarcode.databinding.ActivityMainBinding
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer


class MainActivity : AppCompatActivity() {

    companion object{
        const val TAG = "BarcodeReader"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var reader: MultiFormatReader
    private lateinit var preview: Preview
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        initMultiFormatReader()
        startCamera()
    }

    private fun initMultiFormatReader() {

        reader = MultiFormatReader()
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder().setTargetRotation(Surface.ROTATION_0).build().apply {
                setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(this),
                        ImageAnalysis.Analyzer { image ->
                            handleImage(image)
                        })
                }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (e: Exception) {

            }

        }, ContextCompat.getMainExecutor(this))

    }

    private fun handleImage(image: ImageProxy) {
        if ((image.format == ImageFormat.YUV_420_888
                    || image.format == ImageFormat.YUV_422_888
                    || image.format == ImageFormat.YUV_444_888)
            && image.planes.size == 3
        ) {

            val buffer = image.planes[0].buffer // We get the luminance plane only, since we
            // want to binarize it and we don't wanna take color into consideration.
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)

            val rotatedImage = RotatedImage(bytes, image.width, image.height)

            rotateImageArray(rotatedImage, 90)

            val source = PlanarYUVLuminanceSource(rotatedImage.byteArray,
                rotatedImage.width,
                rotatedImage.height,
                0,
                0,
                rotatedImage.width,
                rotatedImage.height,
                false)
            Log.e(TAG, "handleImage: ${image.width} / ${image.height} " )
            val binarizer = HybridBinarizer(source)
            // Create a BinaryBitmap.
            val binaryBitmap = BinaryBitmap(binarizer)
            // Try decoding...
            try {
                Log.e(TAG, "decoding..." )
                val result: Result = reader.decode(binaryBitmap)
                Log.e(TAG, "decode success: ${result.text} + ${result.barcodeFormat.name}" )
                image.close()
            } catch (e: Exception) {
                Log.e(TAG, "error: ${e.cause}")
                image.close()
            }

        }
    }

    private fun rotateImageArray(imageToRotate: RotatedImage, rotationDegrees: Int) {
        if (rotationDegrees == 0) return // no rotation
        if (rotationDegrees % 90 != 0) return // only 90 degree times rotations

        val width = imageToRotate.width
        val height = imageToRotate.height

        val rotatedData = ByteArray(imageToRotate.byteArray.size)
        for (y in 0 until height) { // we scan the array by rows
            for (x in 0 until width) {
                when (rotationDegrees) {
                    90 -> rotatedData[x * height + height - y - 1] =
                        imageToRotate.byteArray[x + y * width] // Fill from top-right toward left (CW)
                    180 -> rotatedData[width * (height - y - 1) + width - x - 1] =
                        imageToRotate.byteArray[x + y * width] // Fill from bottom-right toward up (CW)
                    270 -> rotatedData[y + x * height] =
                        imageToRotate.byteArray[y * width + width - x - 1] // The opposite (CCW) of 90 degrees
                }
            }
        }

        imageToRotate.byteArray = rotatedData

        if (rotationDegrees != 180) {
            imageToRotate.height = width
            imageToRotate.width = height
        }
    }
}



