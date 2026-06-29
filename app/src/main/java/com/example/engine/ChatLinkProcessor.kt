package com.example.engine

import android.content.Context
import com.example.db.AppDatabase
import com.example.db.FileEntity
import com.example.db.LogEntity
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

object ChatLinkProcessor {

    sealed class Status {
        object Idle : Status()
        object Downloading : Status()
        data class Processing(val current: Int, val total: Int, val lastSaved: String = "") : Status()
        data class Success(val codeBlocksCount: Int, val textBlocksCount: Int, val details: String) : Status()
        data class Error(val message: String) : Status()
    }

    val statusFlow = MutableStateFlow<Status>(Status.Idle)

    /**
     * Layer 1 & 2: Process Chat link by downloading and extracting platform-specific elements/JSON.
     */
    suspend fun processChatLink(context: Context, urlString: String, projectPath: String) = withContext(Dispatchers.IO) {
        try {
            statusFlow.value = Status.Downloading
            
            val normalizedUrl = normalizeUrl(urlString)
            
            // Download HTML / Content
            val rawResponse = downloadUrl(normalizedUrl)
            if (rawResponse.isBlank()) {
                statusFlow.value = Status.Error("فشل تحميل محتوى الرابط. تأكد من اتصال الإنترنت وصحة الرابط. (قد تمنع المنصة التحميل المباشر بسبب حماية Cloudflare)")
                return@withContext
            }
            
            // Extract Platform-Specific Content
            val cleanContent = extractContent(normalizedUrl, rawResponse)
            if (cleanContent.isBlank()) {
                statusFlow.value = Status.Error("فشل استخراج المحتوى النظيف من المنصة.")
                return@withContext
            }
            
            // Extract blocks using Jsoup parser
            val blocks = extractBlocks(cleanContent)
            if (blocks.isEmpty()) {
                statusFlow.value = Status.Error("لم يتم العثور على أي كتل نصية أو برمجية في الرابط.")
                return@withContext
            }
            
            processExtractedBlocks(context, blocks, projectPath)
            
        } catch (e: Exception) {
            e.printStackTrace()
            statusFlow.value = Status.Error("حدث خطأ غير متوقع أثناء معالجة الرابط: ${e.message}")
        }
    }

    /**
     * Layer 3: Direct processing of raw browser-copypasted chat content (Manual Fallback).
     */
    suspend fun processRawContent(context: Context, rawText: String, projectPath: String) = withContext(Dispatchers.IO) {
        try {
            statusFlow.value = Status.Downloading
            
            if (rawText.isBlank()) {
                statusFlow.value = Status.Error("النص المدخل فارغ!")
                return@withContext
            }
            
            // Parse blocks directly from text using triple backticks
            val blocks = mutableListOf<ContentBlock>()
            val parts = rawText.split("```")
            for (i in parts.indices) {
                val part = parts[i]
                if (i % 2 == 1) {
                    // This is a code block
                    val lines = part.split("\n", limit = 2)
                    val lang = lines.firstOrNull()?.trim() ?: ""
                    val code = if (lines.size > 1) lines[1] else ""
                    if (code.isNotBlank()) {
                        blocks.add(ContentBlock(ContentType.CODE, code, lang))
                    }
                } else {
                    // This is standard text
                    if (part.trim().isNotEmpty()) {
                        blocks.add(ContentBlock(ContentType.TEXT, part.trim()))
                    }
                }
            }
            
            if (blocks.isEmpty()) {
                blocks.add(ContentBlock(ContentType.TEXT, rawText))
            }
            
            processExtractedBlocks(context, blocks, projectPath)
            
        } catch (e: Exception) {
            e.printStackTrace()
            statusFlow.value = Status.Error("حدث خطأ أثناء معالجة النص: ${e.message}")
        }
    }

