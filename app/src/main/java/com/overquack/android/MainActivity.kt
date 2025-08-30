package com.overquack.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore

class MainActivity : AppCompatActivity() {

    private companion object {
        const val PICK_FILE_REQUEST_CODE = 123
        const val DEFAULT_IP = "10.10.5.1"
        val DEFAULT_PORTS = listOf(80, 8000, 8080)
        const val ENDPOINT_PATH = "/c2"
    }

    private val client = OkHttpClient()
    private lateinit var buttonConnect: Button
    private lateinit var buttonUpload: Button
    private lateinit var buttonRefresh: Button
    private lateinit var textStatus: TextView
    private lateinit var recyclerPayloads: RecyclerView
    private lateinit var adapter: PayloadAdapter

    private var baseUrl: String? = null
    private var separator: String = ""
    private val payloads = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupRecyclerView()
        setupClickListeners()
    }

    private fun initializeViews() {
        buttonConnect = findViewById(R.id.button_connect)
        buttonUpload = findViewById(R.id.button_upload)
        buttonRefresh = findViewById(R.id.button_refresh)
        textStatus = findViewById(R.id.text_status)
        recyclerPayloads = findViewById(R.id.recycler_payloads)
    }

    private fun setupRecyclerView() {
        adapter = PayloadAdapter(payloads,
            onItemClick = { payloadName ->
                showPayloadOptions(payloadName)
            }
        )
        recyclerPayloads.layoutManager = LinearLayoutManager(this)
        recyclerPayloads.adapter = adapter
    }

    private fun setupClickListeners() {
        buttonConnect.setOnClickListener {
            if (baseUrl == null) {
                connectToDevice()
            } else {
                disconnect()
            }
        }
        
        buttonUpload.setOnClickListener { 
            openFilePicker()
        }

        buttonRefresh.setOnClickListener {
            if (baseUrl != null) {
                loadPayloads()
            } else {
                toast("Please connect to device first")
            }
        }
    }

    private fun connectToDevice() {
        buttonConnect.isEnabled = false
        textStatus.text = "Connecting..."

        lifecycleScope.launch {
            val result = findActiveConnection()
            
            withContext(Dispatchers.Main) {
                if (result.first != null) {
                    baseUrl = result.first
                    separator = result.second
                    buttonConnect.text = "Disconnect"
                    buttonConnect.isEnabled = true
                    buttonRefresh.isEnabled = true
                    buttonUpload.isEnabled = true
                    textStatus.text = "Connected to ${result.first}"
                    toast("Connected successfully!")
                    loadPayloads()
                } else {
                    buttonConnect.isEnabled = true
                    textStatus.text = "Connection failed"
                    toast("Could not connect to device")
                }
            }
        }
    }

    private fun disconnect() {
        baseUrl = null
        separator = ""
        buttonConnect.text = "Connect to Device"
        buttonUpload.isEnabled = false
        buttonRefresh.isEnabled = false
        textStatus.text = "Disconnected"
        payloads.clear()
        adapter.notifyDataSetChanged()
        toast("Disconnected")
    }

    private suspend fun findActiveConnection(): Pair<String?, String> = withContext(Dispatchers.IO) {
        for (port in DEFAULT_PORTS) {
            val url = "http://$DEFAULT_IP:$port$ENDPOINT_PATH"
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.close()
                    // Get separator
                    val sepResponse = doPost(url, "SEP")
                    return@withContext Pair(url, sepResponse ?: "")
                }
                response.close()
            } catch (e: Exception) {
                // Continue to next port
            }
        }
        Pair(null, "")
    }

    private fun loadPayloads() {
        baseUrl?.let { url ->
            lifecycleScope.launch {
                val response = doPost(url, "LS")

                withContext(Dispatchers.Main) {
                    response?.let { responseText ->
                        // Handle both comma-separated AND newline-separated formats
                        val allItems = mutableListOf<String>()

                        // First try splitting by commas
                        if (responseText.contains(",")) {
                            allItems.addAll(responseText.split(","))
                        } else {
                            // Fallback to newline splitting
                            allItems.addAll(responseText.split("\n", "\r\n", "\\n"))
                        }

                        // Clean, filter only .oqs files
                        val oqsPayloads = allItems
                            .map { it.trim() }
                            .filter { line ->
                                line.endsWith(".oqs", ignoreCase = true) &&
                                        line.isNotEmpty() &&
                                        !line.startsWith("#") // Ignore comment lines
                            }

                        payloads.clear()
                        payloads.addAll(oqsPayloads)
                        adapter.notifyDataSetChanged()

                        textStatus.text = "Connected - Found ${oqsPayloads.size} .oqs payload(s)"
                    } ?: run {
                        payloads.clear()
                        adapter.notifyDataSetChanged()
                        textStatus.text = "Connected - Failed to load payloads"
                    }
                }
            }
        }
    }

    private fun showPayloadOptions(payloadName: String) {
        val options = arrayOf("Run", "Read Content", "Delete", "Download")
        
        AlertDialog.Builder(this)
            .setTitle("Payload: $payloadName")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> runPayload(payloadName)
                    1 -> readPayload(payloadName) 
                    2 -> confirmDeletePayload(payloadName)
                    3 -> downloadPayload(payloadName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runPayload(payloadName: String) {
        baseUrl?.let { url ->
            lifecycleScope.launch {
                val command = "RUN$separator$payloadName"
                val response = doPost(url, command)
                
                withContext(Dispatchers.Main) {
                    val message = if (response?.contains("success", true) == true || 
                                     response?.contains("executed", true) == true ||
                                     response?.isNotEmpty() == true) {
                        "✓ Payload '$payloadName' executed successfully"
                    } else {
                        "✗ Failed to execute payload: ${response ?: "Unknown error"}"
                    }
                    toast(message)
                }
            }
        }
    }

    private fun readPayload(payloadName: String) {
        baseUrl?.let { url ->
            lifecycleScope.launch {
                val command = "READ$separator$payloadName"
                val response = doPost(url, command)
                
                withContext(Dispatchers.Main) {
                    if (response?.isNotEmpty() == true) {
                        showPayloadContentDialog(payloadName, response)
                    } else {
                        toast("✗ Failed to read payload content")
                    }
                }
            }
        }
    }

    private fun confirmDeletePayload(payloadName: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Payload")
            .setMessage("Are you sure you want to delete '$payloadName'?\n\nThis action cannot be undone.\n\nNote: Ensure GPIO pin 5 is connected for write operations.")
            .setPositiveButton("Delete") { _, _ ->
                deletePayload(payloadName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePayload(payloadName: String) {
        baseUrl?.let { url ->
            lifecycleScope.launch {
                val command = "DELETE$separator$payloadName"
                val response = doPost(url, command)
                
                withContext(Dispatchers.Main) {
                    if (response?.contains("success", true) == true ||
                        response?.contains("deleted", true) == true ||
                        response?.isNotEmpty() == true) {
                        toast("✓ Payload '$payloadName' deleted successfully")
                        loadPayloads() // Refresh the list
                    } else {
                        toast("✗ Failed to delete payload: ${response ?: "Check GPIO pin 5 connection"}")
                    }
                }
            }
        }
    }

    private fun downloadPayload(payloadName: String) {
        baseUrl?.let { url ->
            lifecycleScope.launch {
                val command = "READ$separator$payloadName"
                val response = doPost(url, command)

                withContext(Dispatchers.Main) {
                    if (response != null && response.isNotEmpty()) {
                        savePayloadToDownloads(payloadName, response)
                    } else {
                        toast("Failed to download payload content.")
                    }
                }
            }
        }
    }

    private fun savePayloadToDownloads(filename: String, content: String) {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { outputStream ->
                outputStream?.write(content.toByteArray(Charsets.UTF_8))
            }
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            toast("Payload saved to Downloads as $filename")
        } else {
            toast("Failed to save payload to Downloads")
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream",
                "text/plain",
                "*/*"
            ))
        }
        
        val chooser = Intent.createChooser(intent, "Select .oqs payload file")
        startActivityForResult(chooser, PICK_FILE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            uploadPayload(uri)
        }
    }

    // ENHANCED UPLOAD WITH MEMORY CHECK (like Linux client)
    private fun uploadPayload(uri: Uri) {
        lifecycleScope.launch {
            try {
                val filename = getFileName(uri)
                
                if (!filename.endsWith(".oqs", ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        toast("Please select a .oqs file")
                    }
                    return@launch
                }
                
                // Step 1: Check free memory on device (like Linux client)
                baseUrl?.let { url ->
                    withContext(Dispatchers.Main) {
                        toast("Checking device memory...")
                    }
                    
                    val freeMemoryStr = doPost(url, "FREE_MEM")
                    if (freeMemoryStr == null) {
                        withContext(Dispatchers.Main) {
                            toast("✗ Failed to get free memory from device")
                        }
                        return@launch
                    }
                    
                    val freeMemory = try {
                        freeMemoryStr.trim().toInt()
                    } catch (e: NumberFormatException) {
                        withContext(Dispatchers.Main) {
                            toast("✗ Invalid memory response from device")
                        }
                        return@launch
                    }
                    
                    // Step 2: Calculate safe memory (75% of free memory, like Linux client)
                    val safeMemory = (freeMemory * 0.75).toInt()
                    
                    withContext(Dispatchers.Main) {
                        toast("Max file size: ${safeMemory / 1000} KB")
                    }
                    
                    // Step 3: Read file content and check size
                    val content = readFileContent(uri)
                    val fileSize = content.toByteArray().size
                    
                    if (fileSize > safeMemory) {
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("File Too Large")
                                .setMessage("File size (${fileSize / 1000} KB) exceeds safe upload limit (${safeMemory / 1000} KB).\n\nThis could cause device memory issues.")
                                .setPositiveButton("Upload Anyway") { _, _ ->
                                    // Proceed with upload anyway
                                    lifecycleScope.launch {
                                        performUpload(url, filename, content, fileSize, true)
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                        return@launch
                    }
                    
                    // Step 4: Proceed with safe upload
                    performUpload(url, filename, content, fileSize, false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    toast("Upload error: ${e.message}")
                }
            }
        }
    }

    private suspend fun performUpload(url: String, filename: String, content: String, fileSize: Int, isForced: Boolean) {
        withContext(Dispatchers.Main) {
            if (isForced) {
                toast("⚠️ Force uploading $filename (${fileSize / 1000} KB)...")
            } else {
                toast("Uploading $filename (${fileSize / 1000} KB)...")
            }
        }
        
        val uploadCommand = "WRITE$separator$filename$separator\n$content"
        
        // For large files (>50KB), use retry logic for better reliability
        val response = if (fileSize > 50000) {
            doPostWithRetry(url, uploadCommand, 3)
        } else {
            doPost(url, uploadCommand)
        }
        
        withContext(Dispatchers.Main) {
            if (response?.contains("success", true) == true ||
                response?.contains("uploaded", true) == true ||
                response?.contains("written", true) == true ||
                response?.isNotEmpty() == true) {
                toast("✓ Upload successful: $filename")
                loadPayloads() // Refresh payload list
            } else {
                toast("✗ Upload failed: ${response ?: "Unknown error"}")
            }
        }
    }

    // Enhanced file content reading with better error handling
    private suspend fun readFileContent(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charset.forName("UTF-8"))).use { reader ->
                    reader.readText()
                }
            } ?: throw IOException("Unable to open file stream")
        } catch (e: Exception) {
            throw IOException("Error reading file: ${e.message}")
        }
    }

    // Enhanced filename extraction (like Linux client's filepath.Base)
    private fun getFileName(uri: Uri): String {
        // First try to get display name from content resolver
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex >= 0) {
                    val displayName = it.getString(displayNameIndex)
                    if (displayName.isNotEmpty()) {
                        // Remove leading dot if present (like Linux client)
                        return if (displayName.startsWith(".")) {
                            displayName.substring(1)
                        } else {
                            displayName
                        }
                    }
                }
            }
        }
        
        // Fallback to URI path segment
        val pathSegment = uri.lastPathSegment ?: "payload.oqs"
        return if (pathSegment.endsWith(".oqs", ignoreCase = true)) {
            if (pathSegment.startsWith(".")) {
                pathSegment.substring(1)
            } else {
                pathSegment
            }
        } else {
            "payload.oqs"
        }
    }

    // Enhanced POST request with better timeout and retry logic
    private suspend fun doPost(url: String, data: String): String? = withContext(Dispatchers.IO) {
        try {
            val requestBody = data.toRequestBody("text/plain".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Connection", "keep-alive")
                .build()
                
            val response = client.newCall(request).execute()
            val result = response.body?.string()
            response.close()
            return@withContext result
            
        } catch (e: Exception) {
            return@withContext null
        }
    }

    // POST with retry logic for large uploads
    private suspend fun doPostWithRetry(url: String, data: String, maxAttempts: Int): String? = withContext(Dispatchers.IO) {
        var attempts = 0
        
        while (attempts < maxAttempts) {
            try {
                val result = doPost(url, data)
                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                // Continue to next attempt
            }
            
            attempts++
            if (attempts < maxAttempts) {
                // Wait before retry (exponential backoff)
                kotlinx.coroutines.delay(1000L * attempts)
                
                withContext(Dispatchers.Main) {
                    textStatus.text = "Retrying upload... (${attempts}/${maxAttempts})"
                }
            }
        }
        return@withContext null
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showPayloadContentDialog(payloadName: String, content: String) {
        AlertDialog.Builder(this)
            .setTitle("Payload Content: $payloadName")
            .setMessage(content)
            .setPositiveButton("OK", null)
            .show()
    }
}

class PayloadAdapter(
    private val payloads: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<PayloadAdapter.PayloadViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PayloadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_payload, parent, false)
        return PayloadViewHolder(view)
    }

    override fun onBindViewHolder(holder: PayloadViewHolder, position: Int) {
        holder.bind(payloads[position])
    }

    override fun getItemCount(): Int = payloads.size

    inner class PayloadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.text_payload_name)

        fun bind(payloadName: String) {
            textView.text = payloadName
            itemView.setOnClickListener {
                onItemClick(payloadName)
            }
        }
    }
}