package com.bbioon.plantdisease.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.local.PreferencesManager
import com.bbioon.plantdisease.data.model.ModelInfo
import com.bbioon.plantdisease.data.remote.GoogleAIService
import com.bbioon.plantdisease.ui.theme.*
import com.bbioon.plantdisease.util.LocaleHelper
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefs: PreferencesManager,
    apiService: GoogleAIService,
    onSetupGuide: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(prefs.getApiKey() ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var currentLang by remember { mutableStateOf("en") }
    var models by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsFailed by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    // Load settings
    LaunchedEffect(Unit) {
        model = prefs.getModel()
        currentLang = prefs.getLanguage()
        if (apiKey.isNotBlank()) {
            modelsLoading = true
            try {
                models = apiService.fetchModels(apiKey)
                if (models.isEmpty()) modelsFailed = true
            } catch (_: Exception) {
                modelsFailed = true
            }
            modelsLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        )

        // API Key
        SectionLabel(stringResource(R.string.settings_api_key))
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text(stringResource(R.string.settings_api_key_placeholder), color = TextMuted) },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = outlinedFieldColors(),
                singleLine = true,
            )
            IconButton(
                onClick = { showKey = !showKey },
                modifier = Modifier
                    .size(48.dp)
                    .background(InputBg, RoundedCornerShape(12.dp))
                    .border(1.dp, Border, RoundedCornerShape(12.dp)),
            ) {
                Icon(
                    if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = TextSecondary,
                )
            }
        }
        Text(
            stringResource(R.string.settings_api_key_hint),
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )

        Spacer(Modifier.height(20.dp))

        // Model
        SectionLabel(stringResource(R.string.settings_model))
        Box(Modifier.padding(horizontal = 24.dp)) {
            when {
                modelsLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = Primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.settings_model_loading), color = TextMuted, fontSize = 14.sp)
                    }
                }
                models.isNotEmpty() && !modelsFailed -> {
                    Column {
                        OutlinedButton(
                            onClick = { showModelPicker = !showModelPicker },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                        ) {
                            Text(model, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                            Icon(
                                if (showModelPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextSecondary,
                            )
                        }
                        if (showModelPicker) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Surface)
                                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                                    .heightIn(max = 250.dp),
                            ) {
                                models.forEach { m ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { model = m.name; showModelPicker = false }
                                            .background(if (m.name == model) PrimaryMuted else Surface)
                                            .padding(12.dp),
                                    ) {
                                        Column {
                                            Text(m.displayName.ifBlank { m.name }, color = if (m.name == model) Primary else TextPrimary, fontWeight = FontWeight.Medium)
                                            Text(m.name, fontSize = 12.sp, color = TextMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Column {
                        if (modelsFailed) {
                            Text(stringResource(R.string.settings_model_failed), fontSize = 12.sp, color = Warning, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            placeholder = { Text(stringResource(R.string.settings_model_placeholder), color = TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = outlinedFieldColors(),
                            singleLine = true,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Language
        SectionLabel(stringResource(R.string.settings_language))
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LangChip("English", currentLang == "en", Modifier.weight(1f)) {
                if (currentLang != "en") showLanguageDialog(context, "en", prefs, scope)
            }
            LangChip("العربية", currentLang == "ar", Modifier.weight(1f)) {
                if (currentLang != "ar") showLanguageDialog(context, "ar", prefs, scope)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Save
        Button(
            onClick = {
                scope.launch {
                    prefs.setApiKey(apiKey.trim())
                    prefs.setModel(model.trim().ifBlank { PreferencesManager.DEFAULT_MODEL })
                    Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.settings_save), fontWeight = FontWeight.SemiBold)
        }

        // Setup guide
        TextButton(
            onClick = onSetupGuide,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Default.MenuBook, contentDescription = null, tint = Link, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.settings_setup_guide), color = Link)
        }

        // About
        HorizontalDivider(color = Border, modifier = Modifier.padding(horizontal = 24.dp))
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.labelMedium)
            Text("${stringResource(R.string.settings_version)} 1.0.0", fontSize = 14.sp, color = TextMuted)
            Text(stringResource(R.string.settings_made_by), fontSize = 14.sp, color = Link)
        }
    }
}

private fun showLanguageDialog(
    context: android.content.Context,
    lang: String,
    prefs: PreferencesManager,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    android.app.AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.settings_restart_required))
        .setMessage(context.getString(R.string.settings_restart_confirm))
        .setPositiveButton(context.getString(R.string.settings_confirm)) { _, _ ->
            scope.launch {
                prefs.setLanguage(lang)
                LocaleHelper.setLocale(context, lang)
                // Restart activity
                (context as? Activity)?.let { activity ->
                    val intent = activity.intent
                    activity.finish()
                    context.startActivity(intent)
                }
            }
        }
        .setNegativeButton(context.getString(R.string.cancel), null)
        .show()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        letterSpacing = 0.2.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun LangChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PrimaryMuted else InputBg)
            .border(1.5.dp, if (selected) Primary else Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Primary else TextSecondary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Border,
    cursorColor = Primary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = InputBg,
    unfocusedContainerColor = InputBg,
)
