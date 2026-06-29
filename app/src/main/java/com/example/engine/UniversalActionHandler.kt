package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.example.db.AppDatabase
import com.example.db.FileEntity
import com.example.db.LogEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class UniversalActionResult(
    val success: Boolean,
    val message: String,
    val details: String? = null
)

object UniversalActionHandler {

    suspend fun handleAction(context: Context, action: String, text: String): UniversalActionResult = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return@withContext UniversalActionResult(false, "⚠️ النص المدخل فارغ!")
        }

        // 1. Check size of the text
        val LIMIT_5MB = 5 * 1024 * 1024
        if (trimmed.length >= LIMIT_5MB) {
            withContext(Dispatchers.Main) {
                LargeTextProcessor.processLargeText(context, text, action)
            }
            return@withContext UniversalActionResult(true, "⚡ جاري تقسيم ومعالجة النص الضخم (> 5MB) في الخلفية...")
        }

        // 2. Resolve action type based on text content
        val sp = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val pBuilder = sp.getString("prefix_builder", "@builder") ?: "@builder"
        val pExecutor = sp.getString("prefix_executor", "@executor") ?: "@executor"

        var resolvedAction = action
        if (trimmed.contains(pBuilder) || trimmed.contains(pExecutor) || trimmed.contains("@builder:file") || trimmed.contains("@executor:")) {
            resolvedAction = "execute_commands"
        } else if (trimmed.contains("# ") || trimmed.contains("**") || trimmed.contains("`") || trimmed.contains("|") || (!trimmed.contains("{") && !trimmed.contains("["))) {
            resolvedAction = "smart_capture"
        }

        return@withContext try {
            when (resolvedAction) {
                "smart_capture" -> {
                    val baseDir = ProjectContextManager.getCurrentProjectDir(context)
                    val cmdContext = CommandContext(
                        context = context.applicationContext,
                        baseDir = baseDir,
                        args = emptyMap(),
                        flags = emptyList()
                    )
                    val res = SmartCaptureEngine.processCapturedText(text, cmdContext)
                    if (res.savedFiles.isNotEmpty()) {
                        val fileNames = res.savedFiles.joinToString("، ") { it.fileName }
                        UniversalActionResult(true, "✅ تم تحليل النص وحفظه بنجاح", "الملفات: $fileNames")
                    } else if (res.ignoredDuplicates > 0) {
                        UniversalActionResult(true, "ℹ️ تم تجاهل النص لأنه مكرر مسبقاً", null)
                    } else if (res.ignoredShortTexts > 0) {
                        UniversalActionResult(false, "⚠️ تم تجاهل النص لأنه قصير جداً", null)
                    } else {
                        val errorMsg = if (res.errors.isNotEmpty()) res.errors.joinToString("\n") else "فشل المعالجة"
                        UniversalActionResult(false, "⚠️ فشل في حفظ الملفات المكتشفة", errorMsg)
                    }
                }

                "execute_commands", "apply_build_pack" -> {
                    val sharedPrefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                    val pBuilder = sharedPrefs.getString("prefix_builder", "@builder") ?: "@builder"
                    val pExecutor = sharedPrefs.getString("prefix_executor", "@executor") ?: "@executor"
                    val pTreedoc = sharedPrefs.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"

                    val settings = mapOf(
                        "absolute_path_handling" to "relative",
                        "base_dir" to ProjectContextManager.getCurrentProjectDir(context).absolutePath,
                        "directive_prefixes" to listOf(pBuilder),
                        "executor_prefixes" to listOf(pExecutor),
                        "treedoc_prefixes" to listOf(pTreedoc)
                    )
                    val engine = BuilderEngine(context.applicationContext, settings)
                    val results = engine.processText(text)
                    
                    var buildersCount = 0
                    val database = AppDatabase.getDatabase(context.applicationContext)
                    for (res in results) {
                        if (res.type == "builder") {
                            buildersCount++
                            val path = res.data?.get("path") ?: "unknown"
                            val size = res.data?.get("size")?.toLongOrNull() ?: 0L
                            val mode = res.data?.get("mode") ?: "w"
                            val fullPath = res.data?.get("full_path") ?: ""
                            database.dao().insertFile(
                                FileEntity(path = path, fullPath = fullPath, size = size, mode = mode)
                            )
                            database.dao().insertLog(
                                LogEntity(
                                    type = "builder",
                                    message = "نظام الوصول الشامل: تم إنشاء $path",
                                    details = "المسار: $fullPath",
                                    source = "universal_handler"
                                )
                            )
                        } else {
                            database.dao().insertLog(
                                LogEntity(
                                    type = res.type,
                                    message = "نظام الوصول الشامل: إجراء ${res.type}",
                                    details = res.message,
                                    source = "universal_handler"
                                )
                            )
                        }
                    }
                    if (results.isNotEmpty()) {
                        UniversalActionResult(true, "⚙️ تم تنفيذ الأوامر وتطبيق الحزم بنجاح", "تم معالجة ${results.size} عمليات بنجاح.")
                    } else {
                        UniversalActionResult(false, "⚠️ لم يتم العثور على أي أوامر أو حزم بناء صالحة لتنفيذها", null)
                    }
                }

                "build_pack" -> {
                    val (wrappedContent, count) = BuildPackExporter.wrapTextWithMode(context, text)
                    if (wrappedContent.isNotEmpty() && count > 0) {
                        withContext(Dispatchers.Main) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Build Pack Wrapped", wrappedContent))
                        }
                        UniversalActionResult(true, "📦 تم تجميع وتجهيز $count كتل برمجية في الحافظة كحزمة بناء!", null)
                    } else {
                        UniversalActionResult(false, "⚠️ لا توجد كتل برمجية/نصية صالحة لتغليفها!", null)
                    }
                }

                "quick_capture" -> {
                    val sp = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                    val draftFolderRel = sp.getString("action_quick_capture_draft_path", "/Drafts") ?: "/Drafts"
                    val activeProjectDir = ProjectContextManager.getCurrentProjectDir(context)
                    
                    val cleanFolderRel = draftFolderRel.trim().removePrefix("/").removeSuffix("/")
                    val targetDir = if (cleanFolderRel.isEmpty()) activeProjectDir else File(activeProjectDir, cleanFolderRel)
                    
                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                    val file = SmartCaptureEngine.getUniqueFile(targetDir, "QuickCapture_$dateStr", "txt")
                    file.parentFile?.mkdirs()
                    file.writeText(text, Charsets.UTF_8)
                    
                    val database = AppDatabase.getDatabase(context.applicationContext)
                    database.dao().insertLog(
                        LogEntity(
                            type = "quick_capture",
                            message = "التقاط سريع: تم حفظ الملف ${file.name}",
                            details = "المسار: ${file.absolutePath}",
                            source = "universal_handler"
                        )
                    )
                    UniversalActionResult(true, "📥 تم التقاط النص وحفظه مباشرة في ملف: ${file.name}", "المجلد: $cleanFolderRel")
                }

                "convert_beautify" -> {
                    val sp = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                    val documentTheme = sp.getString("document_theme", "dark") ?: "dark"
                    
                    val htmlContent = SmartCaptureEngine.generateHtmlWrapper("مستند منسق", "تحويل وتجميل", text, documentTheme)
                    
                    val activeProjectDir = ProjectContextManager.getCurrentProjectDir(context)
                    val targetDir = File(activeProjectDir, "Beautified")
                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                    val file = SmartCaptureEngine.getUniqueFile(targetDir, "Beautified_$dateStr", "html")
                    file.parentFile?.mkdirs()
                    file.writeText(htmlContent, Charsets.UTF_8)
                    
                    withContext(Dispatchers.Main) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Beautified HTML", htmlContent))
                    }
                    
                    val database = AppDatabase.getDatabase(context.applicationContext)
                    database.dao().insertLog(
                        LogEntity(
                            type = "convert_beautify",
                            message = "تحويل وتجميل: تم تجميل النص وحفظه كملف HTML",
                            details = "المسار: ${file.absolutePath}",
                            source = "universal_handler"
                        )
                    )
                    UniversalActionResult(true, "🎨 تم تجميل وتحويل النص إلى HTML منسق ونسخه للحافظة!", "الملف: ${file.name}")
                }

                "auto_detect" -> {
                    val sp = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                    val pBuilder = sp.getString("prefix_builder", "@builder") ?: "@builder"
                    val pExecutor = sp.getString("prefix_executor", "@executor") ?: "@executor"
                    val pTreedoc = sp.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"
                    
                    val hasBuilder = text.contains(pBuilder)
                    val hasExecutor = text.contains(pExecutor) || text.contains(pTreedoc)
                    
                    val nextAction = when {
                        hasBuilder || hasExecutor -> "execute_commands"
                        trimmed.startsWith("<html", ignoreCase = true) || trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) -> "quick_capture"
                        text.contains("# ") || text.contains("**") || text.contains("`") || text.contains("|") -> "convert_beautify"
                        else -> "smart_capture"
                    }
                    
                    Log.d("UniversalActionHandler", "Auto-detected action for content: $nextAction")
                    handleAction(context, nextAction, text)
                }

                else -> UniversalActionResult(false, "⚠️ إجراء غير معروف: $action")
            }
        } catch (e: Exception) {
            Log.e("UniversalActionHandler", "Error executing action: $action", e)
            UniversalActionResult(false, "⚠️ حدث خطأ أثناء المعالجة: ${e.localizedMessage}")
        }
    }
}
