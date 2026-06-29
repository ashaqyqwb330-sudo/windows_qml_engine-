package com.example

import com.example.db.LogEntity
import java.io.File
import java.util.regex.Pattern

data class StoryCard(
    val id: String,
    val icon: String,
    val title: String,
    val summary: String,
    val details: String,
    val time: String,
    val relativeTime: String,
    val successCount: Int,
    val totalCount: Int,
    val rawEvents: List<com.example.db.LogEntity>,
    val rawLogsCount: Int,
    val timestamp: Long,
    val filePath: String? = null
)

object LogAggregator {

    private val FILE_PATTERN = Pattern.compile("(?i)\\b[\\w-]+\\.(kt|txt|json|xml|pdf|png|jpg|html|css|js|md|properties|gradle|pro|db)\\b")

    fun generateStoryCards(rawLogs: List<LogEntity>, mode: String): List<StoryCard> {
        val logs = rawLogs.take(50).sortedBy { it.timestamp }

        val groups = mutableListOf<MutableList<LogEntity>>()
        
        for (log in logs) {
            val lastGroup = groups.lastOrNull()
            if (lastGroup == null) {
                groups.add(mutableListOf(log))
            } else {
                val lastLogInGroup = lastGroup.last()
                val timeDifference = Math.abs(log.timestamp - lastLogInGroup.timestamp)
                
                val currentFile = extractFileName(log)
                val lastFile = extractFileName(lastGroup.first())
                
                val belongsToSameFile = currentFile != null && lastFile != null && currentFile.equals(lastFile, ignoreCase = true)
                val belongsToSameTypeAndNearTime = log.type == lastLogInGroup.type && timeDifference < 15000 // 15 seconds
                
                if (belongsToSameFile || belongsToSameTypeAndNearTime) {
                    lastGroup.add(log)
                } else {
                    groups.add(mutableListOf(log))
                }
            }
        }

        val storyCards = groups.map { group ->
            createStoryCardFromGroup(group, mode)
        }

        return storyCards.reversed()
    }

    private fun extractFileName(log: LogEntity): String? {
        var matcher = FILE_PATTERN.matcher(log.message)
        if (matcher.find()) {
            return matcher.group()
        }
        val details = log.details ?: ""
        matcher = FILE_PATTERN.matcher(details)
        if (matcher.find()) {
            return matcher.group()
        }
        
        val pathKeywords = listOf("المسار:", "تعديل: ", "تم إنشاء ")
        for (kw in pathKeywords) {
            if (log.message.contains(kw)) {
                val temp = log.message.substringAfter(kw).trim().split(" ").firstOrNull()?.trim()
                if (!temp.isNullOrBlank() && (temp.contains("/") || temp.contains("."))) {
                    return File(temp).name
                }
            }
        }
        return null
    }

    private fun isSuccessfulLog(log: LogEntity): Boolean {
        val msg = log.message.lowercase()
        val dtl = (log.details ?: "").lowercase()
        val hasError = msg.contains("فشل") || msg.contains("fail") || msg.contains("error") ||
                       dtl.contains("error") || dtl.contains("exit status 1") || dtl.contains("code 1") ||
                       msg.contains("خطأ") || dtl.contains("خطأ")
        return !hasError
    }

    private fun extractFilePath(log: LogEntity): String? {
        val pathKeywords = listOf("المسار:", "تعديل: ", "تم إنشاء ")
        for (kw in pathKeywords) {
            if (log.message.contains(kw)) {
                val temp = log.message.substringAfter(kw).trim().split(" ").firstOrNull()?.trim()
                if (!temp.isNullOrBlank() && (temp.contains("/") || temp.contains("."))) {
                    return temp
                }
            }
        }
        val details = log.details ?: ""
        for (kw in pathKeywords) {
            if (details.contains(kw)) {
                val temp = details.substringAfter(kw).trim().split(" ").firstOrNull()?.trim()
                if (!temp.isNullOrBlank() && (temp.contains("/") || temp.contains("."))) {
                    return temp
                }
            }
        }
        // Match standard file pattern in message
        val matcher = FILE_PATTERN.matcher(log.message)
        if (matcher.find()) {
            return matcher.group()
        }
        val matcherDetails = FILE_PATTERN.matcher(details)
        if (matcherDetails.find()) {
            return matcherDetails.group()
        }
        return null
    }

