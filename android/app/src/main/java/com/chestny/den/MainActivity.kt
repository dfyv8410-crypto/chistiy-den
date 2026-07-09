package com.chestny.den

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.google.firebase.FirebaseApp
import java.io.File

class MainActivity : AppCompatActivity() {

    lateinit var webView: WebView
    private lateinit var splashOverlay: ViewGroup
    private lateinit var pinOverlay: ViewGroup
    private var pinAttempt = StringBuilder()
    private var savedPin = ""

    companion object {
        private const val TAG = "MainActivity"

        fun rescheduleAllReminders(context: Context) {
            val prefs = context.getSharedPreferences("reminder_prefs", Context.MODE_PRIVATE)
            val morning = prefs.getString("morning", null)
            val day = prefs.getString("day", null)
            val evening = prefs.getString("evening", null)
            if (!morning.isNullOrBlank()) {
                val parts = morning.split(":")
                NotificationHelper.scheduleReminder(context, parts[0].toInt(), parts[1].toInt(),
                    "☀️ Доброе утро", "Новый день восстановления. Отметь своё состояние.", 1001)
            }
            if (!day.isNullOrBlank()) {
                val parts = day.split(":")
                NotificationHelper.scheduleReminder(context, parts[0].toInt(), parts[1].toInt(),
                    "💭 Как ты сейчас?", "Проверь своё состояние.", 1002)
            }
            if (!evening.isNullOrBlank()) {
                val parts = evening.split(":")
                NotificationHelper.scheduleReminder(context, parts[0].toInt(), parts[1].toInt(),
                    "🌙 Итог дня", "Подведи итог дня.", 1003)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SecureStorage.init(this)
        NotificationHelper.createChannels(this)
        try { FirebaseApp.initializeApp(this) } catch (_: Exception) {}

        splashOverlay = findViewById(R.id.splashOverlay)
        pinOverlay = findViewById(R.id.pinOverlay)
        webView = findViewById(R.id.webView)

        setupWebView()
        registerPinListeners()
        setupShortcuts()

        savedPin = SecureStorage.getString("app_pin", "")
        if (savedPin.isNotEmpty() && SecureStorage.getBoolean("app_pin_on", false)) {
            splashOverlay.visibility = ViewGroup.GONE
            showPinScreen()
        } else {
            splashOverlay.postDelayed({ splashOverlay.visibility = ViewGroup.GONE }, 600)
        }

        rescheduleAllReminders(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            (layoutParams as FrameLayout.LayoutParams).topMargin = getStatusBarHeight()

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mediaPlaybackRequiresUserGesture = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.safeBrowsingEnabled = true
            }

            CookieManager.getInstance().setAcceptCookie(true)

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    return assetLoader.shouldInterceptRequest(request?.url ?: return null)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    splashOverlay.visibility = ViewGroup.GONE
                }
            }

            addJavascriptInterface(WebAppInterface(this@MainActivity), WebAppInterface.NAME)

            webChromeClient = WebChromeClient()

            loadUrl("https://appassets.androidplatform.net/index.html")
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun showPinScreen() {
        pinOverlay.visibility = ViewGroup.VISIBLE
        pinAttempt = StringBuilder()
        updatePinDots()
    }

    private fun hidePinScreen() {
        pinOverlay.visibility = ViewGroup.GONE
        pinAttempt = StringBuilder()
    }

    private fun registerPinListeners() {
        val grid = pinOverlay.findViewById<GridLayout>(R.id.pinGrid)
        if (grid == null) return
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            if (child is Button) {
                child.setOnClickListener {
                    val tag = child.tag as? String ?: return@setOnClickListener
                    when (tag) {
                        "backspace" -> {
                            if (pinAttempt.isNotEmpty()) {
                                pinAttempt.deleteCharAt(pinAttempt.length - 1)
                                updatePinDots()
                            }
                        }
                        else -> {
                            if (pinAttempt.length < 6) {
                                pinAttempt.append(tag)
                                updatePinDots()
                                checkPin()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updatePinDots() {
        val dots = pinOverlay.findViewById<android.widget.LinearLayout>(R.id.pinDots)
        for (i in 0 until dots.childCount) {
            val dot = dots.getChildAt(i)
            dot.setBackgroundResource(
                if (i < pinAttempt.length) R.drawable.pin_dot_filled else R.drawable.pin_dot
            )
        }
    }

    private fun checkPin() {
        if (pinAttempt.length < 4) return
        if (pinAttempt.toString() == savedPin) {
            hidePinScreen()
        } else if (pinAttempt.length >= savedPin.length) {
            findViewById<TextView>(R.id.pinError).text = "Неверный пин-код"
            pinOverlay.postDelayed({
                pinAttempt = StringBuilder()
                updatePinDots()
                findViewById<TextView>(R.id.pinError).text = ""
            }, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        if (savedPin.isNotEmpty() && SecureStorage.getBoolean("app_pin_on", false)) {
            if (pinOverlay.visibility != ViewGroup.VISIBLE) {
                showPinScreen()
            }
        }
        rescheduleAllReminders(this)
    }

    override fun onPause() {
        super.onPause()
        if (pinOverlay.visibility == ViewGroup.VISIBLE) {
            pinAttempt = StringBuilder()
            updatePinDots()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            moveTaskToBack(true)
        }
    }

    private fun setupShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        try {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            val helpIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("screen", "help")
            }
            val diaryIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("screen", "diary")
            }
            val prayerIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("screen", "prayer")
            }
            val stepsIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("screen", "steps")
            }
            val shortcuts = listOf(
                ShortcutInfo.Builder(this, "shortcut_help")
                    .setShortLabel(getString(R.string.shortcut_help))
                    .setLongLabel(getString(R.string.shortcut_help))
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_help))
                    .setIntent(helpIntent)
                    .build(),
                ShortcutInfo.Builder(this, "shortcut_diary")
                    .setShortLabel(getString(R.string.shortcut_diary))
                    .setLongLabel(getString(R.string.shortcut_diary))
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_diary))
                    .setIntent(diaryIntent)
                    .build(),
                ShortcutInfo.Builder(this, "shortcut_prayer")
                    .setShortLabel(getString(R.string.shortcut_prayer))
                    .setLongLabel(getString(R.string.shortcut_prayer))
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_prayer))
                    .setIntent(prayerIntent)
                    .build(),
                ShortcutInfo.Builder(this, "shortcut_steps")
                    .setShortLabel(getString(R.string.shortcut_steps))
                    .setLongLabel(getString(R.string.shortcut_steps))
                    .setIcon(Icon.createWithResource(this, R.drawable.ic_shortcut_steps))
                    .setIntent(stepsIntent)
                    .build()
            )
            shortcutManager.setDynamicShortcuts(shortcuts)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Shortcut setup error", e)
        }
    }
}
