package com.bbioon.plantdisease.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResult(
    @SerialName("plant_name") val plantName: String = "Unknown",
    @SerialName("plant_type") val plantType: String = "Unknown",
    @SerialName("is_healthy") val isHealthy: Boolean = true,
    @SerialName("disease_name") val diseaseName: String? = null,
    val description: String = "",
    val treatment: String? = null,
)

@Entity(tableName = "scans")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantName: String,
    val plantType: String,
    val isHealthy: Boolean,
    val diseaseName: String?,
    val description: String,
    val treatment: String?,
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
