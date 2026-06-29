package com.example.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileUtils {
    fun openFile(context: Context, filePath: String) {
        openFileSafely(context, filePath)
    }

    fun openFileSafely(context: Context, path: String) {
        try {
            var file = File(path)
            if (!file.exists() || !file.isAbsolute) {
                val resolvedInProj = File(ProjectContextManager.getCurrentProjectDir(context), path)
                if (resolvedInProj.exists()) {
                    file = resolvedInProj
                } else {
                    val resolvedInBase = File(ProjectContextManager.getBaseDir(context), path)
                    if (resolvedInBase.exists()) {
                        file = resolvedInBase
                    } else {
                        val fileNameOnly = File(path).name
                        val resolvedInProjName = File(ProjectContextManager.getCurrentProjectDir(context), fileNameOnly)
                        if (resolvedInProjName.exists()) {
                            file = resolvedInProjName
                        } else {
                            val resolvedInBaseName = File(ProjectContextManager.getBaseDir(context), fileNameOnly)
                            if (resolvedInBaseName.exists()) {
                                file = resolvedInBaseName
                            }
                        }
                    }
                }
            }
            
            if (!file.exists()) {
                Toast.makeText(context, "تعذر العثور على الملف: $path", Toast.LENGTH_SHORT).show()
                return
            }
            
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val mimeType = when {
                file.name.endsWith(".html") -> "text/html"
                file.name.endsWith(".py") || file.name.endsWith(".java") || file.name.endsWith(".kt") || file.name.endsWith(".txt") -> "text/plain"
                file.name.endsWith(".json") -> "application/json"
                file.name.endsWith(".xml") -> "text/xml"
                else -> "*/*"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("FileUtils", "Error opening file safely: ${e.message}", e)
            Toast.makeText(context, "فشل فتح الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
