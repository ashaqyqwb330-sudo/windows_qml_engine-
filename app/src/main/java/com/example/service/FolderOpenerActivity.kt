package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.BuildPackExporter
import com.example.engine.ProjectContextManager
import com.example.ui.theme.CardSlateBg
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.SlateBg
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextSilver
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderOpenerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri == null) {
            Toast.makeText(this, "⚠️ لم يتم استلام مسار مجلد صالح!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val folderPath = getPathFromUri(uri)
        if (folderPath == null) {
            Toast.makeText(this, "⚠️ لا يمكن الوصول إلى مسار المجلد هذا!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            Toast.makeText(this, "⚠️ المجلد غير موجود أو غير صالح!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            FolderOpenerDialog(
                folder = folder,
                onDismiss = { finish() }
            )
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        if (uri.scheme == "content") {
            // Attempt resolving custom content or document provider paths
            val pathStr = uri.path ?: return null
            if (pathStr.contains("/document/primary:")) {
                val subPath = pathStr.substringAfter("/document/primary:")
                return "/storage/emulated/0/$subPath"
            }
            if (pathStr.startsWith("/tree/primary:")) {
                val subPath = pathStr.substringAfter("/tree/primary:")
                return "/storage/emulated/0/$subPath"
            }
        }
        // Fallback to absolute string check
        val rawStr = uri.toString()
        if (rawStr.startsWith("/")) {
            return rawStr
        }
        return uri.path
    }
}

@Composable
fun FolderOpenerDialog(
    folder: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    val fileCount = remember(folder) {
        try {
            folder.walkTopDown().filter { it.isFile }.count()
        } catch (e: Exception) {
            0
        }
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = !isProcessing) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clickable(enabled = false) {}
                    .border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(16.dp))
                    .testTag("folder_opener_dialog_card"),
                colors = CardDefaults.cardColors(containerColor = SlateBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📂 معالجة المجلد الذكي",
                        color = MetallicGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المجلد الوارد: ${folder.name}",
                        color = TextSilver,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "يحتوي على $fileCount ملفات",
                        color = TextGray,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isProcessing) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MetallicGold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = statusText,
                                color = TextSilver,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Choice 1: Tree Report
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    isProcessing = true
                                    statusText = "جاري إنشاء التقرير الشجري..."
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val report = generateTreeReport(folder)
                                        withContext(Dispatchers.Main) {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Tree Report", report))
                                            Toast.makeText(context, "📊 تم إنشاء التقرير الشجري للمجلد ونسخه للحافظة!", Toast.LENGTH_LONG).show()
                                            isProcessing = false
                                            onDismiss()
                                        }
                                    }
                                }
                                .testTag("folder_tree_report_button"),
                            colors = CardDefaults.cardColors(containerColor = MetallicGold),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📊 إنشاء تقرير شجري",
                                    color = SlateBg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ينشئ رسماً شجرياً هيكلياً دقيقاً لجميع المجلدات والملفات داخله وينسخه للحافظة.",
                                    color = SlateBg.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Choice 2: Build Pack
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    isProcessing = true
                                    statusText = "جاري تجميع الملفات في حزمة بناء..."
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val pack = BuildPackExporter.wrapDirectory(context, folder)
                                        withContext(Dispatchers.Main) {
                                            if (pack.isNotEmpty()) {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Folder Build Pack", pack))
                                                Toast.makeText(context, "📦 تم تجميع المجلد كحزمة بناء ونسخ كود الحزمة للحافظة!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "⚠️ المجلد فارغ ولا يحتوي على ملفات قابلة للتجميع!", Toast.LENGTH_LONG).show()
                                            }
                                            isProcessing = false
                                            onDismiss()
                                        }
                                    }
                                }
                                .testTag("folder_build_pack_button"),
                            colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📦 حزمة بناء للمجلد",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "يقوم بتعبئة محتويات جميع الملفات النصية والبرمجية داخل هذا المجلد في حزمة بناء موحدة.",
                                    color = TextGray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("folder_opener_cancel_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = TextSilver
                            ),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("إلغاء الأمر", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun generateTreeReport(dir: File): String {
    val sb = java.lang.StringBuilder()
    sb.append("📊 تقرير شجري للمجلد: ${dir.name}\n")
    sb.append("المسار الكامل: ${dir.absolutePath}\n")
    sb.append("════════════════════════════════════\n\n")
    buildTreeString(dir, "", sb)
    return sb.toString()
}

private fun buildTreeString(file: File, prefix: String, sb: java.lang.StringBuilder) {
    val list = file.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
    for ((index, child) in list.withIndex()) {
        val isLast = index == list.size - 1
        val connector = if (isLast) "└── " else "├── "
        sb.append(prefix).append(connector).append(if (child.isDirectory) "📁 " else "📄 ").append(child.name).append("\n")
        if (child.isDirectory) {
            val nextPrefix = prefix + if (isLast) "    " else "│   "
            buildTreeString(child, nextPrefix, sb)
        }
    }
}
