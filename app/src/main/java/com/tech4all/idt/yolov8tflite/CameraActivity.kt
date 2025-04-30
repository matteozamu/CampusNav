package com.tech4all.idt.yolov8tflite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tech4all.idt.MainActivity
import com.tech4all.idt.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.createBitmap

/**
 * CameraActivity manages the live camera feed, processes image frames
 * using a YOLOv8 TFLite model for object detection, and overlays results on screen.
 */
class CameraActivity : AppCompatActivity(), Detector.DetectorListener {

    // Set to true if using the front camera
    private val isFrontCamera = false

    // Camera-related components
    private lateinit var preview: Preview
    private lateinit var imageAnalyzer: ImageAnalysis
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    // Detector instance (YOLO model wrapper)
    private lateinit var detector: Detector

    // Background thread for image analysis
    private lateinit var cameraExecutor: ExecutorService

    // UI elements
    private lateinit var inferenceTime: TextView
    private lateinit var overlay: OverlayView
    private lateinit var viewFinder: PreviewView
    private lateinit var backButton: ImageButton

    /**
     * Called when the activity is created. Initializes UI, detector,
     * and sets up camera if permissions are granted.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)

        // Link UI elements
        inferenceTime = findViewById(R.id.inferenceTime)
        overlay = findViewById(R.id.overlay)
        viewFinder = findViewById(R.id.view_finder)
        backButton = findViewById(R.id.backButton)

        // Initialize and prepare the YOLO detector
        detector = Detector(baseContext, Constants.MODEL_PATH, Constants.LABELS_PATH, this)
        detector.setup()

        // Start camera if permissions are already granted
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            // Otherwise, request permission
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Use a single background thread for analyzing images
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Set up navigation back to the main screen
        setupBackButton()
    }

    /**
     * Attaches a click listener to navigate back to MainActivity.
     */
    private fun setupBackButton() {
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Close the camera activity
        }
    }

    /**
     * Starts the camera by binding use cases to the lifecycle.
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Save the camera provider instance
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Binds camera preview and image analysis to the lifecycle.
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val rotation = viewFinder.display.rotation

        // Select the back camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // Create a preview use case
        preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()

        // Create image analysis use case to process each frame
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 960)) // Avoid deprecated setTargetAspectRatio
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        // Set analyzer logic for each camera frame
        imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
            // Convert frame to Bitmap
            val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
            imageProxy.use {
                bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
            }

            // Apply rotation and flip if needed
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                }
            }

            // Final processed bitmap
            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            // Run detection on the bitmap
            detector.detect(rotatedBitmap)
        }

        // Unbind existing use cases
        cameraProvider.unbindAll()

        try {
            // Bind everything to the activity lifecycle
            camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Set preview surface
            preview.setSurfaceProvider(viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    /**
     * Checks if all required permissions are granted.
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Used for handling camera permission requests.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
    }

    /**
     * Cleans up detector and background thread when the activity is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        detector.clear()
        cameraExecutor.shutdown()
    }

    /**
     * Restarts camera when activity resumes.
     */
    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 10

        // Only camera permission required
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
        )
    }

    /**
     * Callback from Detector when no objects are detected.
     */
    override fun onEmptyDetect() {
        overlay.invalidate()
    }

    /**
     * Callback from Detector when detection is successful.
     * Updates overlay and shows inference time.
     *
     * @param boundingBoxes List of detected object boxes.
     * @param inferenceTime Time taken to run inference (in ms).
     */
    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        runOnUiThread {
            this.inferenceTime.text = getString(R.string.inference_time, inferenceTime)
            overlay.apply {
                setResults(boundingBoxes)
                invalidate() // Redraw overlay
            }
        }
    }
}
