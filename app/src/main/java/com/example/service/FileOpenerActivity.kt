package com.example.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.example.engine.FileUtils
import com.example.engine.ProjectContextManager
import com.example.engine.UniversalActionHandler
import com.example.ui.theme.CardSlateBg
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.SlateBg
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextSilver
import java.io.File
import kotlinx.coroutines.launch

class FileOpenerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri == null) {
            Toast.makeText(this, "⚠️ لم يتم استلام أي ملف صالح!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val fileName = getFileNameFromUri(uri)
        val fileContent = readFileContentFromUri(uri)

        if (fileContent.isBlank()) {
            Toast.makeText(this, "⚠️ محتوى الملف فارغ أو لا يمكن قراءته!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            FileOpenerDialog(
                fileName = fileName,
                fileContent = fileContent,
                onDismiss = { finish() }
            )
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}.txt"
        try {
            if (uri.scheme == "content") {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            name = cursor.getString(nameIndex)
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                name = uri.lastPathSegment ?: name
            }
        } catch (e: Exception) {
            // Fallback
        }
        return name
    }

    private fun readFileContentFromUri(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}

@Composable
fun FileOpenerDialog(
    fileName: String,
    fileContent: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    val filePreview = remember(fileContent) {
        if (fileContent.length > 200) {
            fileContent.take(200) + "\n..."
        } else {
            fileContent
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
                    .testTag("file_opener_dialog_card"),
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
                        text = "📄 التقاط الملف الذكي",
                        color = MetallicGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ملف وارد: $fileName",
                        color = TextSilver,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // File Preview Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(CardSlateBg, RoundedCornerShape(8.dp))
                            .border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = filePreview,
                            color = TextGray,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

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
                        // Choice 1: Smart Capture
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    isProcessing = true
                                    statusText = "جاري تحليل الملف وحفظه ذكياً..."
                                    coroutineScope.launch {
                                        val result = UniversalActionHandler.handleAction(
                                            context,
                                            "smart_capture",
                                            fileContent
                                        )
                                        isProcessing = false
                                        Toast
                                            .makeText(context, result.message, Toast.LENGTH_LONG)
                                            .show()
                                        onDismiss()
                                    }
                                }
                                .testTag("file_smart_capture_button"),
                            colors = CardDefaults.cardColors(containerColor = MetallicGold),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "🧠 التقاط ذكي للملف",
                                    color = SlateBg,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "يقوم بتحليل وتفصيل النص وصيغته، ثم يحفظه في SmartInbox.",
                                    color = SlateBg.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Choice 2: Open/View in editor
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable {
                                    isProcessing = true
                                    statusText = "جاري نسخ الملف للمشروع وعرضه..."
                                    coroutineScope.launch {
                                        try {
                                            // Save in active project folder
                                            val projectDir = ProjectContextManager.getCurrentProjectDir(context)
                                            val targetFile = File(projectDir, fileName)
                                            targetFile.writeText(fileContent, Charsets.UTF_8)
                                            
                                            // Launch open action
                                            FileUtils.openFileSafely(context, targetFile.absolutePath)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "⚠️ خطأ في حفظ وعرض الملف: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                        isProcessing = false
                                        onDismiss()
                                    }
                                }
                                .testTag("file_open_in_editor_button"),
                            colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📄 عرض وحفظ في المشروع",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "يحفظ الملف باسمه الأصلي في مشروعك الحالي ويفتحه فوراً.",
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
                                .testTag("file_opener_cancel_button"),
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
