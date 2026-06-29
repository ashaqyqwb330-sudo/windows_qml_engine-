package com.example.service

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextActionExecuteCommands : BaseTextActionActivity() {

    override fun handleText(text: String) {
        val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val pBuilder = sharedPrefs.getString("prefix_builder", "@builder") ?: "@builder"
        val pExecutor = sharedPrefs.getString("prefix_executor", "@executor") ?: "@executor"
        val pTreedoc = sharedPrefs.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"

        val settings = mapOf(
            "absolute_path_handling" to "relative",
            "base_dir" to com.example.engine.ProjectContextManager.getCurrentProjectDir(this).absolutePath,
            "directive_prefixes" to listOf(pBuilder),
            "executor_prefixes" to listOf(pExecutor),
            "treedoc_prefixes" to listOf(pTreedoc)
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val engine = com.example.engine.BuilderEngine(applicationContext, settings)
                val results = engine.processText(text)
                withContext(Dispatchers.Main) {
                    if (results.isNotEmpty()) {
                        // Insert standard metadata entities like builder logs to AppDatabase
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = com.example.db.AppDatabase.getDatabase(applicationContext)
                            for (res in results) {
                                if (res.type == "builder") {
                                    val path = res.data?.get("path") ?: "unknown"
                                    val size = res.data?.get("size")?.toLongOrNull() ?: 0L
                                    val mode = res.data?.get("mode") ?: "w"
                                    val fullPath = res.data?.get("full_path") ?: ""
                                    database.dao().insertFile(
                                        com.example.db.FileEntity(path = path, fullPath = fullPath, size = size, mode = mode)
                                    )
                                }
                            }
                        }
                        Toast.makeText(applicationContext, "⚙️ تم تنفيذ الأوامر بنجاح", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, "⚠️ لا توجد أوامر صالحة", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "⚠️ خطأ أثناء التنفيذ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
