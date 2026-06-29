package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.example.engine.ProjectContextManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SourceExporter {

    fun getAssetFilesRecursive(context: Context, path: String): List<String> {
        val fileList = mutableListOf<String>()
        val list = try {
            context.assets.list(path) ?: emptyArray()
        } catch (e: Exception) {
            Log.e("SourceExporter", "Error listing assets at path: $path", e)
            emptyArray()
        }
        if (list.isEmpty()) {
            // It might be a file, verify by opening
            var isFile = false
            try {
                context.assets.open(path).use { }
                isFile = true
            } catch (e: Exception) {
                // Ignore
            }
            if (isFile) {
                fileList.add(path)
            }
        } else {
            for (item in list) {
                val subPath = if (path.isEmpty()) item else "$path/$item"
                val subList = try {
                    context.assets.list(subPath) ?: emptyArray()
                } catch (e: Exception) {
                    Log.e("SourceExporter", "Error listing assets at subpath: $subPath", e)
                    emptyArray()
                }
                if (subList.isEmpty()) {
                    var isFile = false
                    try {
                        context.assets.open(subPath).use { }
                        isFile = true
                    } catch (e: Exception) {
                        // Ignore
                    }
                    if (isFile) {
                        fileList.add(subPath)
                    }
                } else {
                    fileList.addAll(getAssetFilesRecursive(context, subPath))
                }
            }
        }
        return fileList
    }

    fun isTextFile(path: String): Boolean {
        val ext = path.substringAfterLast('.').lowercase()
        return ext in listOf("kt", "xml", "kts", "properties", "toml", "txt", "json", "pro")
    }

    suspend fun exportSourceToClipboard(context: Context): Triple<Int, String, Boolean> = withContext(Dispatchers.IO) {
        // Read files recursively from assets under "source_tree"
        val rawFiles = try {
            getAssetFilesRecursive(context, "source_tree")
        } catch (e: Exception) {
            Log.e("SourceExporter", "Failed to list assets recursively under source_tree", e)
            emptyList()
        }
        val filteredFiles = rawFiles.filter { isTextFile(it) }

        // Sort files by priority:
        // Priority 1: build.gradle.kts
        // Priority 2: settings.gradle.kts, gradle.properties, gradle-wrapper.properties, libs.versions.toml
        // Priority 3: AndroidManifest.xml
        // Priority 4: .kt files
        // Priority 5: Rest (resources, XML, etc.)
        val sortedFiles = filteredFiles.sortedWith { assetPath1, assetPath2 ->
            val rel1 = assetPath1.removePrefix("source_tree/")
            val rel2 = assetPath2.removePrefix("source_tree/")

            fun getPriority(path: String): Int {
                val fileName = path.substringAfterLast('/')
                return when {
                    fileName == "build.gradle.kts" -> 1
                    fileName == "settings.gradle.kts" || fileName == "gradle.properties" || fileName == "gradle-wrapper.properties" || fileName == "libs.versions.toml" -> 2
                    fileName == "AndroidManifest.xml" -> 3
                    path.endsWith(".kt") -> 4
                    else -> 5
                }
            }

            val p1 = getPriority(rel1)
            val p2 = getPriority(rel2)
            if (p1 != p2) {
                p1.compareTo(p2)
            } else {
                rel1.compareTo(rel2)
            }
        }

        val resultText = StringBuilder()
        
        // Add a beautiful header
        resultText.append("// =========================================================\n")
        resultText.append("// 📥 حزمة التصدير الذاتي للمنصة الذهبية (Self-Extracting Source Code)\n")
        resultText.append("// تم تصدير هذا الكود تلقائياً بكبسة زر واحدة من داخل التطبيق!\n")
        resultText.append("// =========================================================\n\n")

        for (assetPath in sortedFiles) {
            val relativePath = assetPath.removePrefix("source_tree/")
            try {
                val fileContent = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                
                // Construct @builder block
                resultText.append("// @builder:file $relativePath\n")
                resultText.append(fileContent)
                if (!fileContent.endsWith("\n")) {
                    resultText.append("\n")
                }
                resultText.append("// @builder:end\n\n")
            } catch (e: Exception) {
                Log.e("SourceExporter", "Failed to read asset: $assetPath", e)
            }
        }

        val finalDump = resultText.toString()
        val dumpSizeBytes = finalDump.toByteArray(Charsets.UTF_8).size
        val isTooLarge = dumpSizeBytes > 10 * 1024 * 1024 // 10 MB

        var copiedToClipboard = false
        if (!isTooLarge) {
            try {
                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Golden Source Code Dump", finalDump)
                    clipboard.setPrimaryClip(clip)
                }
                copiedToClipboard = true
            } catch (e: Exception) {
                Log.e("SourceExporter", "Failed to copy to clipboard", e)
            }
        } else {
            Log.w("SourceExporter", "Source dump is too large ($dumpSizeBytes bytes), skipped clipboard copy")
        }

        // Save inside the active project directory under SmartInbox
        try {
            val activeProjectDir = ProjectContextManager.getCurrentProjectDir(context)
            val smartInboxDir = File(activeProjectDir, "SmartInbox")
            if (!smartInboxDir.exists()) {
                smartInboxDir.mkdirs()
            }
            val exportFile = File(smartInboxDir, "Source_Export.txt")
            exportFile.writeText(finalDump)
        } catch (e: Exception) {
            Log.e("SourceExporter", "Failed to write local backup export file", e)
        }

        Triple(sortedFiles.size, finalDump, copiedToClipboard)
    }
}
