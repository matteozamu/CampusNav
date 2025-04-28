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
import java.io.File
import java.io.IOException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class GemmaActivity : AppCompatActivity() {

    private val serverUrl = "http://YOUR_COMPUTER_IP:PORT/upload" // <-- replace with your local API endpoint!

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
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
        val file = File(getRealPathFromUri(uri))
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "video",
                file.name,
                RequestBody.create("video/mp4".toMediaTypeOrNull(), file)
            )
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@GemmaActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("UPLOAD", "Upload error: ", e)
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
        val requestBody = FormBody.Builder()
            .add("question", question)
            .build()

        val request = Request.Builder()
            .url("$serverUrl/question") // assumes /question endpoint exists
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
                    Toast.makeText(this@GemmaActivity, "Answer: $answer", Toast.LENGTH_LONG).show()
                }
            }
        })
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
