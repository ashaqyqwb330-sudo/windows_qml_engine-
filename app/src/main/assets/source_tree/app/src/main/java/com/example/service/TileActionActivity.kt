package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.UniversalActionHandler
import com.example.ui.theme.CardSlateBg
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.SlateBg
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextSilver
import kotlinx.coroutines.launch

class TileActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipboardText = if (clipboard.hasPrimaryClip()) {
            clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } else {
            ""
        }

        if (clipboardText.isBlank()) {
            Toast.makeText(this, "⚠️ الحافظة فارغة! لا يوجد نص لمعالجته.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            TileSelectionDialog(
                clipboardText = clipboardText,
                onDismiss = { finish() }
            )
        }
    }
}

@Composable
fun TileSelectionDialog(
    clipboardText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    val actions = remember {
        listOf(
            ActionOption("auto_detect", "🧠⚡ تحليل تلقائي", "يكتشف نوع النص ويطبق الإجراء الأنسب ذكياً", true),
            ActionOption("smart_capture", "🧠 تحليل ذكي", "يحلل النص ويحفظه في SmartInbox كـ HTML", false),
            ActionOption("execute_commands", "⚙️ تنفيذ الأوامر", "يفحص التوجيهات (@executor) وينفذها", false),
            ActionOption("build_pack", "📦 حزمة بناء", "يغلف النص في توجيهات @builder:file بالحافظة", false),
            ActionOption("quick_capture", "📥 التقاط سريع", "يحفظ النص مباشرة في مجلد المسودات", false),
            ActionOption("convert_beautify", "🎨 تحويل وتجميل", "يحول Markdown إلى HTML منسق وجميل", false)
        )
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
                    .testTag("tile_action_dialog_card"),
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
                        text = "⚡ معالجة الحافظة السريعة",
                        color = MetallicGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "اختر إجراء لتطبيقه على نص الحافظة الحالي:",
                        color = TextSilver,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isProcessing) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MetallicGold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = statusText,
                                color = TextSilver,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        actions.forEach { action ->
                            val isAuto = action.id == "auto_detect"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        isProcessing = true
                                        statusText = "جاري تنفيذ [${action.title}]..."
                                        coroutineScope.launch {
                                            val result = UniversalActionHandler.handleAction(context, action.id, clipboardText)
                                            isProcessing = false
                                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                            onDismiss()
                                        }
                                    }
                                    .testTag("tile_action_${action.id}_button"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isAuto) MetallicGold else CardSlateBg
                                ),
                                border = if (isAuto) null else BorderStroke(1.dp, GlassBorder),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = action.title,
                                            color = if (isAuto) SlateBg else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = action.description,
                                            color = if (isAuto) SlateBg.copy(alpha = 0.8f) else TextGray,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("tile_action_cancel_button"),
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
