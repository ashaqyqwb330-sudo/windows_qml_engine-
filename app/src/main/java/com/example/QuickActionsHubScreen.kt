package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.example.ui.theme.*

@Composable
fun QuickActionsHubScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("SmartCapturePrefs", Context.MODE_PRIVATE) }

    // States for custom actions activation switches
    var actionSmartCaptureEnabled by remember {
        mutableStateOf(prefs.getBoolean("action_smart_capture_enabled", true))
    }
    var actionCommandExecutorEnabled by remember {
        mutableStateOf(prefs.getBoolean("action_command_executor_enabled", true))
    }
    var actionBuildPackEnabled by remember {
        mutableStateOf(prefs.getBoolean("action_build_pack_enabled", true))
    }
    var actionQuickCaptureEnabled by remember {
        mutableStateOf(prefs.getBoolean("action_quick_capture_enabled", false))
    }
    var actionBeautifyEnabled by remember {
        mutableStateOf(prefs.getBoolean("action_beautify_enabled", false))
    }
    var actionApplyBuildPackEnabled by remember {
        mutableStateOf(prefs.getBoolean("action_apply_build_pack_enabled", true))
    }

    // States for specific actions sub-settings
    var smartSaveDestination by remember {
        mutableStateOf(prefs.getString("action_smart_save_destination", "SMART_INBOX") ?: "SMART_INBOX")
    }
    var executorStrictSecurity by remember {
        mutableStateOf(prefs.getBoolean("action_executor_strict_security", false))
    }
    var buildPackTimestamp by remember {
        mutableStateOf(prefs.getBoolean("action_build_pack_timestamp", true))
    }
    var quickCaptureDraftPath by remember {
        mutableStateOf(prefs.getString("action_quick_capture_draft_path", "/Drafts") ?: "/Drafts")
    }
    var beautifyInlineCss by remember {
        mutableStateOf(prefs.getBoolean("action_beautify_inline_css", false))
    }
    var applyBuildPackOverwrite by remember {
        mutableStateOf(prefs.getBoolean("action_apply_build_pack_overwrite", true))
    }
    var accessShareSheetEnabled by remember {
        mutableStateOf(prefs.getBoolean("access_share_sheet_enabled", true))
    }
    var accessOpenWithEnabled by remember {
        mutableStateOf(prefs.getBoolean("access_open_with_enabled", true))
    }
    var accessQuickTileEnabled by remember {
        mutableStateOf(prefs.getBoolean("access_quick_tile_enabled", true))
    }

    // Expanded card tracker
    var expandedCard by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SlateBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .background(GlassWhite, RoundedCornerShape(10.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .testTag("back_to_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "الرجوع للإعدادات",
                        tint = MetallicGold
                    )
                }

                Text(
                    text = "⚡ مركز الإجراءات السريعة",
                    color = BrightGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    text = "تخصيص السلوك العام للتطبيق عند نسخ البيانات وتجنب أي تصادمات تشغيلية بين الأدوات الذكية.",
                    color = TextGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            // 1. Smart Capture Card
            item {
                ActionCard(
                    title = "🧠 تحليل ذكي",
                    description = "يحلل النص المنسوخ ويحفظه في SmartInbox وفقاً للتصنيف المناسب تلقائياً.",
                    isEnabled = actionSmartCaptureEnabled,
                    onEnabledChange = { isChecked ->
                        actionSmartCaptureEnabled = isChecked
                        prefs.edit().putBoolean("action_smart_capture_enabled", isChecked).apply()
                        if (isChecked) expandedCard = "smart" else if (expandedCard == "smart") expandedCard = null
                    },
                    isExpanded = expandedCard == "smart",
                    onToggleExpand = {
                        expandedCard = if (expandedCard == "smart") null else "smart"
                    },
                    testTagPrefix = "smart_capture",
                    conflictWarning = if (actionBeautifyEnabled) {
                        "⚠️ هذا الإجراء لا يمكن تفعيله مع 'تحويل وتجميل'."
                    } else null
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "📁 مجلد الحفظ الافتراضي للمخرجات:",
                            color = TextSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val options = listOf(
                                "SMART_INBOX" to "SmartInbox",
                                "PROJECT_DIR" to "مجلد المشروع"
                            )
                            options.forEach { (valKey, valLabel) ->
                                val isSelected = smartSaveDestination == valKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .background(
                                            if (isSelected) MetallicGold.copy(alpha = 0.2f) else GlassWhite,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) MetallicGold else GlassBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            smartSaveDestination = valKey
                                            prefs.edit().putString("action_smart_save_destination", valKey).apply()
                                        }
                                        .testTag("smart_dest_$valKey"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = valLabel,
                                        color = if (isSelected) BrightGold else TextSilver,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Command Executor Card
            item {
                ActionCard(
                    title = "⚙️ تنفيذ الأوامر",
                    description = "يبحث عن توجيهات الأوامر المنصوص عليها وينفذها برمجياً لتهيئة الملفات تلقائياً.",
                    isEnabled = actionCommandExecutorEnabled,
                    onEnabledChange = { isChecked ->
                        actionCommandExecutorEnabled = isChecked
                        prefs.edit().putBoolean("action_command_executor_enabled", isChecked).apply()
                        if (isChecked) expandedCard = "executor" else if (expandedCard == "executor") expandedCard = null
                    },
                    isExpanded = expandedCard == "executor",
                    onToggleExpand = {
                        expandedCard = if (expandedCard == "executor") null else "executor"
                    },
                    testTagPrefix = "command_executor",
                    conflictWarning = if (actionQuickCaptureEnabled) {
                        "⚠️ هذا الإجراء لا يمكن تفعيله مع 'التقاط سريع'."
                    } else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("التحقق الأمني المشدد", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("طلب تأكيد أمني إضافي قبل تشغيل الأوامر المعقدة", color = TextGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = executorStrictSecurity,
                            onCheckedChange = { isChecked ->
                                executorStrictSecurity = isChecked
                                prefs.edit().putBoolean("action_executor_strict_security", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            ),
                            modifier = Modifier.testTag("executor_strict_security_switch")
                        )
                    }
                }
            }

            // 3. Build Pack Card
            item {
                ActionCard(
                    title = "📦 حزمة بناء",
                    description = "يقوم بتجهيز ونمذجة الكتل المحددة كحزم توجيهية جاهزة لتصديرها فوراً للمطور.",
                    isEnabled = actionBuildPackEnabled,
                    onEnabledChange = { isChecked ->
                        actionBuildPackEnabled = isChecked
                        prefs.edit().putBoolean("action_build_pack_enabled", isChecked).apply()
                        if (isChecked) expandedCard = "buildpack" else if (expandedCard == "buildpack") expandedCard = null
                    },
                    isExpanded = expandedCard == "buildpack",
                    onToggleExpand = {
                        expandedCard = if (expandedCard == "buildpack") null else "buildpack"
                    },
                    testTagPrefix = "build_pack",
                    conflictWarning = null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تضمين تفاصيل الوقت والتاريخ", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("إضافة تعليق علوي يحتوي على توقيت إعداد الحزمة الحالي", color = TextGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = buildPackTimestamp,
                            onCheckedChange = { isChecked ->
                                buildPackTimestamp = isChecked
                                prefs.edit().putBoolean("action_build_pack_timestamp", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            ),
                            modifier = Modifier.testTag("build_pack_timestamp_switch")
                        )
                    }
                }
            }

            // 4. Quick Capture Card
            item {
                ActionCard(
                    title = "📥 التقاط سريع",
                    description = "يلتقط أي محتوى منسوخ لبروتوكول الحافظة ويحفظه كملف مسودة فوري.",
                    isEnabled = actionQuickCaptureEnabled,
                    onEnabledChange = { isChecked ->
                        actionQuickCaptureEnabled = isChecked
                        prefs.edit().putBoolean("action_quick_capture_enabled", isChecked).apply()
                        if (isChecked) expandedCard = "quick" else if (expandedCard == "quick") expandedCard = null
                    },
                    isExpanded = expandedCard == "quick",
                    onToggleExpand = {
                        expandedCard = if (expandedCard == "quick") null else "quick"
                    },
                    testTagPrefix = "quick_capture",
                    conflictWarning = if (actionCommandExecutorEnabled) {
                        "⚠️ هذا الإجراء لا يمكن تفعيله مع 'تنفيذ الأوامر'."
                    } else null
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "✍️ مسار مسودة التقاط الحصاد الفوري:",
                            color = TextSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = quickCaptureDraftPath,
                            onValueChange = { newVal ->
                                quickCaptureDraftPath = newVal
                                prefs.edit().putString("action_quick_capture_draft_path", newVal).apply()
                            },
                            textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextSilver,
                                unfocusedTextColor = TextSilver,
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = GlassBorder,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quick_capture_draft_path_input")
                        )
                    }
                }
            }

            // 5. Beautify & Convert Card
            item {
                ActionCard(
                    title = "🎨 تحويل وتجميل",
                    description = "يعيد صياغة النصوص والـ HTML لتحسين أبعاد المظهر ودعم الجمالية المباشرة.",
                    isEnabled = actionBeautifyEnabled,
                    onEnabledChange = { isChecked ->
                        actionBeautifyEnabled = isChecked
                        prefs.edit().putBoolean("action_beautify_enabled", isChecked).apply()
                        if (isChecked) expandedCard = "beautify" else if (expandedCard == "beautify") expandedCard = null
                    },
                    isExpanded = expandedCard == "beautify",
                    onToggleExpand = {
                        expandedCard = if (expandedCard == "beautify") null else "beautify"
                    },
                    testTagPrefix = "beautify",
                    conflictWarning = if (actionSmartCaptureEnabled) {
                        "⚠️ هذا الإجراء لا يمكن تفعيله مع 'تحليل ذكي'."
                    } else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("السماح بالأنماط المضمنة (Inline CSS)", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("حقن أكواد الأنماط والـ CSS داخل سطور الـ HTML مباشرة", color = TextGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = beautifyInlineCss,
                            onCheckedChange = { isChecked ->
                                beautifyInlineCss = isChecked
                                prefs.edit().putBoolean("action_beautify_inline_css", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            ),
                            modifier = Modifier.testTag("beautify_inline_css_switch")
                        )
                    }
                }
            }

            // 6. Apply Build Pack Card
            item {
                ActionCard(
                    title = "📂 تطبيق حزمة بناء",
                    description = "يستقبل توجيهات بناء وينشئ الملفات في مشروعك.",
                    isEnabled = actionApplyBuildPackEnabled,
                    onEnabledChange = { isChecked ->
                        actionApplyBuildPackEnabled = isChecked
                        prefs.edit().putBoolean("action_apply_build_pack_enabled", isChecked).apply()
                        if (isChecked) expandedCard = "apply_buildpack" else if (expandedCard == "apply_buildpack") expandedCard = null
                    },
                    isExpanded = expandedCard == "apply_buildpack",
                    onToggleExpand = {
                        expandedCard = if (expandedCard == "apply_buildpack") null else "apply_buildpack"
                    },
                    testTagPrefix = "apply_buildpack"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الكتابة فوق الملفات الموجودة", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("السماح للمحرك بالكتابة فوق الملفات دون تحذير مسبق", color = TextGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = applyBuildPackOverwrite,
                            onCheckedChange = { isChecked ->
                                applyBuildPackOverwrite = isChecked
                                prefs.edit().putBoolean("action_apply_build_pack_overwrite", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            ),
                            modifier = Modifier.testTag("apply_build_pack_overwrite_switch")
                        )
                    }
                }
            }

            // 7. Self-Export Source Code Card
            item {
                var isExportingLocal by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                    border = BorderStroke(1.dp, GlassBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📤 التصدير الذاتي البرمجي",
                                    color = BrightGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "قم بتصدير وتجميع كامل الكود المصدري للتطبيق كحزمة بناء جاهزة في الحافظة.",
                                    color = TextGray,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    isExportingLocal = true
                                    delay(1000)
                                    val res = SourceExporter.exportSourceToClipboard(context)
                                    isExportingLocal = false
                                    if (res.third) {
                                        Toast.makeText(context, "✅ تم تجميع وتصدير ${res.first} ملفاً برمجياً للحافظة وحفظها بالمشروع!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "⚠️ الحزمة كبيرة جداً. تم حفظ الكود كملف فقط في مجلد المشروع!", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("quick_action_export_source_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetallicGold,
                                contentColor = SlateBg
                            ),
                            shape = RoundedCornerShape(6.dp),
                            enabled = !isExportingLocal
                        ) {
                            if (isExportingLocal) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = SlateBg,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري التجميع...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("📤 تصدير كامل المصدر الآن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 8. Universal Access System Settings Card
            item {
                var isExpandedAccess by remember { mutableStateOf(false) }
                ActionCard(
                    title = "🌐 نظام الوصول الشامل (Universal Access)",
                    description = "إدارة ظهور وتكامل التطبيق مع نظام أندرويد (قائمة المشاركة، الفتح باستخدام، والتبويبة السريعة).",
                    isEnabled = true,
                    onEnabledChange = { },
                    isExpanded = isExpandedAccess,
                    onToggleExpand = { isExpandedAccess = !isExpandedAccess },
                    testTagPrefix = "universal_access"
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Share Sheet Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("قائمة المشاركة (Share Sheet)", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("تفعيل ظهور التطبيق كخيار لمشاركة النصوص من التطبيقات الأخرى.", color = TextGray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = accessShareSheetEnabled,
                                onCheckedChange = { isChecked ->
                                    accessShareSheetEnabled = isChecked
                                    prefs.edit().putBoolean("access_share_sheet_enabled", isChecked).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SlateBg,
                                    checkedTrackColor = MetallicGold,
                                    uncheckedThumbColor = TextGray,
                                    uncheckedTrackColor = GlassWhite
                                ),
                                modifier = Modifier.testTag("access_share_sheet_switch")
                            )
                        }

                        Divider(color = GlassBorder.copy(alpha = 0.1f), thickness = 0.5.dp)

                        // 2. Open With Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الفتح باستخدام (Open With)", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("تفعيل فتح وقراءة ملفات الكود والبرمجة عبر محرر ومحرك التطبيق.", color = TextGray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = accessOpenWithEnabled,
                                onCheckedChange = { isChecked ->
                                    accessOpenWithEnabled = isChecked
                                    prefs.edit().putBoolean("access_open_with_enabled", isChecked).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SlateBg,
                                    checkedTrackColor = MetallicGold,
                                    uncheckedThumbColor = TextGray,
                                    uncheckedTrackColor = GlassWhite
                                ),
                                modifier = Modifier.testTag("access_open_with_switch")
                            )
                        }

                        Divider(color = GlassBorder.copy(alpha = 0.1f), thickness = 0.5.dp)

                        // 3. Quick Tile Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("أداة الضبط السريع (Quick Tile)", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("تفعيل أيقونة لوحة الإشعارات لمعالجة محتوى الحافظة بنقرة واحدة.", color = TextGray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = accessQuickTileEnabled,
                                onCheckedChange = { isChecked ->
                                    accessQuickTileEnabled = isChecked
                                    prefs.edit().putBoolean("access_quick_tile_enabled", isChecked).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SlateBg,
                                    checkedTrackColor = MetallicGold,
                                    uncheckedThumbColor = TextGray,
                                    uncheckedTrackColor = GlassWhite
                                ),
                                modifier = Modifier.testTag("access_quick_tile_switch")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    description: String,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    testTagPrefix: String,
    conflictWarning: String? = null,
    expandedContent: @Composable () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_card")
            .alpha(if (isEnabled) 1f else 0.55f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title, 
                        color = MetallicGold, 
                        fontSize = 13.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("${testTagPrefix}_title")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description, 
                        color = TextGray, 
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SlateBg,
                            checkedTrackColor = MetallicGold,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = GlassWhite
                        ),
                        modifier = Modifier.testTag("${testTagPrefix}_switch")
                    )

                    IconButton(
                        onClick = { if (isEnabled) onToggleExpand() },
                        enabled = isEnabled,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("${testTagPrefix}_expand_button")
                    ) {
                        Icon(
                            imageVector = if (isExpanded && isEnabled) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "توسيع الخيارات",
                            tint = if (isEnabled) TextSilver else TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (conflictWarning != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = conflictWarning,
                    color = DangerRed.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("${testTagPrefix}_conflict_warning")
                )
            }

            AnimatedVisibility(
                visible = isEnabled && isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    expandedContent()
                }
            }
        }
    }
}
