package com.bbioon.plantdisease.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.model.ScanRecord
import com.bbioon.plantdisease.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScanCard(
    scan: ScanRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        AsyncImage(
            model = scan.imageUri,
            contentDescription = scan.plantName,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )

        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = scan.plantName,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = scan.plantType,
                fontSize = 14.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatDate(scan.createdAt),
                fontSize = 12.sp,
                color = TextMuted,
            )
        }

        // Status badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (scan.isHealthy) HealthyBg else DiseasedBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = if (scan.isHealthy) stringResource(R.string.result_healthy)
                else stringResource(R.string.result_diseased),
                color = if (scan.isHealthy) Healthy else Diseased,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
