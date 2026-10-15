package com.example.expensetracker.ui.screens.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.*

/** Covers the whole app while it's locked. Touches don't reach the screen underneath. */
@Composable
fun LockedScreen(message: String?, onUnlock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Canvas)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Paper), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Jade, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("MyMoney is locked", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 28.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(message ?: "Unlock with your fingerprint, face or screen lock.",
            fontSize = 14.sp, color = if (message != null) Coral else Muted, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Jade)
                .clickable(role = Role.Button, onClick = onUnlock).padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) { Text("Unlock", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Deep) }
    }
}
