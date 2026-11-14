package com.example.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.expensetracker.ui.theme.Deep
import com.example.expensetracker.ui.theme.Field
import com.example.expensetracker.ui.theme.Gold
import com.example.expensetracker.ui.theme.Hairline
import com.example.expensetracker.ui.theme.HairlineSoft
import com.example.expensetracker.ui.theme.Ink
import com.example.expensetracker.ui.theme.Leaf
import com.example.expensetracker.ui.theme.Muted
import com.example.expensetracker.ui.theme.Orange
import com.example.expensetracker.ui.theme.Paper
import com.example.expensetracker.ui.theme.Pink
import com.example.expensetracker.ui.theme.Whisper

/* The Marigold design system: the pieces every screen is built from. */

val ScreenPadding = 20.dp
val CardShape = RoundedCornerShape(22.dp)
val FieldShape = RoundedCornerShape(14.dp)
val ButtonShape = RoundedCornerShape(16.dp)

/**
 * For a screen's own Scaffold. The app's Scaffold has already padded for the system bars, and a
 * nested one would pad for them a second time, pushing its title lower than other screens'.
 */
val NoInsets = WindowInsets(0, 0, 0, 0)

/** Padding for a card that holds rows, which bring their own 12dp above and below. */
val RowCardPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)

/** Paper on a page, Field inside a bottom sheet (which is itself Paper). */
val LocalCardColor = compositionLocalOf { Paper }

/**
 * The colour of a budget row's dot and bar: [Pink] over the limit, [Orange] exactly at it, [Gold]
 * from the user's alert level ([alertPercent] of the limit), otherwise [Leaf].
 */
fun budgetTone(spent: Double, limit: Double, alertPercent: Int): Color = when {
    spent > limit -> Pink
    spent == limit -> Orange
    spent >= limit * alertPercent / 100 -> Gold
    else -> Leaf
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) HeaderIcon(Icons.Default.ChevronLeft, "Back", onClick = onBack, alignStart = true)
        Text(
            title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/** A plain 22dp icon with a 40dp touch area, for [ScreenHeader]. */
@Composable
fun HeaderIcon(
    icon: ImageVector, contentDescription: String?, onClick: () -> Unit,
    enabled: Boolean = true, tint: Color = Ink, alignStart: Boolean = false, badge: Boolean = false
) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.Center
    ) {
        Box {
            Icon(icon, contentDescription, tint = if (enabled) tint else Whisper, modifier = Modifier.size(22.dp))
            if (badge) Box(Modifier.size(8.dp).align(Alignment.TopEnd).clip(CircleShape).background(Pink))
        }
    }
}

@Composable
fun SectionLabel(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Muted)
        if (action != null) {
            Text(
                action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Gold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onAction)
            )
        }
    }
}

@Composable
fun MCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(LocalCardColor.current)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HairlineSoft))
}

@Composable
fun ToneDot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** A 3dp bar in [accent], the title over its subtitle, then [trailing]; [actions] sit after that. */
@Composable
fun AccentRow(
    accent: Color,
    title: String,
    subtitle: String,
    trailing: String,
    subtitleColor: Color = Muted,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailingColor: Color = Ink,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(3.dp, 32.dp).clip(RoundedCornerShape(3.dp)).background(accent))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(title, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, fontSize = 13.sp, color = subtitleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing.isNotEmpty()) Text(trailing, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = trailingColor, maxLines = 1)
        actions()
    }
}

@Composable
fun MenuRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = { Icon(Icons.Default.ChevronRight, null, tint = Ink, modifier = Modifier.size(20.dp)) }
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 15.sp, color = Ink)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 13.sp, color = Muted)
        }
        trailing()
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color = Gold, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Hairline)) {
        val shown = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        if (shown > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(CircleShape).background(color))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Deep else Ink,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(Gold) else Modifier.border(1.dp, Hairline, CircleShape))
            // Said only for the chosen one: chips are also plain actions ("Remind me"), which aren't "not selected"
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun SegTabs(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 6.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        options.forEachIndexed { i, option ->
            val selected = i == selectedIndex
            Text(
                option,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Ink else Muted,
                modifier = Modifier
                    .selectable(selected = selected, role = Role.Tab) { onSelect(i) }
                    .padding(bottom = 4.dp)
                    .drawBehind {
                        if (selected) drawLine(Gold, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                    }
                    .padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    ButtonBox(text, onClick, enabled, if (enabled) Gold else Hairline, if (enabled) Deep else Muted, modifier)
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ButtonBox(text, onClick, true, Field, Ink, modifier)
}

@Composable
private fun ButtonBox(text: String, onClick: () -> Unit, enabled: Boolean, fill: Color, textColor: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(ButtonShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, color: Color = Gold, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) color else Muted,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

@Composable
fun FieldBox(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(Field)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (label.isNotEmpty()) Text(label, fontSize = 13.sp, color = Muted)
        content()
    }
}

/**
 * A large amount on one line. With paise ("₹1,00,100.74") it can be wider than the screen at full
 * size, so the text steps down until it fits.
 */
@Composable
fun BigNumber(text: String, size: Int = 44, color: Color = Ink, modifier: Modifier = Modifier) {
    var scale by remember(text, size) { mutableStateOf(1f) }
    Text(
        text,
        fontSize = (size * scale).sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        onTextLayout = { if (it.didOverflowWidth && scale > 0.4f) scale *= 0.92f },
        modifier = modifier
    )
}

/** For an OutlinedTextField: a Field-coloured box with no border until it has focus. Use with [FieldShape]. */
@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Field, unfocusedContainerColor = Field, errorContainerColor = Field, disabledContainerColor = Field,
    focusedBorderColor = Gold, unfocusedBorderColor = Color.Transparent, disabledBorderColor = Color.Transparent, errorBorderColor = Pink,
    focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Gold,
    focusedLabelColor = Muted, unfocusedLabelColor = Muted, errorLabelColor = Pink,
    focusedPlaceholderColor = Whisper, unfocusedPlaceholderColor = Whisper,
    errorSupportingTextColor = Pink, focusedSupportingTextColor = Muted, unfocusedSupportingTextColor = Muted
)

@Composable
fun switchColors() = SwitchDefaults.colors(
    checkedTrackColor = Gold, checkedThumbColor = Deep, checkedBorderColor = Gold,
    uncheckedTrackColor = Hairline, uncheckedThumbColor = Muted, uncheckedBorderColor = Hairline
)

/** The app's bottom sheet: Paper with a grabber and 20dp padding. Cards inside it turn Field-coloured. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Paper,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(36.dp, 4.dp).clip(CircleShape).background(Hairline))
        }
    ) {
        CompositionLocalProvider(LocalCardColor provides Field) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp),
                content = content
            )
        }
    }
}

@Composable
fun SheetTitle(text: String) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    Spacer(Modifier.height(12.dp))
}

/** Two buttons side by side at the foot of a sheet. */
@Composable
fun ButtonPair(secondary: String, onSecondary: () -> Unit, primary: String, onPrimary: () -> Unit, primaryEnabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton(secondary, onSecondary, Modifier.weight(1f))
        PrimaryButton(primary, onPrimary, primaryEnabled, Modifier.weight(1f))
    }
}
