package com.example.ui.components

import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudflareWebViewDialog(
    initialUrl: String = "https://www.novelupdates.com",
    onDismiss: (cookie: String) -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = { onDismiss("") },
        modifier = Modifier.fillMaxWidth().height(600.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cloudflare Cookie Grabber")
                IconButton(onClick = {
                    val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                    onDismiss(cookies)
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Close & Save Cookies")
                }
            }
        },
        text = {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            // Prevent hardware acceleration renderer crashes in containerized environments
                            setLayerType(View.LAYER_TYPE_SOFTWARE, null)

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (url != null) currentUrl = url
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.safeBrowsingEnabled = false
                            loadUrl(initialUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                    onDismiss(cookies)
                }
            ) {
                Text("Capture & Save Cookies")
            }
        }
    )
}