    private suspend fun processExtractedBlocks(context: Context, blocks: List<ContentBlock>, projectPath: String) {
        val total = blocks.size
        var codeCount = 0
        var textCount = 0
        val savedFiles = mutableListOf<String>()
        
        val baseDir = File(projectPath)
        val cmdContext = CommandContext(
            context = context.applicationContext,
            baseDir = baseDir,
            args = emptyMap(),
            flags = emptyList()
        )
        
        val sharedPrefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val pBuilder = sharedPrefs.getString("prefix_builder", "@builder") ?: "@builder"
        val pExecutor = sharedPrefs.getString("prefix_executor", "@executor") ?: "@executor"
        val pTreedoc = sharedPrefs.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"
        
        val builderSettings = mapOf(
            "absolute_path_handling" to "relative",
            "base_dir" to baseDir.absolutePath,
            "directive_prefixes" to listOf(pBuilder),
            "executor_prefixes" to listOf(pExecutor),
            "treedoc_prefixes" to listOf(pTreedoc)
        )
        val builderEngine = BuilderEngine(context.applicationContext, builderSettings)
        val database = AppDatabase.getDatabase(context.applicationContext)
        
        // Shared Preferences for Chat Link Automator Options
        val linkPrefs = context.getSharedPreferences("LinkAutomatorPrefs", Context.MODE_PRIVATE)
        val extractCode = linkPrefs.getBoolean("extract_code", true)
        val extractText = linkPrefs.getBoolean("extract_text", true)
        val applySmartCapture = linkPrefs.getBoolean("apply_smart_capture", true)
        
        for ((index, block) in blocks.withIndex()) {
            val progressMessage = "جاري معالجة الكتلة ${index + 1} من $total..."
            statusFlow.value = Status.Processing(index + 1, total, progressMessage)
            
            when (block.type) {
                ContentType.CODE -> {
                    if (!extractCode) continue
                    // Process with BuilderEngine
                    val results = builderEngine.processText(block.content)
                    var savedSnippet = false
                    for (res in results) {
                        if (res.type == "builder") {
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
                                    message = "مؤتمت الروابط الذكي: تم إنشاء $path",
                                    details = "المسار: $fullPath",
                                    source = "chat_link_automator"
                                )
                            )
                            savedFiles.add(path)
                            savedSnippet = true
                        }
                    }
                    if (!savedSnippet) {
                        // Save standalone code snippet
                        val ext = if (block.language.isNotEmpty()) block.language else "txt"
                        val codeFile = SmartCaptureEngine.getUniqueFile(baseDir, "chat_snippet_${System.currentTimeMillis() / 1000}", ext)
                        codeFile.writeText(block.content)
                        database.dao().insertFile(
                            FileEntity(path = codeFile.name, fullPath = codeFile.absolutePath, size = codeFile.length(), mode = "w")
                        )
                        database.dao().insertLog(
                            LogEntity(
                                type = "builder",
                                message = "مؤتمت الروابط: تم حفظ مقتطف برمجية (${block.language})",
                                details = codeFile.absolutePath,
                                source = "chat_link_automator"
                            )
                        )
                        savedFiles.add(codeFile.name)
                    }
                    codeCount++
                }
                ContentType.TEXT, ContentType.HTML -> {
                    if (!extractText) continue
                    if (applySmartCapture) {
                        // Process with SmartCaptureEngine
                        val res = SmartCaptureEngine.processCapturedText(block.content, cmdContext)
                        if (res.savedFiles.isNotEmpty()) {
                            savedFiles.addAll(res.savedFiles.map { it.fileName })
                        }
                    } else {
                        // Simple raw save
                        val txtFile = SmartCaptureEngine.getUniqueFile(baseDir, "chat_text_${System.currentTimeMillis() / 1000}", "txt")
                        txtFile.writeText(block.content)
                        database.dao().insertFile(
                            FileEntity(path = txtFile.name, fullPath = txtFile.absolutePath, size = txtFile.length(), mode = "w")
                        )
                        database.dao().insertLog(
                            LogEntity(
                                type = "capture",
                                message = "مؤتمت الروابط: تم حفظ نص خام",
                                details = txtFile.absolutePath,
                                source = "chat_link_automator"
                            )
                        )
                        savedFiles.add(txtFile.name)
                    }
                    textCount++
                }
            }
        }
        
        val detailsString = if (savedFiles.isNotEmpty()) {
            "الملفات المحفوظة: " + savedFiles.distinct().joinToString("، ")
        } else {
            "لم يتم إنشاء ملفات جديدة (ربما المحتوى مكرر أو الخيارات معطلة)."
        }
        
        statusFlow.value = Status.Success(codeCount, textCount, detailsString)
    }

    private fun normalizeUrl(urlString: String): String {
        var url = urlString.trim()
        if (url.contains("gist.github.com") && !url.endsWith("/raw")) {
            url = if (url.endsWith("/")) url + "raw" else "$url/raw"
        } else if (url.contains("rentry.co") && !url.endsWith("/raw")) {
            url = if (url.endsWith("/")) url + "raw" else "$url/raw"
        }
        return url
    }

    private fun downloadUrl(urlString: String): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            // Mimic authentic desktop browser
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            conn.setRequestProperty("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
            conn.setRequestProperty("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
            conn.setRequestProperty("Sec-Ch-Ua-Mobile", "?0")
            conn.setRequestProperty("Sec-Ch-Ua-Platform", "\"Windows\"")
            
            conn.instanceFollowRedirects = true
            
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun extractContent(url: String, html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            
            // Check for potential Embedded JSON (e.g., deepseek page state or nextjs client data)
            val scriptElements = doc.select("script")
            for (script in scriptElements) {
                val data = script.html()
                if (data.contains("__INITIAL_STATE__") || data.contains("share-data") || data.contains("\"post\"") || data.contains("\"messages\"")) {
                    // Try to scrape content or return the page cleanly
                    break
                }
            }

            when {
                url.contains("chat.deepseek.com") -> {
                    val markdownDiv = doc.selectFirst("div.markdown") ?: doc.selectFirst(".markdown") ?: doc.selectFirst(".chat-message")
                    markdownDiv?.html() ?: doc.body()?.html() ?: html
                }
                url.contains("chatgpt.com") -> {
                    val article = doc.selectFirst("article") ?: doc.selectFirst("div.conversation") ?: doc.selectFirst(".markdown")
                    article?.html() ?: doc.body()?.html() ?: html
                }
                url.contains("pastebin.com/raw") || url.contains("gist.githubusercontent.com") || url.contains("rentry.co") -> {
                    // Raw paste formats
                    "<html><body><pre><code>$html</code></pre></body></html>"
                }
                else -> {
                    extractMainContent(html)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            html
        }
    }

    fun extractMainContent(html: String): String {
        return try {
            val doc = Jsoup.parse(html)
            doc.select("nav, footer, script, style, .sidebar, .ad, header, iframe, noscript").remove()
            doc.body()?.html() ?: html
        } catch (e: Exception) {
            e.printStackTrace()
            html
        }
    }

    private fun extractBlocks(html: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        
        try {
            val doc = Jsoup.parse(html)
            val elements = doc.select("pre, p, h1, h2, h3, li, blockquote")
            for (element in elements) {
                if (element.tagName() == "pre") {
                    val codeEl = element.selectFirst("code") ?: element
                    val codeContent = codeEl.text()
                    if (codeContent.isNotBlank()) {
                        val className = codeEl.className()
                        var language = ""
                        val langMatcher = java.util.regex.Pattern.compile("(?:language|lang)-([a-zA-Z0-9+#-]+)").matcher(className)
                        if (langMatcher.find()) {
                            language = langMatcher.group(1) ?: ""
                        }
                        blocks.add(ContentBlock(ContentType.CODE, codeContent, language))
                    }
                } else {
                    if (element.parents().any { it.tagName() == "pre" }) {
                        continue
                    }
                    
                    val textContent = element.text().trim()
                    if (textContent.isNotBlank()) {
                        val prefix = when (element.tagName().lowercase()) {
                            "h1" -> "# "
                            "h2" -> "## "
                            "h3" -> "### "
                            "li" -> "* "
                            "blockquote" -> "> "
                            else -> ""
                        }
                        blocks.add(ContentBlock(ContentType.TEXT, prefix + textContent))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (blocks.isEmpty()) {
            val cleanText = stripHtmlTags(unescapeHtml(html)).trim()
            if (cleanText.isNotEmpty()) {
                blocks.add(ContentBlock(ContentType.TEXT, cleanText))
            }
        }
        
        return blocks
    }

    private fun unescapeHtml(input: String): String {
        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
    }

    private fun stripHtmlTags(input: String): String {
        return input.replace("<[^>]*>".toRegex(), "")
    }
}
