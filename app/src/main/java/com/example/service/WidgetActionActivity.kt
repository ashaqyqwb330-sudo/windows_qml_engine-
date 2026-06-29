package com.example.service

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import com.example.engine.LargeTextProcessor
import com.example.engine.ProjectContextManager
import java.io.File

class WidgetActionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val actionType = intent?.getStringExtra("action_type") ?: "smart_capture"

        if (actionType == "tree_report") {
            try {
                val activeDir = ProjectContextManager.getCurrentProjectDir(this)
                val report = generateTreeReport(activeDir)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Project Tree Report", report))
                Toast.makeText(this, "📊 تم إنشاء تقرير شجري لمشروعك النشط (${activeDir.name}) ونسخه للحافظة!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "⚠️ فشل إنشاء التقرير الشجري: ${e.message}", Toast.LENGTH_LONG).show()
            }
            finish()
            return
        }

        // Standard clipboard-based actions (smart_capture, execute_commands)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = if (clipboard.hasPrimaryClip()) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } else {
            ""
        }

        if (text.isBlank()) {
            Toast.makeText(this, "⚠️ الحافظة فارغة! لا يوجد نص لمعالجته.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Delegate execution to LargeTextProcessor (which delegates to UniversalActionHandler or handles chunks dynamically)
        LargeTextProcessor.processLargeText(this, text, actionType)

        finish()
    }

    private fun generateTreeReport(dir: File): String {
        val sb = java.lang.StringBuilder()
        sb.append("📊 تقرير شجري للمشروع النشط: ${dir.name}\n")
        sb.append("المسار الكامل: ${dir.absolutePath}\n")
        sb.append("════════════════════════════════════\n\n")
        buildTreeString(dir, "", sb)
        return sb.toString()
    }

    private fun buildTreeString(file: File, prefix: String, sb: java.lang.StringBuilder) {
        val list = file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
        for ((index, child) in list.withIndex()) {
            // Skip large build directories or cache files to keep the tree beautiful
            if (child.name == "node_modules" || child.name == ".git" || child.name == ".gradle" || child.name == "build") {
                continue
            }
            val isLast = index == list.size - 1
            val connector = if (isLast) "└── " else "├── "
            sb.append(prefix).append(connector).append(if (child.isDirectory) "📁 " else "📄 ").append(child.name).append("\n")
            if (child.isDirectory) {
                val nextPrefix = prefix + if (isLast) "    " else "│   "
                buildTreeString(child, nextPrefix, sb)
            }
        }
    }
}
