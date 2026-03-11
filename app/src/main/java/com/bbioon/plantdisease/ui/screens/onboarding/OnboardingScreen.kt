package com.bbioon.plantdisease.ui.screens.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.data.local.PreferencesManager
import com.bbioon.plantdisease.ui.theme.*
import com.bbioon.plantdisease.util.LocaleHelper
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    prefs: PreferencesManager,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var selectedLang by remember { mutableStateOf("en") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp)
            .statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))

        // Welcome
        Text(
            text = stringResource(R.string.onboarding_welcome),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            fontSize = 16.sp,
            color = TextSecondary,
        )

        Spacer(Modifier.height(8.dp))

        // API Key Section
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Key, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.onboarding_api_key_title), fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Text(stringResource(R.string.onboarding_api_key_desc), fontSize = 14.sp, color = TextSecondary, lineHeight = 20.sp)

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            placeholder = { Text(stringResource(R.string.settings_api_key_placeholder), color = TextMuted) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = Border,
                cursorColor = Primary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = InputBg,
                unfocusedContainerColor = InputBg,
            ),
            singleLine = true,
        )

        TextButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
            },
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Link, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.onboarding_get_key), color = Link)
        }

        // Language Section
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Language, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.onboarding_language_title), fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            LangButton("English", selectedLang == "en", Modifier.weight(1f)) { selectedLang = "en" }
            LangButton("العربية", selectedLang == "ar", Modifier.weight(1f)) { selectedLang = "ar" }
        }

        Spacer(Modifier.weight(1f))

        // Get Started
        Button(
            onClick = {
                scope.launch {
                    if (apiKey.isNotBlank()) prefs.setApiKey(apiKey.trim())
                    prefs.setLanguage(selectedLang)
                    prefs.setFirstLaunchComplete()
                    LocaleHelper.setLocale(context, selectedLang)
                    onComplete()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text(stringResource(R.string.onboarding_get_started), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        TextButton(
            onClick = {
                scope.launch {
                    prefs.setFirstLaunchComplete()
                    onComplete()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_skip), color = TextMuted)
        }

        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun LangButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PrimaryMuted else InputBg)
            .border(
                width = 1.5.dp,
                color = if (selected) Primary else Border,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Primary else TextSecondary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}
