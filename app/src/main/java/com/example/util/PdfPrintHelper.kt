package com.example.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Handler
import android.os.Looper

/**
 * مساعد تصدير وطباعة ملفات PDF من نصوص HTML تفاعلية بالكامل.
 * يقوم بتهيئة WebView غير مرئي في المسار الرئيسي ومن ثم توجيه صفحات الطباعة لنظام التشغيل.
 */
object PdfPrintHelper {
    fun printHtml(context: Context, htmlContent: String, jobName: String = "TreeDoc_Report") {
        Handler(Looper.getMainLooper()).post {
            try {
                val webView = WebView(context)
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        try {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            val printAdapter = webView.createPrintDocumentAdapter(jobName)
                            val printAttributes = PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                .build()
                            printManager.print(jobName, printAdapter, printAttributes)
                        } catch (e: Exception) {
                            android.util.Log.e("PdfPrintHelper", "فشل تشغيل محول الطباعة: ${e.message}")
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
            } catch (e: Exception) {
                android.util.Log.e("PdfPrintHelper", "فشل تهيئة WebView للطباعة: ${e.message}")
            }
        }
    }
}
