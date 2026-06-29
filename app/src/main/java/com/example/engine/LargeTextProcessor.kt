package com.example.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.db.AppDatabase
import com.example.db.LogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LargeTextProcessor {

    private const val TAG = "LargeTextProcessor"
    private const val LIMIT_5MB = 5 * 1024 * 1024 // 5MB character/byte threshold

    /**
     * Entry point to handle text processing, routing small texts directly to UniversalActionHandler,
     * and chunking larger texts (>5MB) to process them asynchronously with progress alerts.
     */
    fun processLargeText(context: Context, text: String, action: String) {
        val appContext = context.applicationContext
        
        // If text size is under the 5MB limit, process it immediately in IO scope.
        if (text.length < LIMIT_5MB) {
            CoroutineScope(Dispatchers.IO).launch {
                val result = UniversalActionHandler.handleAction(appContext, action, text)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, result.message, Toast.LENGTH_LONG).show()
                }
            }
            return
        }

        // Otherwise, process large text using chunking logic.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                showToast(appContext, "⚡ جاري معالجة نص ضخم (> 5MB) وتقسيمه...")
                
                // 1. Choose Split Strategy
                val chunks = splitTextIntoChunks(text)
                val totalChunks = chunks.size
                
                if (totalChunks == 0) {
                    showToast(appContext, "⚠️ فشل تقسيم النص أو النص فارغ!")
                    return@launch
                }

                showToast(appContext, "📦 تم تقسيم النص إلى $totalChunks كتل برمجية/منطقية.")

                // Create unique output directory for this split session
                val projectDir = ProjectContextManager.getCurrentProjectDir(appContext)
                val sessionDirName = "LargeTexts_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                val outputDir = File(projectDir, sessionDirName)
                outputDir.mkdirs()

                val database = AppDatabase.getDatabase(appContext)
                
                // 2. Process each chunk
                for ((index, chunk) in chunks.withIndex()) {
                    val partIndex = index + 1
                    
                    // Show progress on main thread
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "🔄 جاري معالجة $partIndex/$totalChunks كتلة...", Toast.LENGTH_SHORT).show()
                    }

                    // Save chunk as file
                    val extension = if (chunk.contains("<html") || chunk.contains("<!doctype") || action == "convert_beautify") "html" else "txt"
                    val file = File(outputDir, "chat_part_$partIndex.$extension")
                    file.writeText(chunk, Charsets.UTF_8)

                    // Track log
                    database.dao().insertLog(
                        LogEntity(
                            type = "large_text_chunk",
                            message = "محرك النصوص الضخمة: تم حفظ الجزء $partIndex",
                            details = "المسار: ${file.absolutePath}\nالحجم: ${chunk.length} حرف",
                            source = "large_text_processor"
                        )
                    )

                    // Execute action on this chunk via UniversalActionHandler
                    UniversalActionHandler.handleAction(appContext, action, chunk)
                }

                // Final Success Notification
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "✅ اكتملت معالجة النص الضخم بنجاح! تم تقسيم وحفظ $totalChunks كتل في المجلد ${outputDir.name}", Toast.LENGTH_LONG).show()
                }

                database.dao().insertLog(
                    LogEntity(
                        type = "large_text_complete",
                        message = "تمت معالجة النص الضخم بالكامل",
                        details = "تم إنتاج $totalChunks كتل في ${outputDir.absolutePath}",
                        source = "large_text_processor"
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error processing large text", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "⚠️ خطأ في معالجة النص الضخم: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Splits the text based on standard logical boundaries:
     * 1. Builder directive endings (@builder:end)
     * 2. Markdown headers (## or ###)
     * 3. Three consecutive blank lines
     */
    private fun splitTextIntoChunks(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        return when {
            // Option 1: Split at @builder:end directives
            trimmed.contains("@builder:end") -> {
                val parts = trimmed.split("@builder:end")
                val list = mutableListOf<String>()
                for (i in 0 until parts.size - 1) {
                    val chunk = parts[i] + "@builder:end"
                    if (chunk.trim().isNotEmpty()) {
                        list.add(chunk)
                    }
                }
                // Handle any dangling suffix text
                val lastPart = parts.last().trim()
                if (lastPart.isNotEmpty()) {
                    list.add(lastPart)
                }
                list
            }

            // Option 2: Split by Markdown headers (## or ###)
            trimmed.contains(Regex("(?m)^##\\s")) || trimmed.contains(Regex("(?m)^###\\s")) -> {
                val regex = Regex("(?m)^(?=##\\s|###\\s)")
                trimmed.split(regex).map { it.trim() }.filter { it.isNotEmpty() }
            }

            // Option 3: Split by 3 consecutive empty lines
            else -> {
                val regex = Regex("\\n\\s*\\n\\s*\\n")
                trimmed.split(regex).map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    }

    private suspend fun showToast(context: Context, msg: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
