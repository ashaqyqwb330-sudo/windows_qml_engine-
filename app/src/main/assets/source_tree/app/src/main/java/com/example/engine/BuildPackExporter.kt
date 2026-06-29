package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object BuildPackExporter {

    fun splitSmartBlocks(text: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val lines = text.split(Regex("\\r?\\n"))
        var currentBlockContent = java.lang.StringBuilder()
        var inCodeBlock = false

        fun commitCurrentBlock() {
            val content = currentBlockContent.toString().trim()
            if (content.isNotEmpty()) {
                val isCode = content.startsWith("```") && content.endsWith("```")
                val type = when {
                    isCode -> ContentType.CODE
                    content.lowercase(java.util.Locale.ROOT).contains("<!doctype html>") || content.lowercase(java.util.Locale.ROOT).contains("<html") -> ContentType.HTML
                    else -> ContentType.TEXT
                }
                if (type == ContentType.CODE) {
                    val linesOfCode = content.lines()
                    val firstLine = linesOfCode.first()
                    val lang = firstLine.substring(3).trim()
                    val codeBody = linesOfCode.drop(1).dropLast(1).joinToString("\n")
                    blocks.add(ContentBlock(type, codeBody, lang))
                } else {
                    blocks.add(ContentBlock(type, content))
                }
                currentBlockContent = java.lang.StringBuilder()
            }
        }

        var consecutiveEmptyLines = 0

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                currentBlockContent.append(line).append("\n")
                consecutiveEmptyLines = 0
                continue
            }

            if (inCodeBlock) {
                currentBlockContent.append(line).append("\n")
                consecutiveEmptyLines = 0
                continue
            }

            val isHeader = trimmed.startsWith("##")
            val isHr = trimmed == "---"
            if (trimmed.isEmpty()) {
                consecutiveEmptyLines++
            } else {
                consecutiveEmptyLines = 0
            }
            val isTripleN = consecutiveEmptyLines >= 3

            if (isHeader || isHr || isTripleN) {
                commitCurrentBlock()
                if (isTripleN) {
                    consecutiveEmptyLines = 0
                }
            }

            currentBlockContent.append(line).append("\n")
        }

        commitCurrentBlock()
        return blocks
    }

    fun extractFirstLineTitle(content: String, context: Context): String {
        val firstLine = content.lines().firstOrNull { it.trim().isNotEmpty() } ?: "block"
        var clean = firstLine.trim()
            .replace(Regex("^#+\\s*"), "")
            .replace(Regex("^\\*+\\s*"), "")
            .replace(Regex("^-\\s*"), "")
            .replace(Regex("[\\*_\\[\\]`]"), "")
        val sanitized = com.example.engine.SmartCaptureEngine.sanitizeFileName(clean, context)
        return sanitized.replace(Regex("\\s+"), "_").trim().ifEmpty { "block" }
    }

    fun detectExtension(text: String, convertMdToHtml: Boolean): String {
        val trimmedLower = text.trim().lowercase(java.util.Locale.ROOT)
        if (trimmedLower.contains("<html") || trimmedLower.contains("<!doctype html>") || trimmedLower.contains("<title>") || trimmedLower.contains("<body") || trimmedLower.contains("<div")) {
            return "html"
        }
        if (convertMdToHtml) {
            return "html"
        }
        val hasMd = text.contains(Regex("(^#+\\s)|(^-\\s)|(^\\*\\s)|(\\*\\*.+\\*\\*)|(```)", RegexOption.MULTILINE))
        return if (hasMd) "md" else "txt"
    }

    fun wrapTextWithMode(context: Context, text: String): Pair<String, Int> {
        val smartPrefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val mode = smartPrefs.getString("text_packaging_mode", "bundled") ?: "bundled"
        val convertMdToHtml = smartPrefs.getBoolean("convert_md_to_html_on_pack", false)
        
        val capturePrefs = context.getSharedPreferences("SmartCapturePrefs", Context.MODE_PRIVATE)
        val theme = capturePrefs.getString("document_theme", "dark") ?: "dark"

        return when (mode) {
            "smart" -> {
                val blocks = splitSmartBlocks(text)
                if (blocks.isEmpty()) {
                    return Pair("", 0)
                }
                val sb = StringBuilder()
                for ((index, block) in blocks.withIndex()) {
                    val title = extractFirstLineTitle(block.content, context)
                    val cleanTitle = title.replace(Regex("\\s+"), "_").trim().ifEmpty { "block_$index" }
                    
                    val ext: String
                    val content: String
                    
                    if (block.type == com.example.engine.ContentType.TEXT) {
                        ext = detectExtension(block.content, convertMdToHtml)
                        content = if (ext == "html") {
                            com.example.engine.SmartCaptureEngine.generateHtmlWrapper(title, "نص", block.content, theme)
                        } else {
                            block.content
                        }
                    } else if (block.type == com.example.engine.ContentType.HTML) {
                        ext = "html"
                        content = block.content
                    } else {
                        ext = com.example.engine.SmartCaptureEngine.languageToExtension(block.language)
                        content = block.content
                    }
                    val fileName = "$cleanTitle.$ext"
                    
                    val comment = when (ext) {
                        "html", "xml" -> "<!-- ملف: $fileName -->"
                        "md", "txt" -> "[//]: # (ملف: $fileName)"
                        "py", "sh", "rb", "yml", "yaml", "ini", "properties" -> "# ملف: $fileName"
                        "kt", "java", "js", "ts", "css", "cpp", "c", "cs" -> "// ملف: $fileName"
                        else -> "// ملف: $fileName"
                    }
                    
                    sb.append("@builder:file $fileName\n")
                    sb.append(comment).append("\n")
                    sb.append(content).append("\n")
                    sb.append("@builder:end")
                    
                    if (index < blocks.size - 1) {
                        sb.append("\n\n─── ✂️ الملف التالي ✂️ ───\n\n")
                    }
                }
                Pair(sb.toString(), blocks.size)
            }
            "raw" -> {
                val wrapped = "@builder:file captured_text.txt\n[//]: # (ملف: captured_text.txt)\n$text\n@builder:end"
                Pair(wrapped, 1)
            }
            else -> { // "bundled"
                val rawTitle = com.example.engine.SmartCaptureEngine.extractSmartTitle(com.example.engine.ContentBlock(com.example.engine.ContentType.TEXT, text), context)
                val sanitizedTitle = com.example.engine.SmartCaptureEngine.sanitizeFileName(rawTitle, context)
                val cleanTitle = sanitizedTitle.replace(Regex("\\s+"), "_").trim().ifEmpty { "bundled_document" }
                
                val ext = detectExtension(text, convertMdToHtml)
                val content = if (ext == "html") {
                    if (text.contains("<html", ignoreCase = true) || text.contains("<!doctype html", ignoreCase = true)) {
                        text
                    } else {
                        com.example.engine.SmartCaptureEngine.generateHtmlWrapper(rawTitle, "نص", text, theme)
                    }
                } else {
                    text
                }
                val fileName = "$cleanTitle.$ext"
                val comment = when (ext) {
                    "html" -> "<!-- ملف: $fileName -->"
                    "md", "txt" -> "[//]: # (ملف: $fileName)"
                    else -> "// ملف: $fileName"
                }
                val wrapped = "@builder:file $fileName\n$comment\n$content\n@builder:end"
                Pair(wrapped, 1)
            }
        }
    }

    fun wrapMultipleBlocks(context: Context, blocks: List<com.example.engine.ContentBlock>): String {
        val sb = StringBuilder()
        for ((index, block) in blocks.withIndex()) {
            val title = com.example.engine.SmartCaptureEngine.extractSmartTitle(block, context)
            val sanitizedTitle = com.example.engine.SmartCaptureEngine.sanitizeFileName(title, context)
            val cleanTitle = sanitizedTitle.replace(Regex("\\s+"), "_").trim()
            
            val ext = when (block.type) {
                com.example.engine.ContentType.HTML -> "html"
                com.example.engine.ContentType.TEXT -> "md"
                com.example.engine.ContentType.CODE -> com.example.engine.SmartCaptureEngine.languageToExtension(block.language)
            }
            val fileName = "$cleanTitle.$ext"
            
            val comment = when (ext) {
                "html", "xml" -> "<!-- ملف: $fileName -->"
                "md", "txt" -> "[//]: # (ملف: $fileName)"
                "py", "sh", "rb", "yml", "yaml", "ini", "properties" -> "# ملف: $fileName"
                "kt", "java", "js", "ts", "css", "cpp", "c", "cs" -> "// ملف: $fileName"
                else -> "// ملف: $fileName"
            }
            
            sb.append("@builder:file $fileName\n")
            sb.append(comment).append("\n")
            sb.append(block.content).append("\n")
            sb.append("@builder:end")
            
            if (index < blocks.size - 1) {
                sb.append("\n\n─── ✂️ الملف التالي ✂️ ───\n\n")
            }
        }
        return sb.toString()
    }

    fun wrapSingleText(context: Context, text: String) {
        val wrapped = "@builder:file clipboard_text.txt\n$text\n@builder:end"
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Build Pack", wrapped))
            Toast.makeText(context, "📦 تم نسخ حزمة البناء إلى الحافظة", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "فشل نسخ حزمة البناء: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun generateAndCopy(context: Context): String {
        val prefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val scanMode = prefs.getString("scan_mode", "ACTIVE_PROJECT") ?: "ACTIVE_PROJECT"
        val exportFormat = prefs.getString("export_format", "PLAIN") ?: "PLAIN"
        val includeNonCode = prefs.getBoolean("include_non_code", false)
        val includeSubfolders = prefs.getBoolean("include_subfolders", true)

        val activeDir = ProjectContextManager.getCurrentProjectDir(context)
        val scanDir = when (scanMode) {
            "CODE_ONLY" -> File(activeDir, "SmartInbox/code")
            "CUSTOM" -> {
                val customPath = prefs.getString("custom_scan_path", "") ?: ""
                if (customPath.isNotEmpty()) File(customPath) else activeDir
            }
            else -> activeDir // "ACTIVE_PROJECT"
        }

        if (!scanDir.exists() || !scanDir.isDirectory) {
            val errorMsg = "الخطأ: مجلد المسح غير موجود أو غير صالح."
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            return ""
        }

        // Load ignore patterns from .buildignore
        val ignorePaths = mutableListOf<String>()
        val loadedIgnores = mutableSetOf<String>()
        
        fun loadIgnoreFile(f: File) {
            if (f.exists() && f.isFile) {
                try {
                    f.readLines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            if (loadedIgnores.add(trimmed)) {
                                ignorePaths.add(trimmed)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors reading the file
                }
            }
        }
        
        loadIgnoreFile(File(scanDir, ".buildignore"))
        if (activeDir != scanDir) {
            loadIgnoreFile(File(activeDir, ".buildignore"))
        }

        val fileList = mutableListOf<File>()
        try {
            if (includeSubfolders) {
                scanDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(scanDir).path
                        val parts = relativePath.split(File.separator)
                        if (parts.none { it in BuilderEngine.IGNORE_DIRS }) {
                            if (!isIgnored(relativePath, file.name, ignorePaths)) {
                                if (includeNonCode || isCodeFile(file)) {
                                    fileList.add(file)
                                }
                            }
                        }
                    }
                }
            } else {
                scanDir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(scanDir).path
                        if (!isIgnored(relativePath, file.name, ignorePaths)) {
                            if (includeNonCode || isCodeFile(file)) {
                                fileList.add(file)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "فشل مسح الملفات: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        if (fileList.isEmpty()) {
            val emptyMsg = "لا توجد ملفات لمطابقتها وتصديرها."
            Toast.makeText(context, emptyMsg, Toast.LENGTH_SHORT).show()
            return ""
        }

        val generatedText = when (exportFormat) {
            "JSON" -> generateJsonFormat(fileList, scanDir)
            "MARKDOWN" -> generateMarkdownFormat(fileList, scanDir)
            else -> generatePlainFormat(fileList, scanDir)
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Build Pack", generatedText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "📦 تم تجهيز ${fileList.size} ملفًا كتوجيهات بناء وسخها للحافظة.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "فشل نسخ حزمة البناء: ${e.message}", Toast.LENGTH_LONG).show()
        }

        return generatedText
    }

    private fun isCodeFile(file: File): Boolean {
        val ext = file.extension.lowercase(java.util.Locale.ROOT)
        val codeExtensions = setOf(
            "kt", "java", "py", "html", "css", "js", "json", "xml", "cpp", "h", "c", "sh", "yaml", "yml", "properties", "gradle", "md", "txt"
        )
        return codeExtensions.contains(ext)
    }

    private fun getCommentForFile(relativePath: String, ext: String): String {
        return when (ext) {
            "kt", "java" -> "// ملف: $relativePath\n// اللغة: Kotlin/Java"
            "py" -> "# ملف: $relativePath\n# اللغة: Python"
            "html" -> "<!-- ملف: $relativePath -->\n<!-- اللغة: HTML -->"
            else -> ""
        }
    }

    private fun getLanguageName(ext: String): String {
        return when (ext) {
            "kt" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "html" -> "html"
            "json" -> "json"
            "xml" -> "xml"
            "css" -> "css"
            "js" -> "javascript"
            "sh" -> "bash"
            else -> ext
        }
    }

    private fun generatePlainFormat(fileList: List<File>, scanDir: File): String {
        val sb = StringBuilder()
        for ((index, file) in fileList.withIndex()) {
            val relativePath = file.relativeTo(scanDir).path
            val ext = file.extension.lowercase(java.util.Locale.ROOT)
            val comment = getCommentForFile(relativePath, ext)
            val fileContent = try { file.readText() } catch (e: Exception) { "// فشل قراءة الملف" }

            sb.append("@builder:file ").append(relativePath).append("\n")
            if (comment.isNotEmpty()) {
                sb.append(comment).append("\n")
            }
            sb.append(fileContent).append("\n")
            sb.append("@builder:end")

            if (index < fileList.size - 1) {
                sb.append("\n─── ✂️ الملف التالي ✂️ ───\n\n")
            }
        }
        return sb.toString()
    }

    private fun generateJsonFormat(fileList: List<File>, scanDir: File): String {
        val jsonArray = JSONArray()
        for (file in fileList) {
            val relativePath = file.relativeTo(scanDir).path
            val ext = file.extension.lowercase(java.util.Locale.ROOT)
            val fileContent = try { file.readText() } catch (e: Exception) { "" }
            val obj = JSONObject().apply {
                put("path", relativePath)
                put("content", fileContent)
                put("language", getLanguageName(ext))
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(4)
    }

    private fun generateMarkdownFormat(fileList: List<File>, scanDir: File): String {
        val sb = StringBuilder()
        for (file in fileList) {
            val relativePath = file.relativeTo(scanDir).path
            val ext = file.extension.lowercase(java.util.Locale.ROOT)
            val lang = getLanguageName(ext)
            val fileContent = try { file.readText() } catch (e: Exception) { "" }

            sb.append("## ").append(relativePath).append("\n\n")
            sb.append("```").append(lang).append("\n")
            sb.append(fileContent).append("\n")
            sb.append("```\n\n")
        }
        return sb.toString().trim()
    }

    private fun isIgnored(relativePath: String, filename: String, ignorePatterns: List<String>): Boolean {
        if (ignorePatterns.isEmpty()) return false
        val normPath = relativePath.replace('\\', '/')
        for (pattern in ignorePatterns) {
            if (matchesPattern(normPath, filename, pattern)) {
                return true
            }
        }
        return false
    }

    private fun matchesPattern(path: String, filename: String, pattern: String): Boolean {
        if (pattern.endsWith("/")) {
            return path.startsWith(pattern) || path.contains("/$pattern")
        }
        if (pattern.contains("*")) {
            val regex = pattern
                .replace(".", "\\.")
                .replace("?", ".")
                .replace("*", ".*")
            val r = try { Regex("^" + regex + "$") } catch (e: Exception) { null }
            if (r != null) {
                if (r.matches(filename) || r.matches(path)) return true
                val segments = path.split("/")
                for (seg in segments) {
                    if (r.matches(seg)) return true
                }
            }
            return false
        }
        return path == pattern || filename == pattern || path.endsWith("/$pattern")
    }
}
