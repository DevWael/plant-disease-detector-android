package com.bbioon.plantdisease.ui.screens.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.local.AppDatabase
import com.bbioon.plantdisease.data.local.PreferencesManager
import com.bbioon.plantdisease.data.model.AnalysisResult
import com.bbioon.plantdisease.data.model.ScanRecord
import com.bbioon.plantdisease.data.remote.GoogleAIService
import com.bbioon.plantdisease.ui.components.AnalysisResultCard
import com.bbioon.plantdisease.ui.components.LoadingOverlay
import com.bbioon.plantdisease.ui.components.PrinterBottomSheet
import com.bbioon.plantdisease.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@Composable
fun ScannerScreen(
    prefs: PreferencesManager,
    apiService: GoogleAIService,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var base64Image by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<AnalysisResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPrintSheet by remember { mutableStateOf(false) }

    // Persistent scans directory for saved images
    val scansDir = remember {
        File(context.filesDir, "scans").also { it.mkdirs() }
    }

    // Copy a content URI to persistent app storage and return the local file URI
    fun copyToLocalStorage(sourceUri: Uri): Uri {
        val destFile = File(scansDir, "scan_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Cannot read image")
        return Uri.fromFile(destFile)
    }

    // Create a unique temp file URI for camera capture
    fun createTempUri(): Uri {
        val tempFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capturedUri = pendingCameraUri
        if (success && capturedUri != null) {
            // Copy camera capture to persistent storage
            scope.launch {
                try {
                    val persistedUri = withContext(Dispatchers.IO) {
                        copyToLocalStorage(capturedUri)
                    }
                    imageUri = persistedUri
                    processAndAnalyze(context, persistedUri, prefs, apiService, scope,
                        onBase64 = { base64Image = it },
                        onLoading = { isLoading = it },
                        onResult = { result = it },
                        onError = { error = it },
                        onJob = { analysisJob = it },
                    )
                } catch (e: Exception) {
                    error = e.message ?: context.getString(R.string.error_generic)
                }
            }
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            // Copy gallery image to persistent app storage immediately
            // so the URI remains valid after the temp content grant expires
            scope.launch {
                try {
                    val persistedUri = withContext(Dispatchers.IO) {
                        copyToLocalStorage(it)
                    }
                    imageUri = persistedUri
                    processAndAnalyze(context, persistedUri, prefs, apiService, scope,
                        onBase64 = { b -> base64Image = b },
                        onLoading = { l -> isLoading = l },
                        onResult = { r -> result = r },
                        onError = { e -> error = e },
                        onJob = { j -> analysisJob = j },
                    )
                } catch (e: Exception) {
                    error = e.message ?: context.getString(R.string.error_generic)
                }
            }
        }
    }

    // Camera permission
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createTempUri()
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, context.getString(R.string.error_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.scanner_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            // Image capture buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) },
                    modifier = Modifier.weight(1f).height(90.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    enabled = !isLoading,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text(stringResource(R.string.scanner_take_photo), fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f).height(90.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.5.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    enabled = !isLoading,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text(stringResource(R.string.scanner_choose_gallery), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Preview image
            imageUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            // Error
            error?.let { msg ->
                Text(msg, color = Danger, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }

            // Result
            result?.let { analysisResult ->
                AnalysisResultCard(result = analysisResult)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            imageUri = null; result = null; error = null; base64Image = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scanner_scan_another))
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val scan = ScanRecord(
                                    plantName = analysisResult.plantName,
                                    plantType = analysisResult.plantType,
                                    isHealthy = analysisResult.isHealthy,
                                    diseaseName = analysisResult.diseaseName,
                                    description = analysisResult.description,
                                    treatment = analysisResult.treatment,
                                    imageUri = imageUri.toString(),
                                )
                                db.scanDao().insert(scan)
                                Toast.makeText(context, context.getString(R.string.scanner_saved), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scanner_save))
                    }
                }

                // Print button
                OutlinedButton(
                    onClick = { showPrintSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.print_title))
                }

                // Print bottom sheet
                if (showPrintSheet && imageUri != null) {
                    val printScan = ScanRecord(
                        plantName = analysisResult.plantName,
                        plantType = analysisResult.plantType,
                        isHealthy = analysisResult.isHealthy,
                        diseaseName = analysisResult.diseaseName,
                        description = analysisResult.description,
                        treatment = analysisResult.treatment,
                        imageUri = imageUri.toString(),
                    )
                    PrinterBottomSheet(
                        scan = printScan,
                        onDismiss = { showPrintSheet = false },
                    )
                }
            }

            // Empty state
            if (imageUri == null && result == null) {
                Spacer(Modifier.height(48.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🌿", fontSize = 48.sp)
                    Text(stringResource(R.string.scanner_empty_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                    Text(stringResource(R.string.scanner_empty_desc), fontSize = 14.sp, color = TextMuted, textAlign = TextAlign.Center)
                }
            }
        }

        // Loading overlay
        if (isLoading) {
            LoadingOverlay(onCancel = { analysisJob?.cancel(); isLoading = false })
        }
    }
}

private fun processAndAnalyze(
    context: android.content.Context,
    uri: Uri,
    prefs: PreferencesManager,
    apiService: GoogleAIService,
    scope: kotlinx.coroutines.CoroutineScope,
    onBase64: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    onResult: (AnalysisResult?) -> Unit,
    onError: (String?) -> Unit,
    onJob: (Job) -> Unit,
) {
    val job = scope.launch {
        onLoading(true)
        onError(null)
        onResult(null)

        try {
            val base64 = withContext(Dispatchers.IO) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot read image")
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                // Resize
                val maxDim = 1024
                val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
                val resized = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                } else bitmap

                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 70, baos)

                if (baos.size() > 5 * 1024 * 1024) {
                    baos.reset()
                    resized.compress(Bitmap.CompressFormat.JPEG, 40, baos)
                }

                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }

            onBase64(base64)

            val apiKey = prefs.getApiKey()
            if (apiKey.isNullOrBlank()) {
                onError(context.getString(R.string.error_no_api_key))
                onLoading(false)
                return@launch
            }

            val model = prefs.getModel()
            val language = prefs.getLanguage()
            val result = apiService.analyzeImage(base64, language, model, apiKey)
            onResult(result)
        } catch (e: Exception) {
            val msg = when (e.message) {
                "RATE_LIMIT" -> context.getString(R.string.error_rate_limit)
                "INVALID_API_KEY" -> context.getString(R.string.error_invalid_api_key)
                "TIMEOUT" -> context.getString(R.string.error_timeout)
                "PARSE_ERROR" -> context.getString(R.string.error_parse)
                else -> e.message ?: context.getString(R.string.error_generic)
            }
            onError(msg)
        } finally {
            onLoading(false)
        }
    }
    onJob(job)
}
