package com.bbioon.plantdisease.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.local.AppDatabase
import com.bbioon.plantdisease.data.model.ScanRecord
import com.bbioon.plantdisease.ui.components.ScanCard
import com.bbioon.plantdisease.ui.theme.*

@Composable
fun HistoryScreen(
    onScanClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scans by db.scanDao().observeAll().collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        )

        if (scans.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("📋", fontSize = 48.sp)
                    Text(
                        stringResource(R.string.history_empty),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                    )
                    Text(
                        stringResource(R.string.history_empty_desc),
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(scans, key = { it.id }) { scan ->
                    ScanCard(scan = scan, onClick = { onScanClick(scan.id) })
                }
                item { Spacer(Modifier.height(80.dp)) } // Bottom padding for nav bar
            }
        }
    }
}
