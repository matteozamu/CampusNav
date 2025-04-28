package com.tech4all.idt

import android.Manifest
import android.app.Activity
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class GemmaActivity : AppCompatActivity() {

    // UPDATE your server IP and port here:
    private val serverBaseUrl = "http://YOUR_COMPUTER_IP:8000"

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
        val file = copyUriToTempFile(uri)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", // <- MUST match FastAPI `file: UploadFile`
                file.name,
                file.asRequestBody("video/mp4".toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$serverBaseUrl/upload_video")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@GemmaActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@GemmaActivity, "Video uploaded successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@GemmaActivity, "Upload failed: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun sendQuestion(question: String) {
        val client = OkHttpClient()

        val jsonObject = JSONObject()
        jsonObject.put("question", question)

        val requestBody = jsonObject.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$serverBaseUrl/ask_question")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@GemmaActivity, "Question sending failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@GemmaActivity, "Answer: $answer", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@GemmaActivity, "Failed to get answer: ${response.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun copyUriToTempFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream from URI")
        val tempFile = File.createTempFile("upload_", ".mp4", cacheDir)
        val outputStream = FileOutputStream(tempFile)

        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()

        return tempFile
    }
}
