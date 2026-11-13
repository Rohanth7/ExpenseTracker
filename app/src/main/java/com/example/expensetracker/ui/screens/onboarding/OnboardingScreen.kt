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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.expensetracker.data.defaults.DefaultCategories
import com.example.expensetracker.ui.components.*
import com.example.expensetracker.ui.screens.settings.RestoreConfirmDialog
import com.example.expensetracker.ui.theme.*
import com.example.expensetracker.ui.util.AmountCalculator
import com.example.expensetracker.ui.util.parseLimitInput
import com.example.expensetracker.ui.util.NotificationAccessUtil
import com.example.expensetracker.ui.util.SmsPermissionUtil
import com.example.expensetracker.ui.util.fmtINR
import com.example.expensetracker.ui.util.rememberTrackingStatus

private enum class Step { Welcome, Sms, Notifications, Alerts, Income }

/**
 * First-run setup: explains each permission before Android asks for it, then takes an optional
 * monthly income. Every step can be skipped; Settings → Auto-tracking can turn things on later.
 *
 * [onFinish] receives the monthly income, or null if the user skipped it (or it came from a backup).
 * The Welcome step can restore a backup file for people moving phones; setup then carries on with
 * the permission steps, which a new phone still needs.
 */
@Composable
fun OnboardingScreen(onFinish: (monthlyIncome: Double?) -> Unit, viewModel: OnboardingViewModel = viewModel()) {
    val context = LocalContext.current
    val restored = viewModel.restored
    val busy = viewModel.busy
    // A backup with an income needs nothing more; one without categories gets the starter set.
    val skipIncome = restored?.hasIncome == true
    val starterNote = "We'll also add ${DefaultCategories.STARTER.size} starter categories, like " +
        DefaultCategories.STARTER.take(4).joinToString { it.name } + ". You can edit or delete them."
    val activity = context as? Activity
    val steps = remember {
        buildList {
            add(Step.Welcome); add(Step.Sms); add(Step.Notifications)
            // Android 12 and lower don't need a runtime permission to show notifications.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Step.Alerts)
            add(Step.Income)
        }
    }
    // Reopened after a restore: the restore is done, so start at the first permission step.
    var index by rememberSaveable { mutableIntStateOf(if (restored != null) 1 else 0) }
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
        if (uri != null) viewModel.pick(uri)
    }
    LaunchedEffect(viewModel.justRestored) {
        if (!viewModel.justRestored) return@LaunchedEffect
        if (step == Step.Welcome) next()
        viewModel.restoreShown()
    }

    var incomeText by rememberSaveable { mutableStateOf("") }
    val parsedIncome = remember(incomeText) { parseLimitInput(incomeText) }
    val incomeShown = remember(incomeText) { AmountCalculator.previewText(incomeText, parsedIncome) }

    Column(
        modifier = Modifier.fillMaxSize().background(Canvas).systemBarsPadding().padding(horizontal = ScreenPadding).padding(top = 8.dp, bottom = 16.dp)
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                Step.Welcome -> {
                    // Left empty for the mascot.
                    Spacer(Modifier.size(170.dp).align(Alignment.CenterHorizontally))
                    StepBody(
                        title = "Your spending, tracked automatically",
                        body = "MyMoney logs payments for you from bank SMS and UPI app notifications.\n\n" +
                            "Everything stays on this phone. There's no account, no ads and no loans.",
                        centered = true,
                        points = listOf(
                            "Reads bank SMS and GPay, PhonePe and Paytm alerts",
                            "Sorts payments into categories, and learns as you go",
                            "Your data never leaves your phone unless you back it up"
                        )
                    )
                    viewModel.restoreError?.let { Text(it, color = Pink, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
                }
                Step.Sms -> StepBody(
                    title = "Log payments from bank SMS",
                    body = "When your bank texts you about a debit, MyMoney logs the amount and payee.\n\n" +
                        "It only looks at new messages from banks and payment services. OTPs and personal " +
                        "messages are ignored, and nothing is sent anywhere.",
                    status = if (tracking.status.sms) "SMS access is on" else null,
                    example = { SmsExample() }
                )
                Step.Notifications -> StepBody(
                    title = "Catch UPI payments from GPay and PhonePe",
                    body = "Some UPI payments only show up as a notification from the payment app. To log those, " +
                        "Android needs you to switch on notification access for MyMoney.\n\n" +
                        "MyMoney only reads notifications from payment and banking apps. Messages and chats are ignored.",
                    hint = "Tap the button, find MyMoney and turn on the switch, then come back. If the switch is " +
                        "greyed out, open App info for MyMoney, tap ⋮ and choose \"Allow restricted settings\" first.",
                    status = if (tracking.status.notifications) "Notification access is on" else null
                )
                Step.Alerts -> StepBody(
                    title = "Get a prompt after each payment",
                    body = "MyMoney sends a notification right after a payment so you can pick its category in one tap. " +
                        "It also warns you when a category is close to its budget.",
                    status = if (alertsGranted) "Alerts are on" else null
                )
                Step.Income -> if (restored != null && skipIncome) {
                    StepBody(
                        title = "You're all set",
                        body = "Restored from your backup: ${restored.summary}.\n\nYou can change anything later in Settings.",
                        centered = true,
                        points = if (restored.hadCategories) emptyList() else listOf(starterNote)
                    )
                } else {
                    StepBody(
                        title = "What's your monthly income?",
                        body = "This is optional. It lets Home show how much is still free to spend this month. " +
                            "You can change it any time from the Home screen.",
                        centered = true,
                        points = when {
                            restored == null -> listOf(starterNote)
                            restored.hadCategories -> listOf("Restored from your backup: ${restored.summary}.")
                            else -> listOf("Restored from your backup: ${restored.summary}.", starterNote)
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = incomeText,
                        onValueChange = { incomeText = it },
                        label = { Text("Monthly income (₹)") },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        isError = incomeText.isNotBlank() && parsedIncome == null,
                        // "50000", not "50,000": on a comma-decimal phone that's ₹50.
                        supportingText = when {
                            incomeText.isNotBlank() && parsedIncome == null -> {{ Text("Enter an amount like 50000") }}
                            incomeShown != null -> {{ Text(incomeShown, color = Muted, fontSize = 13.sp) }}
                            else -> null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = FieldShape, colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        ProgressDots(count = steps.size, current = index, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        when (step) {
            Step.Welcome -> {
                PrimaryButton("Get started", onClick = next, enabled = !busy)
                RestoreAction(busy) {
                    // Drive restore needs the app's Google sign-in client registered first (Play launch work).
                    if (!busy) restoreLauncher.launch(arrayOf("application/json", "*/*"))
                }
            }
            Step.Sms -> if (tracking.status.sms) PrimaryButton("Continue", onClick = next) else {
                PrimaryButton(if (smsDeniedForGood) "Open app settings" else "Allow SMS access", onClick = {
                    if (smsDeniedForGood) SmsPermissionUtil.openSettings(context)
                    else smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                })
                SkipAction("Not now", onClick = next)
            }
            Step.Notifications -> if (tracking.status.notifications) PrimaryButton("Continue", onClick = next) else {
                PrimaryButton("Turn on notification access", onClick = { NotificationAccessUtil.openSettings(context) })
                SkipAction("Not now", onClick = next)
            }
            Step.Alerts -> if (alertsGranted) PrimaryButton("Continue", onClick = next) else {
                PrimaryButton("Allow alerts", onClick = { alertsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
                SkipAction("Not now", onClick = next)
            }
            Step.Income -> if (skipIncome) {
                PrimaryButton("Finish", onClick = { onFinish(null) })
            } else {
                val incomeInvalid = incomeText.isNotBlank() && parsedIncome == null
                PrimaryButton("Finish", onClick = { onFinish(parsedIncome?.takeIf { it > 0 }) }, enabled = !incomeInvalid)
                SkipAction("Skip", onClick = { onFinish(null) })
            }
        }
    }

    // Nothing is replaced until the user has seen what the backup holds.
    viewModel.pending?.let { RestoreConfirmDialog(data = it, fromDrive = false, onConfirm = viewModel::confirm, onDismiss = viewModel::cancel) }
}

@Composable
private fun ProgressDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            Box(
                Modifier.height(5.dp).width(if (i == current) 18.dp else 5.dp).clip(CircleShape)
                    .background(if (i == current) Gold else Hairline)
            )
        }
    }
}

@Composable
private fun ColumnScope.StepBody(
    title: String,
    body: String,
    points: List<String> = emptyList(),
    hint: String? = null,
    status: String? = null,
    centered: Boolean = false,
    example: (@Composable ColumnScope.() -> Unit)? = null
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Spacer(Modifier.height(16.dp))
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 30.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Text(body, fontSize = 15.sp, color = Muted, lineHeight = 22.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
    if (example != null) {
        Spacer(Modifier.height(14.dp))
        example()
    }
    if (points.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        MCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                points.forEach { Text(it, fontSize = 15.sp, color = Ink) }
            }
        }
    }
    if (hint != null) {
        Spacer(Modifier.height(16.dp))
        Text(hint, fontSize = 13.sp, color = Muted)
    }
    if (status != null) {
        Spacer(Modifier.height(16.dp))
        Text("✓  $status", fontSize = 15.sp, color = Mint, fontWeight = FontWeight.SemiBold)
    }
}

/** What the SMS step does, shown with a made-up message. */
@Composable
private fun ColumnScope.SmsExample() {
    MCard {
        Text("Your bank sends", fontSize = 13.sp, color = Muted)
        Text("Rs 486.00 debited from a/c **1234 to SWIGGY on 12-Nov", fontSize = 15.sp, color = Ink)
    }
    Text("↓", fontSize = 15.sp, color = Gold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 4.dp))
    MCard(padding = RowCardPadding) {
        AccentRow(accent = Gold, title = "Swiggy", subtitle = "Food · today", trailing = "₹${fmtINR(486.0)}")
    }
}

@Composable
private fun ColumnScope.SkipAction(text: String, onClick: () -> Unit) {
    TextAction(text, onClick, color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
}

@Composable
private fun ColumnScope.RestoreAction(busy: Boolean, onClick: () -> Unit) {
    Text(
        if (busy) buildAnnotatedString { append("Restoring…") } else buildAnnotatedString {
            append("Moving from another phone? ")
            withStyle(SpanStyle(color = Gold)) { append("Restore a backup") }
        },
        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Muted, textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
            .clip(FieldShape).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp)
    )
}
