package com.example.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StyleDetector {

    data class StyleEntry(
        val name: String,
        val selector: String,
        val rules: String,
        val category: String,
        val createdAt: String
    )

    private fun getStyleBankFile(context: Context): File {
        val baseDir = ProjectContextManager.getCurrentProjectDir(context)
        val styleBankDir = File(baseDir, "StyleBank")
        if (!styleBankDir.exists()) {
            styleBankDir.mkdirs()
        }
        return File(styleBankDir, "styles.json")
    }

    fun loadStyles(context: Context): List<StyleEntry> {
        val file = getStyleBankFile(context)
        if (!file.exists()) {
            // Pre-populate with beautiful golden styles!
            val defaultStyles = listOf(
                StyleEntry("الأزرار الذهبية الفاخرة", ".btn-golden", "background: linear-gradient(135deg, #D97706, #FBBF24); color: #111827; border: none; padding: 12px 24px; border-radius: 12px; font-weight: bold; cursor: pointer; box-shadow: 0 4px 15px rgba(217, 119, 6, 0.4);", "أزرار", "2026-06-25 21:00"),
                StyleEntry("البطاقات الزجاجية الداكنة", ".card-glass-dark", "background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(10px); border: 1px solid rgba(217, 119, 6, 0.3); border-radius: 16px; padding: 20px; color: #F3F4F6; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);", "بطاقات", "2026-06-25 21:00"),
                StyleEntry("النصوص المضيئة بذهب برّاق", ".text-gold-glow", "color: #FBBF24; text-shadow: 0 0 10px rgba(251, 191, 36, 0.6); font-weight: 800; letter-spacing: 0.5px;", "نصوص", "2026-06-25 21:00"),
                StyleEntry("شاشات المنصة الذهبية", ".screen-golden-dark", "background-color: #1A1A2E; color: #E5E7EB; font-family: 'Cairo', sans-serif; min-height: 100vh; padding: 24px;", "تخطيطات", "2026-06-25 21:00")
            )
            saveStyles(context, defaultStyles)
            return defaultStyles
        }

        val list = mutableListOf<StyleEntry>()
        try {
            val content = file.readText(Charsets.UTF_8)
            val array = JSONArray(content)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    StyleEntry(
                        obj.getString("name"),
                        obj.getString("selector"),
                        obj.getString("rules"),
                        obj.getString("category"),
                        obj.getString("createdAt")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveStyles(context: Context, styles: List<StyleEntry>) {
        val file = getStyleBankFile(context)
        try {
            val array = JSONArray()
            for (style in styles) {
                val obj = JSONObject().apply {
                    put("name", style.name)
                    put("selector", style.selector)
                    put("rules", style.rules)
                    put("category", style.category)
                    put("createdAt", style.createdAt)
                }
                array.put(obj)
            }
            file.writeText(array.toString(4), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addStyle(context: Context, entry: StyleEntry) {
        val current = loadStyles(context).toMutableList()
        current.removeAll { it.selector.trim().lowercase(Locale.ROOT) == entry.selector.trim().lowercase(Locale.ROOT) }
        current.add(0, entry)
        saveStyles(context, current)
    }

    fun deleteStyle(context: Context, selector: String) {
        val current = loadStyles(context).toMutableList()
        current.removeAll { it.selector.trim().lowercase(Locale.ROOT) == selector.trim().lowercase(Locale.ROOT) }
        saveStyles(context, current)
    }

    fun detectAndSave(context: Context, text: String): Int {
        var count = 0
        try {
            val styleRegex = Regex("<style[^>]*>(.*?)</style>", RegexOption.DOT_MATCHES_ALL)
            val matches = styleRegex.findAll(text)
            
            val sheets = mutableListOf<String>()
            for (match in matches) {
                sheets.add(match.groupValues[1])
            }
            
            if (sheets.isEmpty() && text.contains("{") && text.contains("}") && !text.contains("<html") && !text.contains("<body")) {
                sheets.add(text)
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val nowStr = dateFormat.format(Date())

            for (sheet in sheets) {
                val cssBlockRegex = Regex("([^{]+)\\{([^}]+)\\}", RegexOption.MULTILINE)
                val cssMatches = cssBlockRegex.findAll(sheet)
                
                for (cssMatch in cssMatches) {
                    val selector = cssMatch.groupValues[1].trim()
                    val rules = cssMatch.groupValues[2].trim()
                    
                    if (selector.isNotEmpty() && rules.contains(":") && selector.length < 100) {
                        if (selector.startsWith(".") || selector.startsWith("#") || selector.contains("-btn") || selector.contains("card") || selector.contains("text")) {
                            val name = when {
                                selector.startsWith(".") -> "تنسيق الكلاس ${selector.substring(1)}"
                                selector.startsWith("#") -> "تنسيق المعرّف ${selector.substring(1)}"
                                else -> "تنسيق $selector"
                            }
                            
                            val category = when {
                                selector.contains("btn") || selector.contains("button") -> "أزرار"
                                selector.contains("card") || selector.contains("box") -> "بطاقات"
                                selector.contains("text") || selector.contains("title") || selector.contains("header") -> "نصوص"
                                else -> "عام"
                            }

                            addStyle(
                                context,
                                StyleEntry(
                                    name = name,
                                    selector = selector,
                                    rules = rules,
                                    category = category,
                                    createdAt = nowStr
                                )
                            )
                            count++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return count
    }
}
