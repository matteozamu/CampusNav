package com.tech4all.idt.gemma3

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
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tech4all.idt.R
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import android.view.View


/**
 * Activity responsible for interacting with the FastAPI server.
 * It allows the user to record or pick a video, upload it, and ask questions
 * related to the video content.
 */
class GemmaActivity : AppCompatActivity() {

    // Base URL of the FastAPI server
    private val serverBaseUrl = "http://192.168.1.140:8000" // 👈 Your personal server URL

    // UI components
    private lateinit var recordButton: Button
    private lateinit var pickButton: Button
    private lateinit var uploadButton: Button
    private lateinit var askButton: Button
    private lateinit var questionInput: EditText
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val chatMessages = mutableListOf<ChatMessage>()
    private val loadingSpinner: ProgressBar by lazy { findViewById(R.id.loadingSpinner) }
    // Holds the URI of the selected video
    private var selectedVideoUri: Uri? = null

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 123 // Request code for permissions
    }

    /**
     * Registers a callback for handling video selection (both camera and gallery).
     */
    private val videoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                selectedVideoUri = result.data?.data // Get video URI from the result
                Toast.makeText(this, "Video selected!", Toast.LENGTH_SHORT).show()
            }
        }

    /**
     * Called when the activity is created. Initializes the UI components
     * and sets up click listeners for buttons.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gemma)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        chatAdapter = ChatAdapter(chatMessages)
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = LinearLayoutManager(this)

        // Set up the action bar with a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true);
        onBackPressedDispatcher.addCallback(this) {
            // Qui il comportamento quando l’utente preme "Indietro"
            finish() // o fai qualcosa prima di uscire
        }

        // Initialize UI components
        recordButton = findViewById(R.id.recordButton)
        pickButton = findViewById(R.id.pickButton)
        uploadButton = findViewById(R.id.uploadButton)
        askButton = findViewById(R.id.askButton)
        questionInput = findViewById(R.id.questionInput)

        // Button listeners
        recordButton.setOnClickListener {
            checkAndRequestPermissions {
                openCameraToRecordVideo() // Open camera to record video
            }
        }

        pickButton.setOnClickListener {
            checkAndRequestPermissions {
                openVideoPicker() // Open gallery to pick a video
            }
        }

        uploadButton.setOnClickListener {
            selectedVideoUri?.let {
                uploadVideo(it) // Upload the selected video
            } ?: Toast.makeText(this, "No video selected!", Toast.LENGTH_SHORT).show()
        }

        askButton.setOnClickListener {
            val question = questionInput.text.toString().trim()
            if (question.isNotEmpty()) {
                questionInput.text.clear()
                sendQuestion(question)
            }
        }

    }

    private fun addMessageToChat(message: String, isUser: Boolean) {
        chatMessages.add(ChatMessage(message, isUser))
        chatAdapter.notifyItemInserted(chatMessages.size - 1)
        chatRecyclerView.scrollToPosition(chatMessages.size - 1)
    }

    /**
     * Checks and requests necessary permissions for camera and external storage access.
     * If permissions are granted, the specified action is performed.
     *
     * @param onGranted Action to be executed if permissions are granted.
     */
    private fun checkAndRequestPermissions(onGranted: () -> Unit) {
        val permissionsNeeded = mutableListOf(Manifest.permission.CAMERA)

        // Check permissions for video reading based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Request only the necessary permissions
        val permissionsToRequest = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        // If permissions are already granted, execute the action
        if (permissionsToRequest.isEmpty()) {
            onGranted()
        } else {
            // Otherwise, request the necessary permissions
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS_CODE)
        }
    }

    /**
     * Handles the result of the permission request.
     *
     * @param requestCode The request code passed to [requestPermissions].
     * @param permissions The requested permissions.
     * @param grantResults The grant results for the corresponding permissions.
     */
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

    /**
     * Opens the camera to record a video.
     */
    private fun openCameraToRecordVideo() {
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        videoPickerLauncher.launch(intent)
    }

    /**
     * Opens the gallery to pick a video.
     */
    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        videoPickerLauncher.launch(intent)
    }

    /**
     * Uploads the selected video to the server.
     *
     * @param uri URI of the selected video.
     */
    private fun uploadVideo(uri: Uri) {
        Toast.makeText(this, "Uploading video...", Toast.LENGTH_SHORT).show()

        try {
            val file = getFileFromUri(uri) // Convert URI to File

            // Log file details for debugging
            Log.d("UPLOAD", "File path: ${file.absolutePath}, exists: ${file.exists()}, length: ${file.length()}")

            // Check MIME type of the file
            val mimeType = getMimeType(uri)
            Log.d("UPLOAD", "Mime type: $mimeType")

            // Only upload if the file is a valid video
            if (mimeType?.startsWith("video/") == true) {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file", file.name, file.asRequestBody("video/mp4".toMediaTypeOrNull())
                    )
                    .build()

                val client = OkHttpClient.Builder()
                    .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)  // Connection timeout
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)    // Write timeout
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)     // Read timeout
                    .build()

                // Create and send POST request to upload video
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

                    // Handle the server response after uploading the video
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

    /**
     * Returns the MIME type for the specified URI.
     *
     * @param uri The URI of the file.
     * @return The MIME type of the file, or null if it cannot be determined.
     */
    private fun getMimeType(uri: Uri): String? {
        return contentResolver.getType(uri)
    }

    /**
     * Converts a URI to a File object by copying the content of the URI to a temporary file.
     *
     * @param uri The URI of the file.
     * @return The resulting File object.
     * @throws IOException If there is an error reading the URI or writing to the temporary file.
     */
    private fun getFileFromUri(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open input stream from URI")

        val tempFile = File.createTempFile("upload", ".mp4", cacheDir)
        tempFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }

        return tempFile
    }

    /**
     * Sends a question to the server for processing.
     *
     * @param question The question to send to the server.
     */
    private fun sendQuestion(question: String) {
        Toast.makeText(this, "Sending question...", Toast.LENGTH_SHORT).show()

        val client = OkHttpClient()

        val jsonBody = """
        {
            "question": "$question"
        }
    """.trimIndent()

        val mediaType = "application/json".toMediaTypeOrNull()
        val requestBody = jsonBody.toRequestBody(mediaType)

        Log.d("SEND_QUESTION", "Sending question JSON: $jsonBody")

        val request = Request.Builder()
            .url("$serverBaseUrl/ask_question")
            .post(requestBody)
            .build()

        loadingSpinner.visibility = View.VISIBLE
        addMessageToChat(question, isUser = true)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    addMessageToChat("Errore durante l'invio della domanda: ${e.message}", isUser = false)
                }
                Log.e("QUESTION", "Question sending error: ", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = response.body?.string()
                runOnUiThread {
                    loadingSpinner.visibility = View.GONE
                    if (response.isSuccessful) {
                        addMessageToChat(answer ?: "Nessuna risposta ricevuta.", isUser = false)
                    } else {
                        addMessageToChat("Errore dal server: ${response.message}", isUser = false)
                        Log.e("QUESTION", "Failed response: ${response.code} - $answer")
                    }
                }
            }
        })
    }


    /**
     * Displays a dialog showing the analysis summary of the uploaded video.
     *
     * @param summary The analysis summary to display in the dialog.
     */
    private fun showSummaryDialog(summary: String) {
        AlertDialog.Builder(this)
            .setTitle("Video Analysis Summary")
            .setMessage(summary)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Displays a dialog showing the answer to the question asked.
     *
     * @param answer The answer received from the server.
     */
    private fun showAnswerDialog(answer: String) {
        AlertDialog.Builder(this)
            .setTitle("Answer from Gemma3")
            .setMessage(answer)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Retrieves the real file path from the URI (useful for certain operations).
     *
     * @param uri The URI of the file.
     * @return The real file path as a string.
     * @throws IllegalArgumentException If the file path cannot be retrieved.
     */
    private fun getRealPathFromUri(uri: Uri): String {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            cursor.moveToFirst()
            return cursor.getString(columnIndex)
        }
        throw IllegalArgumentException("Unable to retrieve path from uri")
    }

    /**
     * Handle the back button press in the action bar.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish() // oppure usa onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
