package com.example.expensetracker.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.components.PrimaryButton
import com.example.expensetracker.ui.components.ScreenPadding
import com.example.expensetracker.ui.theme.*

/** Covers the whole app while it's locked. Touches don't reach the screen underneath. */
@Composable
fun LockedScreen(message: String?, onUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Canvas)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding().padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Left empty for the mascot.
        Spacer(Modifier.size(150.dp))
        Spacer(Modifier.height(14.dp))
        Text("Locked", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(message ?: "Unlock with your fingerprint, face or screen lock.",
            fontSize = 13.sp, color = if (message != null) Pink else Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        PrimaryButton("Unlock", onUnlock, modifier = Modifier.width(200.dp))
    }
}
