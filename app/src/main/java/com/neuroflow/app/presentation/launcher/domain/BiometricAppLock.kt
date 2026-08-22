package com.neuroflow.app.presentation.launcher.domain

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Utility for biometric authentication to lock/unlock apps.
 * Uses BiometricPrompt with BIOMETRIC_STRONG authenticator.
 */
object BiometricAppLock {

    private const val TAG = "BiometricAppLock"

    /**
     * Check if biometric authentication is available on this device.
     *
     * @param context Android context
     * @return true if biometric hardware is available and enrolled, false otherwise
     */
    fun isAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d(TAG, "Biometric authentication is available")
                true
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Log.d(TAG, "No biometric hardware available")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Log.d(TAG, "Biometric hardware unavailable")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Log.d(TAG, "No biometric credentials enrolled")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                Log.d(TAG, "Security update required for biometric")
                false
            }
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
                Log.d(TAG, "Biometric authentication unsupported")
                false
            }
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                Log.d(TAG, "Biometric status unknown")
                false
            }
            else -> {
                Log.d(TAG, "Unknown biometric status")
                false
            }
        }
    }

    /**
     * Authenticate the user with biometric prompt.
     *
     * @param activity FragmentActivity for showing the prompt
     * @param onSuccess Callback invoked on successful authentication
     * @param onFailure Callback invoked on authentication failure or cancellation
     */
    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)

                    when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                            // User cancelled - no error message
                            Log.d(TAG, "Authentication cancelled by user")
                            onFailure()
                        }
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            // Too many attempts
                            Log.w(TAG, "Biometric lockout: $errString")
                            Toast.makeText(
                                activity,
                                "Too many attempts. Try again later.",
                                Toast.LENGTH_LONG
                            ).show()
                            onFailure()
                        }
                        BiometricPrompt.ERROR_NO_BIOMETRICS -> {
                            // No biometrics enrolled
                            Log.w(TAG, "No biometrics enrolled")
                            Toast.makeText(
                                activity,
                                "No biometric credentials enrolled",
                                Toast.LENGTH_SHORT
                            ).show()
                            onFailure()
                        }
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> {
                            // Hardware issue
                            Log.e(TAG, "Biometric hardware error: $errString")
                            Toast.makeText(
                                activity,
                                "Biometric authentication unavailable",
                                Toast.LENGTH_SHORT
                            ).show()
                            onFailure()
                        }
                        else -> {
                            // Other errors
                            Log.e(TAG, "Authentication error $errorCode: $errString")
                            Toast.makeText(
                                activity,
                                "Authentication failed: $errString",
                                Toast.LENGTH_SHORT
                            ).show()
                            onFailure()
                        }
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Authentication succeeded")
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // This is called for each failed attempt (e.g., wrong fingerprint)
                    // but the prompt stays open for retry
                    Log.d(TAG, "Authentication attempt failed")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock App")
            .setSubtitle("Authenticate to access this app")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
