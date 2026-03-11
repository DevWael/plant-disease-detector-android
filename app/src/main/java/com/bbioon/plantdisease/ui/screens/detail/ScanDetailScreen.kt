package com.bbioon.plantdisease.ui.screens.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.local.AppDatabase
import com.bbioon.plantdisease.data.model.AnalysisResult
import com.bbioon.plantdisease.data.model.ScanRecord
import com.bbioon.plantdisease.ui.components.AnalysisResultCard
import com.bbioon.plantdisease.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDetailScreen(
    scanId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    var scan by remember { mutableStateOf<ScanRecord?>(null) }

    LaunchedEffect(scanId) {
        scan = db.scanDao().getScanById(scanId)
    }

    val currentScan = scan
    if (currentScan == null) {
        Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            android.app.AlertDialog.Builder(context)
                                .setMessage(context.getString(R.string.history_delete_confirm))
                                .setPositiveButton(context.getString(R.string.delete)) { _, _ ->
                                    scope.launch {
                                        db.scanDao().deleteById(scanId)
                                        onBack()
                                    }
                                }
                                .setNegativeButton(context.getString(R.string.cancel), null)
                                .show()
                        },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Danger)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Image
            AsyncImage(
                model = currentScan.imageUri,
                contentDescription = currentScan.plantName,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )

            // Result card
            AnalysisResultCard(
                result = AnalysisResult(
                    plantName = currentScan.plantName,
                    plantType = currentScan.plantType,
                    isHealthy = currentScan.isHealthy,
                    diseaseName = currentScan.diseaseName,
                    description = currentScan.description,
                    treatment = currentScan.treatment,
                ),
            )

            // Date
            Text(
                text = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(currentScan.createdAt)),
                fontSize = 14.sp,
                color = TextMuted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
