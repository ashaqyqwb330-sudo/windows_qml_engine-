package com.example

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceExportScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<Triple<Int, String, Boolean>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "📤 التصدير الذاتي للمصدر",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "للخلف",
                            tint = MetallicGold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateBg)
            )
        },
        containerColor = SlateBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Intro Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✨", fontSize = 24.sp)
                        Text(
                            text = "ميزة التصدير الذاتي البرمجي (Self-Export)",
                            color = BrightGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "هذه ميزة ثورية وخارقة للعادة! تتيح لك تفريغ وتصدير الكود المصدري الكامل لهذا التطبيق (بما في ذلك ملفات Gradle ومصادر لغة Kotlin والموارد الرسومية والواجهات بالكامل) كحزمة بناء مجمعة ومغلفة بتوجيهات @builder.",
                        color = TextSilver,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يمكنك نسخ الحزمة البرمجية بالكامل ولصقها أمام أي مساعد ذكي خارجي (مثل ChatGPT أو DeepSeek أو Gemini) لإعادة بناء التطبيق أو تعديله أو إضافة ميزات جديدة إليه بالكامل بمجرد تكرار الأوامر!",
                        color = TextMuted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Export CTA Button
            Button(
                onClick = {
                    scope.launch {
                        isExporting = true
                        exportResult = null
                        // Give a professional scanning feel
                        delay(1200)
                        val res = SourceExporter.exportSourceToClipboard(context)
                        exportResult = res
                        isExporting = false
                        if (res.third) {
                            Toast.makeText(context, "✅ تم تصدير ${res.first} ملفاً للحافظة وحفظها بالمشروع!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "⚠️ الحزمة كبيرة جداً. تم حفظ الكود كملف فقط في مجلد المشروع!", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_export_source"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MetallicGold,
                    contentColor = SlateBg
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = !isExporting
            ) {
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = SlateBg,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("جاري قراءة وتجميع الملفات...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📤 تصدير الكود المصدري كحزمة بناء", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Export Result Section
            AnimatedVisibility(
                visible = exportResult != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                exportResult?.let { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0x1510B981)),
                            border = BorderStroke(1.dp, Color(0x3010B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldGlow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "تم التصدير بنجاح تام!",
                                        color = EmeraldGlow,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (result.third) {
                                        "• إجمالي الملفات المصدرة والمفلترة: ${result.first} ملفاً برمجياً وتكوينياً حقيقياً.\n" +
                                        "• حالة الحافظة: تم نسخ حزمة البناء بالكامل وتجهيزها للصق فوراً.\n" +
                                        "• نسخة احتياطية: تم الحفظ كملف نصي منظم في مجلد المشروع:\n" +
                                        "  `SmartInbox/Source_Export.txt`"
                                    } else {
                                        "• إجمالي الملفات المصدرة والمفلترة: ${result.first} ملفاً برمجياً وتكوينياً حقيقياً.\n" +
                                        "• حالة الحافظة: ⚠️ الحزمة كبيرة جداً (> 10MB)، تفادياً لتجميد الذاكرة لم يتم نسخها تلقائياً للحافظة.\n" +
                                        "• نسخة احتياطية: تم الحفظ كملف نصي منظم بنجاح في مجلد المشروع:\n" +
                                        "  `SmartInbox/Source_Export.txt`"
                                    },
                                    color = TextSilver,
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        // Re-copy Button
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Golden Source Code Dump", result.second)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "📋 تم إعادة نسخ الكود المصدري للحافظة!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = BrightGold
                            ),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📋 نسخ كود الحزمة البرمجية مجدداً", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Code Preview Block
                        Text(
                            text = "👀 معاينة الهيكل الخارجي لحزمة البناء التصديرية:",
                            color = BrightGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        val previewSnippet = """
                            // =========================================================
                            // 📥 حزمة التصدير الذاتي للمنصة الذهبية (Self-Exporting Source Code)
                            // =========================================================
                            
                            // @builder:file build.gradle.kts
                            plugins {
                              alias(libs.plugins.android.application)
                              ...
                            }
                            // @builder:end
                            
                            // @builder:file app/src/main/AndroidManifest.xml
                            <manifest ...>
                            ...
                            </manifest>
                            // @builder:end
                            
                            // @builder:file app/src/main/java/com/example/MainActivity.kt
                            package com.example
                            ...
                            // @builder:end
                        """.trimIndent()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111122)),
                            border = BorderStroke(1.dp, GlassBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = previewSnippet,
                                    color = TextSilver,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Warning / Note Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                border = BorderStroke(1.dp, GlassBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BrightGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "ملاحظة: حزمة البناء المجمعة يتم تنسيقها تلقائياً لتوافق نظام @builder بالملي، مما يجعل من الممكن تطبيقها على الفور على أي مشروع فارغ باستخدام أداة الأتمتة.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}
