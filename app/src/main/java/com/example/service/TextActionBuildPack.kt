package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

class TextActionBuildPack : BaseTextActionActivity() {

    override fun handleText(text: String) {
        try {
            val (wrappedContent, count) = com.example.engine.BuildPackExporter.wrapTextWithMode(this, text)
            if (wrappedContent.isEmpty() || count == 0) {
                Toast.makeText(this, "⚠️ لا توجد كتل نصية صالحة لمعالجتها!", Toast.LENGTH_SHORT).show()
                return
            }
            
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Build Pack Wrapped", wrappedContent))
            
            Toast.makeText(this, "📦 تم تجهيز $count كتل كتوجيهات بناء.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "⚠️ فشل تجهيز حزمة البناء: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
