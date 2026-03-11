package com.bbioon.plantdisease.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.model.AnalysisResult
import com.bbioon.plantdisease.ui.theme.*

@Composable
fun AnalysisResultCard(
    result: AnalysisResult,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardColor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Status badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (result.isHealthy) HealthyBg else DiseasedBg)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (result.isHealthy) stringResource(R.string.result_healthy)
                    else stringResource(R.string.result_diseased),
                    color = if (result.isHealthy) Healthy else Diseased,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }

        // Fields
        ResultRow(stringResource(R.string.result_plant_name), result.plantName)
        ResultRow(stringResource(R.string.result_plant_type), result.plantType)
        if (!result.diseaseName.isNullOrBlank()) {
            ResultRow(stringResource(R.string.result_disease), result.diseaseName, Diseased)
        }
        if (result.description.isNotBlank()) {
            ResultRow(stringResource(R.string.result_description), result.description)
        }
        if (!result.treatment.isNullOrBlank()) {
            ResultRow(stringResource(R.string.result_treatment), result.treatment, PrimaryLight)
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = TextPrimary,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted,
            letterSpacing = 0.2.sp,
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = valueColor,
            lineHeight = 22.sp,
        )
    }
}
