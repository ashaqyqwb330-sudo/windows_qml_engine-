package com.example.service

import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextActionSmartCapture : BaseTextActionActivity() {

    override fun handleText(text: String) {
        val baseDir = com.example.engine.ProjectContextManager.getCurrentProjectDir(this)
        val cmdContext = com.example.engine.CommandContext(
            context = applicationContext,
            baseDir = baseDir,
            args = emptyMap(),
            flags = emptyList()
        )
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.example.engine.SmartCaptureEngine.processCapturedText(text, cmdContext)
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "✅ تم تحليل النص وحفظه في SmartInbox", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "⚠️ خطأ أثناء التحليل: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
