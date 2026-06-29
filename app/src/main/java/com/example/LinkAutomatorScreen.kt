package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ChatLinkProcessor
import com.example.engine.ProjectContextManager
import com.example.ui.theme.*
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkAutomatorScreen(
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var manualTextInput by remember { mutableStateOf("") }
    var processingModeTab by remember { mutableStateOf(0) } // 0 = Link, 1 = Manual Text Paste

    // Preferences for Link Automator
    val linkPrefs = remember { context.getSharedPreferences("LinkAutomatorPrefs", Context.MODE_PRIVATE) }
    var extractCode by remember { mutableStateOf(linkPrefs.getBoolean("extract_code", true)) }
    var extractText by remember { mutableStateOf(linkPrefs.getBoolean("extract_text", true)) }
    var applySmartCapture by remember { mutableStateOf(linkPrefs.getBoolean("apply_smart_capture", true)) }

    // Read active project from ProjectContextManager
    val currentProject = remember { ProjectContextManager.getCurrentProjectPath(context) }
    val projectDir = remember { ProjectContextManager.getCurrentProjectDir(context) }

    val status by ChatLinkProcessor.statusFlow.collectAsState()

    fun savePrefs() {
        linkPrefs.edit().apply {
            putBoolean("extract_code", extractCode)
            putBoolean("extract_text", extractText)
            putBoolean("apply_smart_capture", applySmartCapture)
            apply()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Decorative Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                border = BorderStroke(0.8.dp, GlassBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔗 مُؤتمت روابط المحادثات الذكي",
                        color = BrightGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "الصق رابط محادثة عامة (ChatGPT أو DeepSeek أو Claude) لتحميلها فوراً، تحليل محتواها، واستخراج الأكواد البرمجية والمستندات الذكية تلقائياً.",
                        color = TextSilver,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // 2. Active Project Context Info
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GoldGlassBg, RoundedCornerShape(12.dp))
                    .border(BorderStroke(0.6.dp, MetallicGold.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Project Info",
                        tint = BrightGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "المشروع النشط الحالي للحفظ:",
                            color = TextSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentProject,
                            color = BrightGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = "تغيير",
                    color = MetallicGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            Toast.makeText(context, "يمكنك تغيير المشروع النشط من علامة تبويب المشاريع.", Toast.LENGTH_LONG).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Processing Mode Tab Switcher
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F1A), RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (processingModeTab == 0) MetallicGold else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { processingModeTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔗 معالجة الرابط",
                        color = if (processingModeTab == 0) Color.Black else TextSilver,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (processingModeTab == 1) MetallicGold else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { processingModeTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🧠 لصق النص يدوياً (البديل)",
                        color = if (processingModeTab == 1) Color.Black else TextSilver,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Input URL Field or Manual Text Field depending on processingModeTab
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                border = BorderStroke(0.6.dp, GlassBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (processingModeTab == 0) {
                        Text(
                            text = "رابط المشاركة العامة المباشر:",
                            color = BrightGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            placeholder = { Text("https://chat.deepseek.com/share/...", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_url_input"),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrightGold,
                                unfocusedBorderColor = GlassBorder,
                                focusedContainerColor = Color(0xFF0F0F1A),
                                unfocusedContainerColor = Color(0xFF0F0F1A)
                            ),
                            trailingIcon = {
                                if (urlInput.isNotEmpty()) {
                                    IconButton(onClick = { urlInput = "" }) {
                                        Text("✖", color = DangerRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboardText = clipboardManager.getText()?.text
                                    if (!clipboardText.isNullOrBlank()) {
                                        urlInput = clipboardText.trim()
                                        Toast.makeText(context, "تم لصق الرابط من الحافظة!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "الحافظة فارغة!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2F)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📋 لصق الرابط", color = TextSilver, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    urlInput = "https://chat.deepseek.com/share/demo-shared-link-example"
                                    Toast.makeText(context, "تم إدراج رابط تجريبي للتجربة!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2F)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🧪 مثال تجريبي", color = TextSilver, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "انسخ محتوى صفحة المحادثة من المتصفح والصقه بالكامل هنا:",
                            color = BrightGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = manualTextInput,
                            onValueChange = { manualTextInput = it },
                            placeholder = { Text("انسخ المحادثة والصقها كاملة هنا... المحرك سيفرز الأكواد والنصوص ويصنفها تلقائياً.", color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("chat_manual_text_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = BrightGold,
                                unfocusedBorderColor = GlassBorder,
                                focusedContainerColor = Color(0xFF0F0F1A),
                                unfocusedContainerColor = Color(0xFF0F0F1A)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboardText = clipboardManager.getText()?.text
                                    if (!clipboardText.isNullOrBlank()) {
                                        manualTextInput = clipboardText.trim()
                                        Toast.makeText(context, "تم لصق النص من الحافظة!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "الحافظة فارغة!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2F)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📋 لصق المحتوى", color = TextSilver, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    manualTextInput = """
                                        هذا النص من المحادثة ويحتوي على الكود التالي:
                                        ```kotlin
                                        // @builder:src/main/Utils.kt
                                        package com.example
                                        
                                        fun showGlow() {
                                            println("مرحباً بالجميع!")
                                        }
                                        ```
                                        يمكن أيضاً إضافة أي شروحات وسوف يستخرجها المحرك.
                                    """.trimIndent()
                                    Toast.makeText(context, "تم إدراج نص تجريبي للتجربة!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2F)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("🧪 مثال تجريبي", color = TextSilver, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4. Extraction Configs
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                border = BorderStroke(0.6.dp, GlassBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "⚙️ خيارات المعالجة المخصصة:",
                        color = BrightGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Switch 1: Extract Code
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("استخراج الأكواد البرمجية", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("البحث عن وسوم pre/code والتحويل للمحرك الذكي", color = TextSilver, fontSize = 10.sp)
                        }
                        Switch(
                            checked = extractCode,
                            onCheckedChange = {
                                extractCode = it
                                savePrefs()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BrightGold,
                                checkedTrackColor = MetallicGold.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Divider(color = GlassBorder, thickness = 0.6.dp)

                    // Switch 2: Extract Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("استخراج الشروحات والمستندات", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("توليد ملفات نصوص مرتبة للمناقشات والفقرات", color = TextSilver, fontSize = 10.sp)
                        }
                        Switch(
                            checked = extractText,
                            onCheckedChange = {
                                extractText = it
                                savePrefs()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BrightGold,
                                checkedTrackColor = MetallicGold.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Divider(color = GlassBorder, thickness = 0.6.dp)

                    // Switch 3: Apply Smart Capture
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تطبيق الالتقاط الذكي الفاخر (Smart Capture)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("تجميل وتنسيق النصوص كملفات HTML تفاعلية بلمسة ذهبية", color = TextSilver, fontSize = 10.sp)
                        }
                        Switch(
                            checked = applySmartCapture,
                            onCheckedChange = {
                                applySmartCapture = it
                                savePrefs()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BrightGold,
                                checkedTrackColor = MetallicGold.copy(alpha = 0.5f)
                            ),
                            enabled = extractText
                        )
                    }
                }
            }
        }

        // 5. Processing Status & Action Button
        item {
            val isProcessing = status is ChatLinkProcessor.Status.Downloading || status is ChatLinkProcessor.Status.Processing

            Button(
                onClick = {
                    if (processingModeTab == 0) {
                        if (urlInput.isBlank()) {
                            Toast.makeText(context, "الرجاء إدخال رابط المحادثة أولاً!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            ChatLinkProcessor.processChatLink(context, urlInput, projectDir.absolutePath)
                        }
                    } else {
                        if (manualTextInput.isBlank()) {
                            Toast.makeText(context, "الرجاء لصق محتوى المحادثة أولاً!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            ChatLinkProcessor.processRawContent(context, manualTextInput, projectDir.absolutePath)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("run_link_automator"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(12.dp),
                enabled = !isProcessing,
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = if (isProcessing) {
                                    listOf(Color.Gray, Color.DarkGray)
                                } else {
                                    listOf(DarkGold, MetallicGold, BrightGold)
                                }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run",
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProcessing) "جاري تشغيل المعالجة والأتمتة..." else "⚡ تشغيل الأتمتة الفاخرة",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // 6. Detailed Progress Indicator / Status Updates
        item {
            when (val currentStatus = status) {
                is ChatLinkProcessor.Status.Downloading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                        border = BorderStroke(0.6.dp, MetallicGold.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = BrightGold)
                            Text(
                                text = "🔄 جاري الاتصال وتحميل رابط المحادثة العام...",
                                color = TextSilver,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                is ChatLinkProcessor.Status.Processing -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                        border = BorderStroke(0.6.dp, MetallicGold.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "🧠 جاري التحليل والتقسيم والتوجيه التلقائي...",
                                color = BrightGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            LinearProgressIndicator(
                                progress = currentStatus.current.toFloat() / currentStatus.total.toFloat(),
                                color = BrightGold,
                                trackColor = GlassBorder,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "الكتلة: ${currentStatus.current} / ${currentStatus.total}",
                                    color = TextSilver,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "حالة: نشط ⚡",
                                    color = EmeraldGlow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = currentStatus.lastSaved,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                is ChatLinkProcessor.Status.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                        border = BorderStroke(0.8.dp, EmeraldGlow.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = EmeraldGlow,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✅ تمت الأتمتة بنجاح باهر!",
                                    color = EmeraldGlow,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("أكواد مستخرجة", color = TextSilver, fontSize = 11.sp)
                                    Text("${currentStatus.codeBlocksCount}", color = BrightGold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("نصوص ومستندات", color = TextSilver, fontSize = 11.sp)
                                    Text("${currentStatus.textBlocksCount}", color = BrightGold, fontSize = 20.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            Divider(color = GlassBorder, thickness = 0.6.dp)

                            Text(
                                text = "مستندات وتعديلات النظام:",
                                color = TextSilver,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentStatus.details,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                is ChatLinkProcessor.Status.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                        border = BorderStroke(0.8.dp, DangerRed.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚠️ فشل إكمال معالجة الرابط",
                                color = DangerRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = currentStatus.message,
                                color = TextSilver,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                else -> {
                    // Idle state, show placeholder instruction
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(0.6.dp, GlassBorder), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Ready",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "جاهز وبانتظار لصق رابط أو نص المحادثة للبدء...",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
