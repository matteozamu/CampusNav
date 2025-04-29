package com.tech4all.idt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class GemmaActivity : AppCompatActivity() {

    private val serverBaseUrl = "http://192.168.1.140:8000" // 👈 Your new updated server URL

    private lateinit var recordButton: Button
    private lateinit var pickButton: Button
    private lateinit var uploadButton: Button
    private lateinit var askButton: Button
    private lateinit var questionInput: EditText

    private var selectedVideoUri: Uri? = null

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 123
    }

    private val videoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedVideoUri = result.data?.data
                Toast.makeText(this, "Video selected!", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gemma)

        recordButton = findViewById(R.id.recordButton)
        pickButton = findViewById(R.id.pickButton)
        uploadButton = findViewById(R.id.uploadButton)
        askButton = findViewById(R.id.askButton)
        questionInput = findViewById(R.id.questionInput)

        recordButton.setOnClickListener {
            checkAndRequestPermissions {
                openCameraToRecordVideo()
            }
        }

        pickButton.setOnClickListener {
            checkAndRequestPermissions {
                openVideoPicker()
            }
        }

        uploadButton.setOnClickListener {
            selectedVideoUri?.let {
                uploadVideo(it)
            } ?: Toast.makeText(this, "No video selected!", Toast.LENGTH_SHORT).show()
        }

        askButton.setOnClickListener {
            val question = questionInput.text.toString()
            if (question.isNotBlank()) {
                sendQuestion(question)
            } else {
                Toast.makeText(this, "Please enter a question!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions(onGranted: () -> Unit) {
        val permissionsNeeded = mutableListOf(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val permissionsToRequest = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            onGranted()
        } else {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_PERMISSIONS_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions required to proceed.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openCameraToRecordVideo() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        videoPickerLauncher.launch(intent)
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        videoPickerLauncher.launch(intent)
    }

    private fun uploadVideo(uri: Uri) {
        Toast.makeText(this, "Uploading video...", Toast.LENGTH_SHORT).show()

        try {
            val file = getFileFromUri(uri)

            Log.d("UPLOAD", "File path: ${file.absolutePath}, exists: ${file.exists()}, length: ${file.length()}")

            // Verifica MIME type del file
            val mimeType = getMimeType(uri)
            Log.d("UPLOAD", "Mime type: $mimeType")

            if (mimeType?.startsWith("video/") == true) {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file",
                        file.name,
                        file.asRequestBody("video/mp4".toMediaTypeOrNull())
                    )
                    .build()

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)  // Timeout di connessione
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)    // Timeout per scrittura
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)     // Timeout per lettura
                    .build()

                val request = Request.Builder()
                    .url("$serverBaseUrl/upload_video")
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        runOnUiThread {
                            Toast.makeText(this@GemmaActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                        Log.e("UPLOAD", "Upload error: ", e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        runOnUiThread {
                            if (response.isSuccessful) {
                                showSummaryDialog(responseBody ?: "No summary received.")
                            } else {
                                Toast.makeText(this@GemmaActivity, "Upload failed: ${response.message}", Toast.LENGTH_LONG).show()
                                Log.e("UPLOAD", "Response error: ${response.code} - $responseBody")
                            }
                        }
                    }
                })
            } else {
                Toast.makeText(this, "Invalid file type. Please select a video file.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error preparing file: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("UPLOAD", "File preparation error: ", e)
        }
    }

    private fun getMimeType(uri: Uri): String? {
        return contentResolver.getType(uri)
    }

    private fun getFileFromUri(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open input stream from URI")

        val tempFile = File.createTempFile("upload", ".mp4", cacheDir)
        tempFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }

        return tempFile
    }

    private fun sendQuestion(question: String) {
        Toast.makeText(this, "Sending question...", Toast.LENGTH_SHORT).show()

        val client = OkHttpClient()

        // Crea il corpo della richiesta in formato JSON
        val jsonBody = """
        {
            "question": "$question"
        }
    """

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = RequestBody.create(mediaType, jsonBody)

        Log.d("SEND_QUESTION", "Sending question JSON: $jsonBody")

        val request = Request.Builder()
            .url("$serverBaseUrl/ask_question")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@GemmaActivity, "Question sending failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("QUESTION", "Question sending error: ", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        showAnswerDialog(answer ?: "No answer received.")
                    } else {
                        Toast.makeText(this@GemmaActivity, "Question failed: ${response.message}", Toast.LENGTH_LONG).show()
                        Log.e("QUESTION", "Failed response: ${response.code} - $answer")
                    }
                }
            }
        })
    }



    private fun showSummaryDialog(summary: String) {
        AlertDialog.Builder(this)
            .setTitle("Video Analysis Summary")
            .setMessage(summary)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAnswerDialog(answer: String) {
        AlertDialog.Builder(this)
            .setTitle("Answer from Gemma3")
            .setMessage(answer)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun getRealPathFromUri(uri: Uri): String {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            cursor.moveToFirst()
            return cursor.getString(columnIndex)
        }
        throw IllegalArgumentException("Unable to retrieve path from uri")
    }
}
