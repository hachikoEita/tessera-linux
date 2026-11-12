// Copyright (C) 2026 Daryan Kamaran Karim
// SPDX-License-Identifier: GPL-3.0-or-later

package com.daryan.tessera

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Personal-fork feature: a full-screen window that shows a local web page (OpenDesign's server,
 * running in a Linux terminal session) inside the app, with no browser bars. Own task, so it has
 * its own card in Recents. Back goes back in the page, then sends the card to the background.
 */
class WebWindowActivity : ComponentActivity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent.data?.toString() ?: run { finishAndRemoveTask(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Web"

        @Suppress("DEPRECATION")
        setTaskDescription(ActivityManager.TaskDescription("$title (Linux)"))

        web = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        view.loadData(
                            "<html><body style='font-family:sans-serif;padding:24px'>" +
                                "<h2>Can't reach $title</h2>" +
                                "<p>Is the $title terminal still running? Start it from the Apps tab, " +
                                "wait until it says it is ready, then open this window again.</p>" +
                                "<p style='color:#666'>Reason: ${error.description} (code ${error.errorCode}) for $url</p></body></html>",
                            "text/html", "utf-8",
                        )
                    }
                }
            }
            loadUrl(url)
        }
        setContentView(web)
        // Keep the page clear of the status bar, navigation bar and keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else moveTaskToBack(true)
            }
        })
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TITLE = "title"

        fun open(context: Context, url: String, title: String) {
            context.startActivity(
                Intent(context, WebWindowActivity::class.java)
                    .setData(Uri.parse(url))
                    .putExtra(EXTRA_TITLE, title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT),
            )
        }
    }
}
