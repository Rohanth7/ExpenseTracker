package com.example.expensetracker.ui.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricUtil {
    /**
     * Fingerprint/face or the phone's PIN, pattern or password. Android 9 and 10 don't support
     * BIOMETRIC_STRONG together with DEVICE_CREDENTIAL (the prompt throws), so they use WEAK.
     */
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True if the phone has a screen lock or enrolled biometrics the prompt can use. */
    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system prompt. [onError] gets a message only for errors worth showing; a cancel
     * by the user passes null.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Unlock MyMoney",
        subtitle: String = "Use your fingerprint, face or screen lock",
        onSuccess: () -> Unit,
        onError: (String?) -> Unit
    ) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val quiet = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onError(if (quiet) null else errString.toString())
                }
            })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }
}
