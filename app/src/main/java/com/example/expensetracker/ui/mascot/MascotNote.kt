package com.example.expensetracker.ui.mascot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.expensetracker.ui.theme.Paper

/** A boxed note with her beside it. Callers leave it out when she is hidden. */
@Composable
fun MascotNote(mood: MascotMood, modifier: Modifier = Modifier, background: Color = Paper, text: @Composable ColumnScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(background).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Mascot(mood, Modifier.width(46.dp))
        Column(Modifier.weight(1f), content = text)
    }
}
