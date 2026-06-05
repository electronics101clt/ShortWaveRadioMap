package com.shortwavemap

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.webkit.*
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private val urls = arrayOf(
        "https://rx.skywavelinux.com",
        "https://map.kiwisdr.com",
        "https://rx-tx.info/map-sdr-points"
    )

    private var currentUrlIndex = 0
    private var setupStep = SetupStep.NONE

    private enum class SetupStep {
        NONE,
        WIFI,
        TIME,
        DONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        setupWebView()

        // Gate: Check setup before attempting to load anything
        checkSetupGate()
    }

    private fun checkSetupGate() {
        // Gate 1: Check WiFi
        if (!isNetworkAvailable()) {
            setupStep = SetupStep.WIFI
            showWifiSetup()
            return
        }

        // Gate 2: Test connection to verify time/certificates
        testConnection()
    }

    private fun testConnection() {
        Toast.makeText(this, "Checking connection...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                val connection = URL(urls[0]).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val responseCode = connection.responseCode

                runOnUiThread {
                    if (responseCode in 200..399) {
                        // All gates passed - proceed to load
                        loadWithFallback()
                    } else {
                        // Connection failed - likely time/certificate issue
                        setupStep = SetupStep.TIME
                        showTimeSetup()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    // Check if it's a certificate error
                    if (isCertificateError(e)) {
                        setupStep = SetupStep.TIME
                        showTimeSetupForCertError()
                    } else {
                        // Other error - might be network blocked
                        setupStep = SetupStep.TIME
                        showTimeSetup()
                    }
                }
            }
        }
    }

    private fun isCertificateError(e: Exception): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val message = cause.message?.lowercase() ?: ""
            if (message.contains("certificate") ||
                message.contains("cert path") ||
                message.contains("validity") ||
                message.contains("expired") ||
                message.contains("not yet valid")) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun showTimeSetupForCertError() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val dateTime = String.format("%02d/%02d/%d %02d:%02d",
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            year,
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE))

        AlertDialog.Builder(this)
            .setTitle("Certificate Error - Wrong Time")
            .setMessage("""
                |SSL Certificate error detected!
                |
                |Device time: $dateTime
                |Device year: $year
                |
                |This is usually caused by wrong date/time.
                |
                |Fix date/time to continue:
                |
                |1. Turn ON "Automatic date & time"
                |   OR
                |2. Manually set correct date/time
                |3. Press BACK to return
            """.trimMargin())
            .setPositiveButton("Open Date/Time") { _, _ ->
                openDateTimeSettings()
            }
            .setNegativeButton("View Offline") { _, _ ->
                setupStep = SetupStep.NONE
                loadOfflinePage()
            }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
        }

        // Add JavaScript interface for offline page buttons
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun retry() {
                runOnUiThread {
                    currentUrlIndex = 0
                    // Re-run setup gate check
                    checkSetupGate()
                }
            }

            @JavascriptInterface
            fun setupWifi() {
                runOnUiThread {
                    setupStep = SetupStep.WIFI
                    openWifiSettings()
                }
            }
        }, "RetryConnection")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    tryNextUrl()
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    private fun loadWithFallback() {
        // This is only called after passing setup gates
        checkUrlAndLoad(urls[currentUrlIndex])
    }

    private fun showWifiSetup() {
        AlertDialog.Builder(this)
            .setTitle("WiFi Not Connected")
            .setMessage("""
                |Connect to WiFi to access live shortwave radio receivers:
                |
                |1. Turn WiFi ON
                |2. Tap your network
                |3. Enter password
                |4. Wait for "Connected"
                |5. Press BACK to return
            """.trimMargin())
            .setPositiveButton("Open WiFi") { _, _ ->
                setupStep = SetupStep.WIFI
                openWifiSettings()
            }
            .setNegativeButton("View Offline") { _, _ ->
                setupStep = SetupStep.NONE
                loadOfflinePage()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkUrlAndLoad(url: String) {
        thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val responseCode = connection.responseCode

                runOnUiThread {
                    if (responseCode in 200..399) {
                        webView.loadUrl(url)
                    } else {
                        tryNextUrl()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tryNextUrl()
                }
            }
        }
    }

    private fun tryNextUrl() {
        currentUrlIndex++
        if (currentUrlIndex < urls.size) {
            Handler(Looper.getMainLooper()).postDelayed({
                checkUrlAndLoad(urls[currentUrlIndex])
            }, 1000)
        } else {
            loadOfflinePage()
        }
    }

    private fun loadOfflinePage() {
        webView.loadUrl("file:///android_asset/offline.html")
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun openWifiSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun openDateTimeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "Open Settings > Date & Time", Toast.LENGTH_LONG).show()
        }
    }

    private fun showTimeSetup() {
        val cal = Calendar.getInstance()
        val dateTime = String.format("%02d/%02d/%d %02d:%02d",
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.YEAR),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE))

        AlertDialog.Builder(this)
            .setTitle("Check Date & Time")
            .setMessage("""
                |Current: $dateTime
                |
                |Wrong date/time can cause SSL certificate errors.
                |
                |1. Turn ON "Automatic date & time"
                |
                |   OR manually:
                |
                |2. Tap "Set date" - enter today
                |3. Tap "Set time" - enter now
                |4. Press BACK to return
            """.trimMargin())
            .setPositiveButton("Open Date/Time") { _, _ ->
                setupStep = SetupStep.TIME
                openDateTimeSettings()
            }
            .setNegativeButton("Skip") { _, _ ->
                setupStep = SetupStep.NONE
                testConnection()
            }
            .setCancelable(false)
            .show()
    }


    override fun onResume() {
        super.onResume()

        when (setupStep) {
            SetupStep.WIFI -> {
                if (isNetworkAvailable()) {
                    // WiFi gate passed - continue to connection test gate
                    setupStep = SetupStep.NONE
                    testConnection()
                } else {
                    showWifiRetry()
                }
            }
            SetupStep.TIME -> {
                // Time was adjusted - retry connection test gate
                setupStep = SetupStep.NONE
                testConnection()
            }
            else -> {}
        }
    }

    private fun showWifiRetry() {
        AlertDialog.Builder(this)
            .setTitle("WiFi Not Connected")
            .setMessage("WiFi is still not connected.\n\nTry again?")
            .setPositiveButton("Try Again") { _, _ ->
                setupStep = SetupStep.WIFI
                openWifiSettings()
            }
            .setNegativeButton("View Offline") { _, _ ->
                setupStep = SetupStep.NONE
                loadOfflinePage()
            }
            .setCancelable(false)
            .show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
