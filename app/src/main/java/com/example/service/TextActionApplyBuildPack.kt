package com.example.service

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TextActionApplyBuildPack : BaseTextActionActivity() {

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
                
                var buildersCount = 0
                val database = com.example.db.AppDatabase.getDatabase(applicationContext)
                
                for (res in results) {
                    if (res.type == "builder") {
                        buildersCount++
                        val path = res.data?.get("path") ?: "unknown"
                        val size = res.data?.get("size")?.toLongOrNull() ?: 0L
                        val mode = res.data?.get("mode") ?: "w"
                        val fullPath = res.data?.get("full_path") ?: ""
                        database.dao().insertFile(
                            com.example.db.FileEntity(path = path, fullPath = fullPath, size = size, mode = mode)
                        )
                        database.dao().insertLog(
                            com.example.db.LogEntity(
                                type = "builder",
                                message = "تطبيق حزمة البناء: تم إنشاء $path",
                                details = "المسار: $fullPath",
                                source = "buildpack"
                            )
                        )
                    } else {
                        database.dao().insertLog(
                            com.example.db.LogEntity(
                                type = res.type,
                                message = "تطبيق حزمة البناء: إجراء ${res.type}",
                                details = res.message,
                                source = "buildpack"
                            )
                        )
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (buildersCount > 0) {
                        Toast.makeText(applicationContext, "✅ تم تطبيق حزمة البناء: تم إنشاء $buildersCount ملفات.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(applicationContext, "⚠️ لا توجد كتل بناء صالحة لتطبيقها!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "⚠️ فشل تطبيق حزمة البناء: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
