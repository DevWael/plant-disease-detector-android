package com.bbioon.plantdisease.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bbioon.plantdisease.R
import com.bbioon.plantdisease.ui.theme.*

@Composable
fun LoadingOverlay(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Overlay),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .background(SurfaceElevated, RoundedCornerShape(16.dp))
                .padding(32.dp),
        ) {
            CircularProgressIndicator(
                color = Primary,
                strokeWidth = 3.dp,
            )
            Text(
                text = stringResource(R.string.scanner_analyzing),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.alpha(alpha),
            )
            TextButton(onClick = onCancel) {
                Text(
                    text = stringResource(R.string.scanner_cancel),
                    color = Danger,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
