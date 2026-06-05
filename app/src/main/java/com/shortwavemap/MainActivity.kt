package com.shortwavemap

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var errorLayout: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var progressBar: ProgressBar

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
        errorLayout = findViewById(R.id.errorLayout)
        errorText = findViewById(R.id.errorText)
        retryButton = findViewById(R.id.retryButton)
        progressBar = findViewById(R.id.progressBar)

        setupWebView()

        retryButton.setOnClickListener {
            currentUrlIndex = 0
            loadWithFallback()
        }

        loadWithFallback()
    }

    @SuppressLint("SetJavaScriptEnabled")
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
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
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
            showError(getString(R.string.no_network))
            return
        }

        errorLayout.visibility = View.GONE
        webView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

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
            showError(getConnectionGuidance())
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        webView.visibility = View.GONE
        errorLayout.visibility = View.VISIBLE
        errorText.text = message
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getConnectionGuidance(): String {
        val locale = Locale.getDefault().language
        return if (locale == "zh") {
            getString(R.string.connection_help_zh)
        } else {
            getString(R.string.connection_help_en)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
