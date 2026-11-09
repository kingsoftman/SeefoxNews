package com.hongtian.seefox

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SoftwareDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 完全使用系统默认界面，不做任何全屏/隐藏状态栏操作
        setContentView(R.layout.activity_software_detail)

        findViewById<TextView>(R.id.backBtn).setOnClickListener { finish() }
        // 优先用列表页直接传过来的文章内容，绝对不会文不对题
        val directTitle = intent.getStringExtra("title") ?: ""
        val directCategory = intent.getStringExtra("category") ?: ""
        val directDate = intent.getStringExtra("date") ?: ""
        val directHtml = intent.getStringExtra("html") ?: ""

        val webView = findViewById<WebView>(R.id.webContent)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 禁止双指缩放，详情页固定缩放比例
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        // 禁止横向滚动条，解决内容超宽横向滑动的问题
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = true
        webView.webViewClient = WebViewClient()

        // 构建完整HTML，适配深浅模式
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        val bgColor = if (isDark) "#1a1a1a" else "#ffffff"
        val textColor = if (isDark) "#e0e0e0" else "#333333"
        val linkColor = if (isDark) "#7c6ff0" else "#6c5ce7"

        fun renderHtml(title: String, category: String, date: String, content: String) {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body {
                        background-color: $bgColor;
                        color: $textColor;
                        font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
                        font-size: 16px;
                        line-height: 1.8;
                        padding: 20px;
                        margin: 0;
                    }
                    h1, h2, h3 {
                        color: ${if (isDark) "#ffffff" else "#1a1a1a"};
                        margin-top: 24px;
                        margin-bottom: 12px;
                        line-height: 1.4;
                    }
                    h3 { font-size: 18px; }
                    p { margin-bottom: 16px; }
                    ul, ol { margin: 12px 0 16px 24px; }
                    li { margin-bottom: 8px; }
                    code {
                        background-color: ${if (isDark) "#333" else "#f5f5f7"};
                        padding: 2px 6px;
                        border-radius: 4px;
                        font-size: 14px;
                        color: $linkColor;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 16px 0;
                        font-size: 14px;
                    }
                    th, td {
                        border: 1px solid ${if (isDark) "#444" else "#e0e0e0"};
                        padding: 8px 12px;
                        text-align: left;
                    }
                    th {
                        background-color: ${if (isDark) "#2a2a2a" else "#f5f5f7"};
                    }
                    strong { color: ${if (isDark) "#ffffff" else "#1a1a1a"}; }
                    a { color: $linkColor; }
                    img {
                        max-width: 100%;
                        height: auto;
                        border-radius: 8px;
                        margin: 12px 0;
                    }
                    body {
                        overflow-x: hidden;
                        word-wrap: break-word;
                        word-break: break-all;
                    }
                    p, div, span {
                        word-break: break-all;
                    }
                </style>
                </head>
                <body>
                <h1 style="font-size:22px;margin-top:0;margin-bottom:8px;">$title</h1>
                <p style="color:${if (isDark) "#999" else "#999"};font-size:13px;margin-bottom:24px;">$category · $date</p>
                $content
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }

        // 优先用列表页直接传过来的文章内容，绝对不会文不对题
        if (directHtml.isNotEmpty()) {
            renderHtml(directTitle, directCategory, directDate, directHtml)
        }
    }
}
