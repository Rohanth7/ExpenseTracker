package com.example.expensetracker.ui.screens.onboarding

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.expensetracker.data.defaults.DefaultCategories
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.parseLimitInput
import com.example.expensetracker.ui.util.NotificationAccessUtil
import com.example.expensetracker.ui.util.SmsPermissionUtil
import com.example.expensetracker.ui.util.rememberTrackingStatus

private enum class Step { Welcome, Sms, Notifications, Alerts, Income }

/**
 * First-run setup: explains each permission before Android asks for it, then takes an optional
 * monthly income. Every step can be skipped; Settings → Auto-tracking can turn things on later.
 *
 * [onFinish] receives the monthly income, or null if the user skipped it (or it came from a backup),
 * and whether a backup was restored. The Welcome step can restore a backup file for people moving
 * phones; setup then carries on with the permission steps, which a new phone still needs.
 */
@Composable
fun OnboardingScreen(
    onFinish: (monthlyIncome: Double?, restored: Boolean) -> Unit,
    viewModel: OnboardingViewModel = viewModel()
) {
    val context = LocalContext.current
    var restoredSummary by rememberSaveable { mutableStateOf<String?>(null) }
    var restoredHasIncome by rememberSaveable { mutableStateOf(false) }
    var restoreError by rememberSaveable { mutableStateOf<String?>(null) }
    val restoring = viewModel.restoring
    val activity = context as? Activity
    val steps = remember {
        buildList {
            add(Step.Welcome); add(Step.Sms); add(Step.Notifications)
            // Android 12 and lower don't need a runtime permission to show notifications.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Step.Alerts)
            add(Step.Income)
        }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val next = { if (index < steps.lastIndex) index++ }
    BackHandler(enabled = index > 0) { index-- }

    val tracking = rememberTrackingStatus()

    var smsDeniedForGood by rememberSaveable { mutableStateOf(false) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tracking.refresh()
        // After a denial with "don't ask again" (or a second denial), Android won't show the
        // prompt anymore, so the button has to send the user to App info instead.
        smsDeniedForGood = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECEIVE_SMS)
        if (granted) next()
    }

    var alertsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val alertsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        alertsGranted = granted
        next()
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        restoreError = null
        viewModel.restore(uri)
    }
    LaunchedEffect(viewModel.outcome) {
        when (val outcome = viewModel.outcome) {
            is RestoreOutcome.Restored -> {
                restoredSummary = outcome.summary; restoredHasIncome = outcome.hasIncome
                if (step == Step.Welcome) next()
            }
            is RestoreOutcome.Failed -> restoreError = outcome.reason
            null -> return@LaunchedEffect
        }
        viewModel.outcomeShown()
    }

    var incomeText by rememberSaveable { mutableStateOf("") }
    val parsedIncome = parseLimitInput(incomeText)

    Column(
        modifier = Modifier.fillMaxSize().background(Canvas).systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        ProgressDots(count = steps.size, current = index)
        Spacer(Modifier.height(32.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                Step.Welcome -> {
                    StepBody(
                        eyebrow = "WELCOME",
                        title = "Your spending, tracked automatically",
                        body = "MyMoney logs payments for you from bank SMS and UPI app notifications.\n\n" +
                            "Everything stays on this phone. There's no account, no ads and no loans.",
                        points = listOf(
                            "📩  Reads bank SMS and GPay, PhonePe and Paytm alerts",
                            "🗂  Sorts payments into categories, and learns as you go",
                            "🔒  Your data never leaves your phone unless you back it up"
                        )
                    )
                    restoreError?.let { Text(it, color = Coral, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
                }
                Step.Sms -> StepBody(
                    eyebrow = "STEP 1 · BANK SMS",
                    title = "Log payments from bank SMS",
                    body = "When your bank texts you about a debit, MyMoney logs the amount and payee.\n\n" +
                        "It only looks at new messages from banks and payment services. OTPs and personal " +
                        "messages are ignored, and nothing is sent anywhere.",
                    status = if (tracking.status.sms) "SMS access is on" else null
                )
                Step.Notifications -> StepBody(
                    eyebrow = "STEP 2 · PAYMENT APPS",
                    title = "Catch UPI payments from GPay and PhonePe",
                    body = "Some UPI payments only show up as a notification from the payment app. To log those, " +
                        "Android needs you to switch on notification access for MyMoney.\n\n" +
                        "MyMoney only reads notifications from payment and banking apps. Messages and chats are ignored.",
                    hint = "Tap the button, find MyMoney and turn on the switch, then come back. If the switch is " +
                        "greyed out, open App info for MyMoney, tap ⋮ and choose \"Allow restricted settings\" first.",
                    status = if (tracking.status.notifications) "Notification access is on" else null
                )
                Step.Alerts -> StepBody(
                    eyebrow = "STEP 3 · ALERTS",
                    title = "Get a prompt after each payment",
                    body = "MyMoney sends a notification right after a payment so you can pick its category in one tap. " +
                        "It also warns you when a category is close to its budget.",
                    status = if (alertsGranted) "Alerts are on" else null
                )
                Step.Income -> if (restoredSummary != null && restoredHasIncome) {
                    StepBody(
                        eyebrow = "LAST STEP",
                        title = "You're all set",
                        body = "Restored from your backup: $restoredSummary.\n\nYou can change anything later in Settings."
                    )
                } else {
                    StepBody(
                        eyebrow = "LAST STEP",
                        title = "What's your monthly income?",
                        body = "This is optional. It lets the Overview show how much is still free to spend this month. " +
                            "You can change it any time from the Overview screen.",
                        points = if (restoredSummary != null) listOf("Restored from your backup: $restoredSummary.")
                            else listOf("We'll also add ${DefaultCategories.STARTER.size} starter categories, like " +
                            DefaultCategories.STARTER.take(4).joinToString { it.name } + ". You can edit or delete them.")
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = incomeText,
                        onValueChange = { incomeText = it },
                        label = { Text("Monthly income (₹)") },
                        singleLine = true,
                        isError = incomeText.isNotBlank() && parsedIncome == null,
                        supportingText = if (incomeText.isNotBlank() && parsedIncome == null) {
                            { Text("Enter an amount like 50,000") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Ink, unfocusedTextColor = Ink,
                            focusedBorderColor = Jade, unfocusedBorderColor = Hairline,
                            focusedLabelColor = Jade, unfocusedLabelColor = Muted, cursorColor = Jade
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Buttons
        when (step) {
            Step.Welcome -> {
                PrimaryButton("Get started", enabled = !restoring, onClick = next)
                SecondaryButton(if (restoring) "Restoring…" else "Moving from another phone? Restore a backup") {
                    // Drive restore needs the app's Google sign-in client registered first (Play launch work).
                    if (!restoring) restoreLauncher.launch(arrayOf("application/json", "*/*"))
                }
            }
            Step.Sms -> if (tracking.status.sms) PrimaryButton("Continue", onClick = next) else {
                PrimaryButton(if (smsDeniedForGood) "Open app settings" else "Allow SMS access") {
                    if (smsDeniedForGood) SmsPermissionUtil.openSettings(context)
                    else smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                }
                SecondaryButton("Not now", onClick = next)
            }
            Step.Notifications -> if (tracking.status.notifications) PrimaryButton("Continue", onClick = next) else {
                PrimaryButton("Turn on notification access") { NotificationAccessUtil.openSettings(context) }
                SecondaryButton("Not now", onClick = next)
            }
            Step.Alerts -> if (alertsGranted) PrimaryButton("Continue", onClick = next) else {
                PrimaryButton("Allow alerts") { alertsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                SecondaryButton("Not now", onClick = next)
            }
            Step.Income -> if (restoredSummary != null && restoredHasIncome) {
                PrimaryButton("Finish") { onFinish(null, true) }
            } else {
                val incomeInvalid = incomeText.isNotBlank() && parsedIncome == null
                PrimaryButton("Finish", enabled = !incomeInvalid) { onFinish(parsedIncome?.takeIf { it > 0 }, restoredSummary != null) }
                SecondaryButton("Skip") { onFinish(null, restoredSummary != null) }
            }
        }
    }
}

@Composable
private fun ProgressDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.height(4.dp).width(if (i == current) 24.dp else 12.dp).clip(CircleShape)
                    .background(if (i <= current) Jade else Hairline)
            )
        }
    }
}

@Composable
private fun StepBody(
    eyebrow: String,
    title: String,
    body: String,
    points: List<String> = emptyList(),
    hint: String? = null,
    status: String? = null
) {
    Text(eyebrow, fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, letterSpacing = 1.8.sp, color = Muted, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Text(title, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 30.sp, color = Ink, lineHeight = 34.sp)
    Spacer(Modifier.height(16.dp))
    Text(body, fontSize = 15.sp, color = InkSoft, lineHeight = 22.sp)
    if (points.isNotEmpty()) {
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Paper).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { points.forEach { Text(it, fontSize = 14.sp, color = Ink, lineHeight = 20.sp) } }
    }
    if (hint != null) {
        Spacer(Modifier.height(16.dp))
        Text(hint, fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
    }
    if (status != null) {
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(JadeSoft).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { Text("✓  $status", fontSize = 14.sp, color = JadeInk, fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Jade else Jade.copy(alpha = 0.35f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Deep) }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, color = Muted, fontWeight = FontWeight.Medium) }
}

