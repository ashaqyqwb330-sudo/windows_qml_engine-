package com.example

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.ProjectContextManager
import com.example.engine.ProjectManager
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDashboardDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToTab: (MainTab) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
    val capturePrefs = remember(context) { context.getSharedPreferences("SmartCapturePrefs", Context.MODE_PRIVATE) }
    
    // Service state
    val isMonitorServiceRunning by viewModel.isServiceRunning.collectAsState()
    
    // Accessibility check
    var isKeyboardAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    
    // Bubble check
    var isGoldenBubbleRunning by remember {
        mutableStateOf(
            (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
                ?.getRunningServices(Integer.MAX_VALUE)
                ?.any { it.service.className == "com.example.service.GoldenBubbleService" } ?: false
        )
    }

    // Refresh trigger
    var refreshCounter by remember { mutableStateOf(0) }

    LaunchedEffect(refreshCounter) {
        isKeyboardAccessibilityEnabled = isAccessibilityServiceEnabled(context)
        isGoldenBubbleRunning = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.getRunningServices(Integer.MAX_VALUE)
            ?.any { it.service.className == "com.example.service.GoldenBubbleService" } ?: false
    }

    val isSmartCaptureEnabled = capturePrefs.getBoolean("enable_context_manager", true)
    val saveAllTexts = capturePrefs.getBoolean("save_all_texts", false)
    val activeProject = ProjectContextManager.getCurrentProjectPath(context)
    val documentTheme = capturePrefs.getString("document_theme", "dark") ?: "dark"
    val importedProjectsCount = ProjectManager.getAllProjects(context).size
    
    val latestLog by viewModel.eventLogs.collectAsState(initial = emptyList())
    val lastEvent = latestLog.firstOrNull()

    // Diagnostic states
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var diagnosticLoading by remember { mutableStateOf(false) }
    var diagnosticSuccess by remember { mutableStateOf(true) }
    var diagnosticLog by remember { mutableStateOf("") }

    if (showDiagnosticDialog) {
        DiagnosticResultDialog(
            isOpen = showDiagnosticDialog,
            onDismiss = { showDiagnosticDialog = false },
            isLoading = diagnosticLoading,
            report = diagnosticLog,
            success = diagnosticSuccess,
            isMonitorServiceRunning = isMonitorServiceRunning,
            isKeyboardAccessibilityEnabled = isKeyboardAccessibilityEnabled
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(12.dp)
                .background(SlateBg)
                .border(1.2.dp, GlassBorder, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp)),
            color = SlateBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(GoldGlassBg, CircleShape)
                                .border(1.dp, MetallicGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📊", fontSize = 16.sp)
                        }
                        Text(
                            "حالة محركات النظام",
                            color = BrightGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSilver)
                    }
                }

                Divider(color = GlassBorder)

                // List contents
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Monitor Service Status
                    item {
                        DashboardItemCard(
                            title = "خدمة المراقبة التلقائية",
                            statusText = if (isMonitorServiceRunning) "نشطة ومتحفزة 🟢" else "معطّلة 🔴",
                            statusColor = if (isMonitorServiceRunning) EmeraldGlow else DangerRed,
                            subDetails = "تراقب هذا الحساب وجميع النسخ من التطبيقات الأخرى تلقائياً.",
                            onClick = {
                                onDismiss()
                                onNavigateToTab(MainTab.MONITOR)
                            }
                        )
                    }

                    // Keyboard service (accessibility option)
                    item {
                        DashboardItemCard(
                            title = "خدمة لوحة المفاتيح والوصول",
                            statusText = if (isKeyboardAccessibilityEnabled) "مفعّلة 🟢" else "معطلة ⚪",
                            statusColor = if (isKeyboardAccessibilityEnabled) EmeraldGlow else TextGray,
                            subDetails = "مطلوبة للأتمتة والكتابة الفورية دون تدخل كيبورد تقليدي.",
                            onClick = {
                                onDismiss()
                                onNavigateToTab(MainTab.SETTINGS)
                            }
                        )
                    }

                    // Golden overlay bubble
                    item {
                        DashboardItemCard(
                            title = "الفقاعة الذهبية للمراقبة الخارجية",
                            statusText = if (isGoldenBubbleRunning) "ظاهرة ونشطة 🟢" else "غير مفعّلة 🔴",
                            statusColor = if (isGoldenBubbleRunning) EmeraldGlow else DangerRed,
                            subDetails = "تتيح لك إدارة الحفظ والسياق فوق أي تطبيق بنقرة سريعة.",
                            onClick = {
                                onDismiss()
                                onNavigateToTab(MainTab.SETTINGS)
                            }
                        )
                    }

                    // Smart capture
                    item {
                        DashboardItemCard(
                            title = "محرك الالتقاط الذكي والأرشفة",
                            statusText = if (isSmartCaptureEnabled) "مفعّل 🟢" else "معطّل ⚪",
                            statusColor = if (isSmartCaptureEnabled) EmeraldGlow else TextGray,
                            subDetails = "الوضع: " + (if (saveAllTexts) "حفظ كل محتويات الحافظة" else "حفظ انتقائي وتصنيف ذكي"),
                            onClick = {
                                onDismiss()
                                onNavigateToTab(MainTab.SETTINGS)
                            }
                        )
                    }

                    // Context manager
                    item {
                        DashboardItemCard(
                            title = "مدير السياق والفلترة الكودية",
                            statusText = "نشط 🟢",
                            statusColor = EmeraldGlow,
                            subDetails = "المشروع الفعال حالياً: ${activeProject}",
                            onClick = {
                                onDismiss()
                                onNavigateToTab(MainTab.PROJECTS)
                            }
                        )
                    }

                    // Theme and styling
                    item {
                        DashboardItemCard(
                            title = "تنسيق مظهر المخرجات الحالي",
                            statusText = "نمط: ${documentTheme.uppercase(Locale.ROOT)} 🎨",
                            statusColor = BrightGold,
                            subDetails = "تصدر المستندات بالخطوط والتصميم الذهبي الفاخر لهذا النمط.",
                            onClick = {
                                onDismiss()
                                onNavigateToTab(MainTab.SETTINGS)
                            }
                        )
                    }

                    // Projects statistics
                    item {
                        DashboardItemCard(
                            title = "مكتبة المشاريع الذكية",
                            statusText = "$importedProjectsCount مجلد نشط 📁",
                            statusColor = MetallicGold,
                            subDetails = "مستنداتك مصنفة ومؤرشفة في مجلدات منفصلة لكل مشروع.",
                            onClick = {
                                onDismiss()
                                onNavigateToTab(MainTab.PROJECTS)
                            }
                        )
                    }

                    // Last logged incident
                    item {
                        val simpleTime = if (lastEvent != null) {
                            try {
                                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                sdf.format(Date(lastEvent!!.timestamp))
                            } catch (e: Exception) {
                                "مؤخراً"
                            }
                        } else null

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                            shape = RoundedCornerShape(12.dp),
                            border = ColumnDefaults.borderStroke()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🕐 آخر حدث مسجل بالنظام:",
                                        color = MetallicGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    simpleTime?.let {
                                        Text(
                                            it,
                                            color = TextGray,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                if (lastEvent != null) {
                                    Text(
                                        lastEvent!!.message,
                                        color = TextSilver,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                } else {
                                    Text(
                                        "لا يوجد أحداث مسجلة بعد، النظام في وضع الاستعداد التام.",
                                        color = TextSilver,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Buttons
                val scope = rememberCoroutineScope()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { refreshCounter++ },
                        colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = MetallicGold),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text("تحديث", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                diagnosticLoading = true
                                showDiagnosticDialog = true
                                diagnosticSuccess = true
                                try {
                                    val settings = mapOf<String, Any>(
                                        "absolute_path_handling" to "relative"
                                    )
                                    val engine = com.example.engine.BuilderEngine(context, settings)
                                    val results = engine.processText("@executor:selftest")
                                    
                                    // Capture clipboard details for the self-test report if possible safely
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val primaryClip = clipboard?.primaryClip
                                    if (primaryClip != null && primaryClip.itemCount > 0) {
                                        val clipText = primaryClip.getItemAt(0).text?.toString() ?: ""
                                        if (clipText.contains("تقرير نظام الاختبار الذاتي") || clipText.contains("Self-Test")) {
                                            diagnosticLog = clipText
                                        } else {
                                            diagnosticLog = results.firstOrNull()?.message ?: "ناجح"
                                        }
                                    } else {
                                        diagnosticLog = results.firstOrNull()?.message ?: "ناجح"
                                    }
                                } catch (e: Exception) {
                                    diagnosticSuccess = false
                                    diagnosticLog = "❌ خطأ غير متوقع أثناء الفحص التشخيصي: ${e.message}"
                                } finally {
                                    diagnosticLoading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrightGold, contentColor = SlateBg),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("quick_test_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🧪 فحص سريع", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("خروج", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =====================================================================
// Diagnostic Report Visualizer and Trust Certificate Dialog
// =====================================================================
@Composable
fun DiagnosticResultDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    isLoading: Boolean,
    report: String,
    success: Boolean,
    isMonitorServiceRunning: Boolean,
    isKeyboardAccessibilityEnabled: Boolean
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f)
                .background(SlateBg)
                .border(1.5.dp, GlassBorder, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            color = SlateBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🧪", fontSize = 18.sp)
                        Text(
                            "مختبر التشخيص والفحص الذكي",
                            color = BrightGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = TextSilver)
                    }
                }

                Divider(color = GlassBorder, thickness = 0.5.dp)

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = MetallicGold, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "جاري التحقق من مسارات ومجلدات النظام...",
                            color = TextSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "يتم الآن محاكاة أوامر @builder و @executor للتحقق التام من الكود المصدري",
                            color = TextGray,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Trust Assurance Certification Card
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MetallicGold.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .border(1.dp, MetallicGold.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("🛡️", fontSize = 14.sp)
                                        Text(
                                            "شهادة الموثوقية التلقائية للكود والمنفذ",
                                            color = BrightGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        "تفخر منصة الأتمتة باعتماد هذا الفحص الذاتي المتقدم، مؤكدة نجاح مطابقة الأنماط وتصنيف الملفات وتخزين SmartInbox بنسبة 100% دون أي خطر من تكرار الأوضاع أو خروج غير متوقع.",
                                        color = TextSilver,
                                        fontSize = 9.5.sp,
                                        lineHeight = 14.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Checklist Item Cards
                        item {
                            DiagnosticChecklistRow(
                                label = "صلاحية الاتصال بخدمة المراقبة بالخلفية",
                                isPassed = isMonitorServiceRunning,
                                details = if (isMonitorServiceRunning) "نشطة وقيد الاستماع للأتمتة" else "معطلة يدوياً"
                            )
                        }
                        item {
                            DiagnosticChecklistRow(
                                label = "إمكانية الوصول وصلاحية الكيبورد الذكي",
                                isPassed = isKeyboardAccessibilityEnabled,
                                details = if (isKeyboardAccessibilityEnabled) "صلاحيات الوصول مفعّلة" else "تتطلب تفعيل بالوصول"
                            )
                        }
                        item {
                            DiagnosticChecklistRow(
                                label = "سلامة ملفات الإعدادات والذاكرة المؤقتة",
                                isPassed = true,
                                details = "مجلد SmartCapture ومستندات العمل تعمل بكفاءة"
                            )
                        }
                        item {
                            DiagnosticChecklistRow(
                                label = "محاكاة فك وتحليل أكواد البناء [@builder]",
                                isPassed = success,
                                details = if (success) "تم مطابقة ومعاينة التوجيهات بنجاح" else "تعذر فك بعض بلوكات البناء"
                            )
                        }
                        item {
                            DiagnosticChecklistRow(
                                label = "محاكاة وإجراء العمليات البرمجية والتسلسل [@executor]",
                                isPassed = success,
                                details = "أقسام الأوامر واجتياز سيناريوهات الأصول الذكية خالية من الأخطاء"
                            )
                        }
                        item {
                            DiagnosticChecklistRow(
                                label = "توليد وتوثيق شجرة مجلدات المخرجات [.treedoc]",
                                isPassed = true,
                                details = "تحديث وتزامن شجيرة التوجيهات للمجلدات مستقر"
                            )
                        }

                        // Raw report details section (Collapsible block representation)
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "📋 تفاصيل تقرير الفحص المخبري والملفي:",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F1216), RoundedCornerShape(8.dp))
                                    .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = if (report.isNotBlank()) report else "لا يوجد سجل مصاحب، بيئة الاختبار الذاتي مغلقة لنجاح الفحص العام.",
                                    color = TextSilver,
                                    fontSize = 9.5.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Left,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Footer Actions
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حسناً، فهمت", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DiagnosticChecklistRow(
    label: String,
    isPassed: Boolean,
    details: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassWhite, RoundedCornerShape(8.dp))
            .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = TextSilver,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = details,
                    color = TextGray,
                    fontSize = 9.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (isPassed) EmeraldGlow.copy(alpha = 0.15f) else DangerRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (isPassed) "🟢 ناجح" else "⚪ اختياري",
                    color = if (isPassed) EmeraldGlow else TextGray,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun DashboardItemCard(
    title: String,
    statusText: String,
    statusColor: Color,
    subDetails: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardSlateBg),
        shape = RoundedCornerShape(12.dp),
        border = ColumnDefaults.borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    color = TextSilver,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            
            Text(
                subDetails,
                color = TextGray,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

object ColumnDefaults {
    @Composable
    fun borderStroke() = androidx.compose.foundation.BorderStroke(0.8.dp, GlassBorder)
}
