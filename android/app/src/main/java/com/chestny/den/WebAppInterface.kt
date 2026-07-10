package com.chestny.den

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class WebAppInterface(private val activity: Activity) {
    companion object {
        const val NAME = "AndroidBridge"
        private const val TAG = "WebAppInterface"
    }

    @JavascriptInterface
    fun getAppVersion(): String = "1.0.0"

    @JavascriptInterface
    fun getPlatform(): String = "android"

    @JavascriptInterface
    fun vibrate(durationMs: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs.toLong())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibrate error", e)
        }
    }

    @JavascriptInterface
    fun scheduleNotification(title: String, body: String, hour: Int, minute: Int, requestCode: Int) {
        NotificationHelper.scheduleReminder(activity, hour, minute, title, body, requestCode)
    }

    @JavascriptInterface
    fun showNotification(title: String, body: String) {
        NotificationHelper.showNotification(activity, title, body)
    }

    @JavascriptInterface
    fun isBiometricAvailable(): String {
        return try {
            val biometricManager = androidx.biometric.BiometricManager.from(activity)
            val result = biometricManager.canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            Gson().toJson(mapOf(
                "available" to (result == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS),
                "code" to result
            ))
        } catch (e: Exception) {
            Gson().toJson(mapOf("available" to false, "error" to e.message))
        }
    }

    @JavascriptInterface
    fun authenticateBiometric(callbackId: String) {
        try {
            val executor = Executors.newSingleThreadExecutor()
            val biometricPrompt = BiometricPrompt(
                activity as FragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        activity.runOnUiThread {
                            val webView = (activity as MainActivity).webView
                            webView.evaluateJavascript(
                                "javascript:(function(){var cb=window._bioCallbacks&&window._bioCallbacks['$callbackId'];if(cb){cb(true);delete window._bioCallbacks['$callbackId'];}})();",
                                null
                            )
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        activity.runOnUiThread {
                            val webView = (activity as MainActivity).webView
                            webView.evaluateJavascript(
                                "javascript:(function(){var cb=window._bioCallbacks&&window._bioCallbacks['$callbackId'];if(cb){cb(false,'$errString');delete window._bioCallbacks['$callbackId'];}})();",
                                null
                            )
                        }
                    }

                    override fun onAuthenticationFailed() {
                        activity.runOnUiThread {
                            val webView = (activity as MainActivity).webView
                            webView.evaluateJavascript(
                                "javascript:(function(){var cb=window._bioCallbacks&&window._bioCallbacks['$callbackId'];if(cb){cb(false,'Failed');delete window._bioCallbacks['$callbackId'];}})();",
                                null
                            )
                        }
                    }
                }
            )
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_title))
                .setSubtitle(activity.getString(R.string.biometric_subtitle))
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Biometric error", e)
        }
    }

    @JavascriptInterface
    fun getSecureSettings(key: String): String = SecureStorage.getString(key)

    @JavascriptInterface
    fun setSecureSettings(key: String, value: String) = SecureStorage.putString(key, value)

    @JavascriptInterface
    fun removeSecureSettings(key: String) = SecureStorage.remove(key)

    @JavascriptInterface
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }

    @JavascriptInterface
    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        activity.startActivity(Intent.createChooser(intent, "Поделиться"))
    }

    @JavascriptInterface
    fun toast(message: String) {
        activity.runOnUiThread { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    }

    @JavascriptInterface
    fun logEvent(event: String, data: String) {
        Log.d(TAG, "Event: $event - $data")
    }

    @JavascriptInterface
    fun fetchApi(url: String, optionsJson: String, callbackId: String) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val gson = Gson()
                val options = gson.fromJson(optionsJson, Map::class.java) as Map<String, Any>
                val method = (options["method"] as? String)?.uppercase() ?: "GET"
                val headers = options["headers"] as? Map<String, String> ?: emptyMap()
                val body = options["body"] as? String

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                headers.forEach { (key, value) -> conn.setRequestProperty(key, value) }

                if (body != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                    conn.doOutput = true
                    OutputStreamWriter(conn.outputStream).use { it.write(body) }
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseBody = BufferedReader(InputStreamReader(stream)).readText()

                val result = gson.toJson(mapOf(
                    "status" to responseCode,
                    "body" to responseBody
                ))

                activity.runOnUiThread {
                    val webView = (activity as MainActivity).webView
                    webView.evaluateJavascript(
                        "javascript:(function(){var cb=window._fetchCallbacks&&window._fetchCallbacks['$callbackId'];if(cb){cb($result);delete window._fetchCallbacks['$callbackId'];}})();",
                        null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchApi error", e)
                val errorJson = Gson().toJson(mapOf("status" to 0, "error" to (e.message ?: "Unknown error")))
                activity.runOnUiThread {
                    val webView = (activity as MainActivity).webView
                    webView.evaluateJavascript(
                        "javascript:(function(){var cb=window._fetchCallbacks&&window._fetchCallbacks['$callbackId'];if(cb){cb($errorJson);delete window._fetchCallbacks['$callbackId'];}})();",
                        null
                    )
                }
            }
        }
    }
}
