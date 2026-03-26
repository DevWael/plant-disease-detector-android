package com.bbioon.plantdisease.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.model.AnalysisResult
import com.bbioon.plantdisease.ui.theme.*

// Severity colors
private val SeverityLow = Color(0xFF4CAF50)
private val SeverityLowBg = Color(0x1A4CAF50)
private val SeverityModerate = Color(0xFFFFA000)
private val SeverityModerateBg = Color(0x1AFFA000)
private val SeveritySevere = Color(0xFFFF6D00)
private val SeveritySevereBg = Color(0x1AFF6D00)
private val SeverityCritical = Color(0xFFD32F2F)
private val SeverityCriticalBg = Color(0x1AD32F2F)

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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status badges row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Healthy / Diseased badge
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

            // Severity badge
            if (!result.severity.isNullOrBlank()) {
                val (severityColor, severityBg) = getSeverityColors(result.severity)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(severityBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = result.severity,
                        color = severityColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // ─── Plant Information ───────────────────────
        SectionHeader(stringResource(R.string.result_section_plant_info), "🌱")

        ResultRow(stringResource(R.string.result_plant_name), result.plantName)
        if (!result.scientificName.isNullOrBlank()) {
            ResultRow(
                stringResource(R.string.result_scientific_name),
                result.scientificName,
                valueStyle = FontStyle.Italic,
            )
        }
        ResultRow(stringResource(R.string.result_plant_type), result.plantType)

        // ─── Diagnosis (if diseased) ────────────────
        if (!result.isHealthy) {
            SectionDivider()
            SectionHeader(stringResource(R.string.result_section_diagnosis), "🔬")

            if (!result.diseaseName.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_disease), result.diseaseName, Diseased)
            }
            if (!result.scientificDiseaseName.isNullOrBlank()) {
                ResultRow(
                    stringResource(R.string.result_scientific_disease_name),
                    result.scientificDiseaseName,
                    valueStyle = FontStyle.Italic,
                )
            }
            if (!result.pathogenType.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_pathogen_type), result.pathogenType)
            }
            if (!result.diseaseStage.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_disease_stage), result.diseaseStage)
            }
            if (!result.symptoms.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_symptoms), result.symptoms)
            }
            if (!result.cause.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_cause), result.cause)
            }
            if (!result.spreadRisk.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_spread_risk), result.spreadRisk)
            }
            if (!result.favorableConditions.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_favorable_conditions), result.favorableConditions)
            }
        }

        // Description (always shown)
        if (result.description.isNotBlank()) {
            SectionDivider()
            ResultRow(stringResource(R.string.result_description), result.description)
        }

        // ─── Treatment & Prevention ─────────────────
        if (!result.treatment.isNullOrBlank() || !result.prevention.isNullOrBlank()) {
            SectionDivider()
            SectionHeader(stringResource(R.string.result_section_treatment), "💊")

            if (!result.treatment.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_treatment), result.treatment, PrimaryLight)
            }
            if (!result.prevention.isNullOrBlank()) {
                ResultRow(stringResource(R.string.result_prevention), result.prevention)
            }
        }

        // Professional notes (inline at the bottom)
        if (!result.notes.isNullOrBlank()) {
            SectionDivider()
            ResultRow(stringResource(R.string.result_notes), result.notes, TextMuted)
        }
    }
}

@Composable
private fun SectionHeader(title: String, emoji: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = TextMuted.copy(alpha = 0.2f),
        thickness = 1.dp,
    )
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    valueStyle: FontStyle = FontStyle.Normal,
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
            fontStyle = valueStyle,
        )
    }
}

private fun getSeverityColors(severity: String): Pair<Color, Color> {
    val lower = severity.lowercase()
    return when {
        lower.contains("low") || lower.contains("منخفضة") -> SeverityLow to SeverityLowBg
        lower.contains("moderate") || lower.contains("متوسطة") -> SeverityModerate to SeverityModerateBg
        lower.contains("severe") || lower.contains("شديدة") -> SeveritySevere to SeveritySevereBg
        lower.contains("critical") || lower.contains("حرجة") -> SeverityCritical to SeverityCriticalBg
        else -> TextSecondary to CardColor
    }
}
