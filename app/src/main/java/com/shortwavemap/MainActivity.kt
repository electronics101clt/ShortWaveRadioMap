package com.shortwavemap

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private val urls = arrayOf(
        "https://rx.skywavelinux.com",
        "https://map.kiwisdr.com",
        "https://rx-tx.info/map-sdr-points"
    )

    private var currentUrlIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        setupWebView()
        loadWithFallback()
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

        // Add JavaScript interface for offline page retry button
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun retry() {
                runOnUiThread {
                    currentUrlIndex = 0
                    loadWithFallback()
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
        if (!isNetworkAvailable()) {
            loadOfflinePage()
            return
        }

        checkUrlAndLoad(urls[currentUrlIndex])
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

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
