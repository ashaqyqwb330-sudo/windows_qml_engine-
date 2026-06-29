package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import java.io.File

class TransparentActivity : Activity() {
    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1001
        const val ACTION_PICK_FOLDER = "com.example.service.ACTION_PICK_FOLDER"
        const val ACTION_SHOW_IME_PICKER = "com.example.service.ACTION_SHOW_IME_PICKER"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        if (action == ACTION_PICK_FOLDER) {
            try {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)
            } catch (e: Exception) {
                Toast.makeText(this, "فشل فتح مستكشف المجلدات: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            // Default behaviour: show IME picker
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FOLDER) {
            if (resultCode == RESULT_OK) {
                val uri = data?.data
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        val path = getPathFromTreeUri(this, uri)
                        if (path != null) {
                            com.example.engine.UnifiedPathManager.setActivePath(this, path)
                            // Send broadcast to update the tree immediately
                            sendBroadcast(Intent("ACTION_REFRESH_IME_TREE"))
                            Toast.makeText(this, "تم تغيير المجلد النشط إلى: $path", Toast.LENGTH_LONG).show()
                        } else {
                            // Fallback to absolute URI representation if we can't map it directly
                            val rawUriStr = uri.toString()
                            com.example.engine.UnifiedPathManager.setActivePath(this, rawUriStr)
                            sendBroadcast(Intent("ACTION_REFRESH_IME_TREE"))
                            Toast.makeText(this, "تم تغيير المجلد كـ URI: $rawUriStr", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "خطأ بالوصول للمجلد: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            finish()
        }
    }

    private fun getPathFromTreeUri(context: Context, uri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                val path = Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
                return path
            } else {
                // Secondary storage / SD card support
                val extDirs = context.getExternalFilesDirs(null)
                for (dir in extDirs) {
                    if (dir != null) {
                        val path = dir.absolutePath
                        val index = path.indexOf("/Android/data")
                        if (index != -1) {
                            val base = path.substring(0, index)
                            val folder = File(base, split[1])
                            if (folder.exists()) {
                                return folder.absolutePath
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // silent catch
        }
        return null
    }
}