    private fun createStoryCardFromGroup(group: List<LogEntity>, mode: String): StoryCard {
        val firstLog = group.first()
        val latestLog = group.last()
        val count = group.size
        
        val type = firstLog.type
        val fileName = extractFileName(firstLog)
        
        val icon = when {
            type == "builder" || fileName?.endsWith(".kt") == true -> "💻"
            fileName?.endsWith(".pdf") == true -> "📄"
            fileName?.endsWith(".json") == true || fileName?.endsWith(".xml") == true -> "⚙️"
            type == "clipboard_service" || type == "clipboard_monitor" -> "📋"
            type == "smart_capture" -> "📥"
            type == "gemini" -> "🧠"
            type == "executor" -> "🛡️"
            else -> "📝"
        }

        val relativeTime = getRelativeTime(latestLog.timestamp)
        val successCount = group.count { isSuccessfulLog(it) }
        val totalCount = group.size
        val filePath = extractFilePath(latestLog) ?: extractFilePath(firstLog)

        val (title, summary, details) = when (mode) {
            "developer" -> {
                val displayFile = fileName ?: "ملف المشروع"
                val act = when {
                    count > 1 -> "⚙️ إدارة وتعديل الملفات البرمجية"
                    type == "builder" -> "💻 بناء وتجميع الأكواد"
                    type == "smart_capture" -> "📥 التقاط ذكي للمحتوى المنسوخ"
                    type == "executor" -> "🛡️ تنفيذ أمر موجه النظام"
                    type == "gemini" -> "🧠 طلب استشاري ذكي من جمناي"
                    else -> "📝 تنظيم المكون البنيوي للملف"
                }
                val summ = when {
                    count > 1 -> "تم تنفيذ $count عمليات تعديل ومعالجة على الملف [$displayFile]."
                    type == "builder" -> "تم إنتاج وتجميع ملف الكلمات والشيفرات لـ [$displayFile] بنجاح."
                    type == "smart_capture" -> "التقاط المقطع البرمجي وحفظه في مساحة الحافظة التلقائية."
                    type == "executor" -> "تنفيذ الأمر الموجه بمحرك النظام بنسبة نجاح $successCount/$totalCount."
                    type == "gemini" -> "تم استدعاء جمناي لمعالجة الهيكل البرمجي لـ [$displayFile]."
                    else -> "مزامنة التعديلات على الملف [$displayFile] وحمايته بالكامل."
                }
                val detailsText = if (count > 1) {
                    "تسلسل الخطوات البرمجية:\n" + group.joinToString("\n ➔ ") { "● " + it.message }
                } else {
                    firstLog.details ?: firstLog.message
                }
                Triple(act, summ, detailsText)
            }
            "academic" -> {
                val displayFile = fileName?.substringBefore(".") ?: "المصنف المعرفي"
                val act = when {
                    count > 1 -> "📚 مصادقة بروتوكولات المزامنة المعرفية"
                    type == "builder" -> "🎓 معالجة وتحسين صياغات المحتوى"
                    type == "smart_capture" -> "📥 توثيق المصادر وتوطين البيانات"
                    type == "executor" -> "🔬 استدعاء محفز التجربة والاختبار"
                    type == "gemini" -> "🧠 توليد واستخلاص الأجوبة الاستنباطية"
                    else -> "📝 ترميز مخرجات القالب التعليمي"
                }
                val summ = when {
                    count > 1 -> "تم توثيق سلسلة مزامنة ومطابقة مكونات ($displayFile) وتأمينها تماماً."
                    type == "builder" -> "بناء القوالب الصياغية المعيارية ومعاينة جودة المحتوى لـ ($displayFile)."
                    type == "smart_capture" -> "تم التقاط وحفظ الاقتباس التاريخي بنجاح لحمايته من الفقد."
                    type == "executor" -> "تنفيذ موجه الأوامر للتأكد من الحالة التشغيلية للمصنف الهيكلي."
                    type == "gemini" -> "توظيف التفكير المتسلسل من جمناي لدراسة البنية وحل الهيكل."
                    else -> "تأكيد دورة الإدخال وحفظ النتاجات التعليمية بأمان."
                }
                val detailsText = "بروتوكول تتبع الأحداث برقم معالجة فريد. حالة النجاح: $successCount من $totalCount عمليات مسجلة."
                Triple(act, summ, detailsText)
            }
            "user" -> {
                val displayFile = fileName ?: "الملف"
                val act = when {
                    count > 1 -> "✨ تنظيم وحفظ أعمالك بأمان"
                    type == "builder" -> "👍 تم دمج ومزامنة تعديلاتك!"
                    type == "smart_capture" -> "📋 تم حفظ نص هام في الحافظة"
                    type == "executor" -> "⚡ تشغيل الأدوات والتحسين الذكي"
                    type == "gemini" -> "🔮 رد مساعدك الذكي جمناي"
                    else -> "✅ تم الاحتفاظ بملفاتك آمنة"
                }
                val summ = when {
                    count > 1 -> "قمنا بتنظيم وحماية $count ملفات هامة في مشروعك دون عناء."
                    type == "builder" -> "تم حفظ وبناء التعديلات الجديدة التي قمت بها في [$displayFile] بنجاح."
                    type == "smart_capture" -> "تم التقاط النص المنسوخ وحفظه تلقائياً لكي لا تفقده."
                    type == "executor" -> "قام النظام بتهيئة البيئة المطلوبة وبدء تشغيل أدواتك بسلاسة."
                    type == "gemini" -> "أجاب الذكاء الاصطناعي على استفساراتك الأخيرة وقدم حلاً رائعاً."
                    else -> "وضعنا الملف الجديد [$displayFile] في مكانه المناسب وكل شيء جاهز!."
                }
                val detailsText = "كل شيء يعمل بسلاسة فائقة، تم إجراء $successCount عملية ناجحة تماماً وحفظ تاريخها."
                Triple(act, summ, detailsText)
            }
            else -> { // technical / default
                val act = "⚙️ حدث نظام تقني: " + firstLog.type
                val summ = firstLog.message
                val detailsText = firstLog.details ?: firstLog.message
                Triple(act, summ, detailsText)
            }
        }

        return StoryCard(
            id = group.map { it.id }.joinToString("-"),
            icon = icon,
            title = title,
            summary = summary,
            details = details,
            time = relativeTime,
            relativeTime = relativeTime,
            successCount = successCount,
            totalCount = totalCount,
            rawEvents = group,
            rawLogsCount = count,
            timestamp = latestLog.timestamp,
            filePath = filePath
        )
    }

    private fun getRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "الآن"
            diff < 3600_000 -> "${diff / 60_000} دقيقة"
            diff < 86400_000 -> "${diff / 3600_000} ساعة"
            else -> "${diff / 86400_000} يوم"
        }
    }
}
