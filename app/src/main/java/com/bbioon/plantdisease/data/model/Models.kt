package com.bbioon.plantdisease.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResult(
    @SerialName("plant_name") val plantName: String = "Unknown",
    @SerialName("scientific_name") val scientificName: String? = null,
    @SerialName("plant_type") val plantType: String = "Unknown",
    @SerialName("is_healthy") val isHealthy: Boolean = true,
    @SerialName("disease_name") val diseaseName: String? = null,
    @SerialName("scientific_disease_name") val scientificDiseaseName: String? = null,
    @SerialName("pathogen_type") val pathogenType: String? = null,
    val severity: String? = null,
    @SerialName("disease_stage") val diseaseStage: String? = null,
    @SerialName("spread_risk") val spreadRisk: String? = null,
    val symptoms: String? = null,
    val cause: String? = null,
    val description: String = "",
    val treatment: String? = null,
    val prevention: String? = null,
    @SerialName("favorable_conditions") val favorableConditions: String? = null,
    val notes: String? = null,
)

@Entity(tableName = "scans")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantName: String,
    val scientificName: String? = null,
    val plantType: String,
    val isHealthy: Boolean,
    val diseaseName: String?,
    val scientificDiseaseName: String? = null,
    val pathogenType: String? = null,
    val severity: String? = null,
    val diseaseStage: String? = null,
    val spreadRisk: String? = null,
    val symptoms: String? = null,
    val cause: String? = null,
    val description: String,
    val treatment: String?,
    val prevention: String? = null,
    val favorableConditions: String? = null,
    val notes: String? = null,
    val imageUri: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class ModelInfo(
    val name: String,
    val displayName: String = "",
    val description: String = "",
    val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable
data class ModelsResponse(
    val models: List<ModelInfo> = emptyList(),
)

@Serializable
data class ApiError(
    val error: ApiErrorDetail? = null,
)

@Serializable
data class ApiErrorDetail(
    val message: String = "",
)
