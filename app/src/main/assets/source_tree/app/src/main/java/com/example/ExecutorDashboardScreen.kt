package com.example

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CodeEditor
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Suggestion data holder representation
data class SmartSuggestion(
    val title: String,
    val description: String,
    val icon: String,
    val command: String
)

// Command History record
data class ExecutedCommandRecord(
    val timestamp: String,
    val command: String,
    val isDryRun: Boolean,
    val status: String, // "SUCCESS", "ERROR"
    val outputSummary: String
)

// Real-time Type-ahead suggestion data holder
data class TypeAheadSuggestion(
    val token: String,
    val label: String,
    val subtitle: String,
    val type: String, // "COMMAND", "PARAMETER", "EXAMPLE"
    val fullExample: String
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ExecutorDashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseDirPath = viewModel.baseDirSetting.collectAsState().value

    // Interactive States
    var selectedTemplate by remember { mutableStateOf<String?>(null) }
    var smartSuggestions by remember { mutableStateOf<List<SmartSuggestion>>(emptyList()) }
    var rawCommandStr by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf("جاهز لاستلام أوامر المنفذ البرمجية... ⚡") }
    var isExecutingCommand by remember { mutableStateOf(false) }

    // Command History State List
    val commandHistory = remember { mutableStateListOf<ExecutedCommandRecord>() }

    // Hardcoded rich database of syntax suggestions
    val allTypeAheadItems = remember(baseDirPath) {
        listOf(
            TypeAheadSuggestion(
                token = "@executor:scan",
                label = "فحص المجلد (scan)",
                subtitle = "مسح المجلدات والملفات وهيكلتها",
                type = "COMMAND",
                fullExample = "@executor:scan --path=\"$baseDirPath\" --recursive"
            ),
            TypeAheadSuggestion(
                token = "@executor:move",
                label = "نقل وترتيب (move)",
                subtitle = "نقل وتصنيف الملفات تلقائياً بالتزامن",
                type = "COMMAND",
                fullExample = "@executor:move --path=\"$baseDirPath\" --dest=\"archives\""
            ),
            TypeAheadSuggestion(
                token = "@executor:selftest",
                label = "فحص ذاتي (selftest)",
                subtitle = "التحقق من سلامة وصلاحيات النظام",
                type = "COMMAND",
                fullExample = "@executor:selftest"
            ),
            TypeAheadSuggestion(
                token = "@executor:report",
                label = "تقرير التحليل (report)",
                subtitle = "توليد كشف مفصل بإحصائيات الملفات والموارد",
                type = "COMMAND",
                fullExample = "@executor:report --path=\"$baseDirPath\""
            ),
            TypeAheadSuggestion(
                token = "@executor:organize",
                label = "تنظيم ذكي (organize)",
                subtitle = "فرز وترتيب الملفات حسب الامتداد أو التاريخ",
                type = "COMMAND",
                fullExample = "@executor:organize --path=\"$baseDirPath\" --rules=\"by_type\""
            ),
            TypeAheadSuggestion(
                token = "@executor:clean",
                label = "تنظيف المهملات (clean)",
                subtitle = "مسح الملفات المؤقتة والمهملات والملفات الفارغة",
                type = "COMMAND",
                fullExample = "@executor:clean --path=\"$baseDirPath\" --older-than=14d --dry-run"
            ),
            TypeAheadSuggestion(
                token = "--path=",
                label = "مسار المجلد (--path)",
                subtitle = "تحديد مجلد العمل الأساسي المستهدف",
                type = "PARAMETER",
                fullExample = "--path=\"$baseDirPath\""
            ),
            TypeAheadSuggestion(
                token = "--dest=",
                label = "مسار الوجهة (--dest)",
                subtitle = "مجلد الهدف المعد لحفظ الملفات المنقولة",
                type = "PARAMETER",
                fullExample = "--dest=\"$baseDirPath/Organized\""
            ),
            TypeAheadSuggestion(
                token = "--recursive",
                label = "بحث متداخل (--recursive)",
                subtitle = "مسح جميع المجلدات الفرعية والعميقة بالكامل",
                type = "PARAMETER",
                fullExample = "--recursive"
            ),
            TypeAheadSuggestion(
                token = "--dry-run",
                label = "محاكاة آمنة (--dry-run)",
                subtitle = "معاينة واختبار دون تغييرات حقيقية على المجلدات",
                type = "PARAMETER",
                fullExample = "--dry-run"
            ),
            TypeAheadSuggestion(
                token = "--filter=",
                label = "فيلتر الملفات (--filter)",
                subtitle = "حصر فحص ملفات معينة (مثال: --filter=\"*.jpg\")",
                type = "PARAMETER",
                fullExample = "--filter=\"*.mp4\""
            ),
            TypeAheadSuggestion(
                token = "--rules=",
                label = "قواعد الفرز (--rules)",
                subtitle = "تأخذ القيمة \"by_type\" أو \"by_date\" أو \"by_size\"",
                type = "PARAMETER",
                fullExample = "--rules=\"by_type\""
            ),
            TypeAheadSuggestion(
                token = "--older-than=",
                label = "عمر الملفات (--older-than)",
                subtitle = "حذف أو فرز الملفات الأقدم من المدى المحدد (مثال: 30d)",
                type = "PARAMETER",
                fullExample = "--older-than=30d"
            ),
            TypeAheadSuggestion(
                token = "--format=",
                label = "تنسيق العرض (--format)",
                subtitle = "نوع صيغة مخرجات الشاشة: text أو json",
                type = "PARAMETER",
                fullExample = "--format=json"
            )
        )
    }

    // Interactive suggestions filter
    val currentTypeAheadSuggestions = remember(rawCommandStr, baseDirPath) {
        val lastWord = rawCommandStr.split("\\s+".toRegex()).lastOrNull() ?: ""
        if (lastWord.startsWith("-")) {
            allTypeAheadItems.filter { 
                it.type == "PARAMETER" && it.token.startsWith(lastWord, ignoreCase = true)
            }.ifEmpty { 
                allTypeAheadItems.filter { it.type == "PARAMETER" }
            }
        } else if (lastWord.startsWith("@")) {
            allTypeAheadItems.filter { 
                it.type == "COMMAND" && it.token.startsWith(lastWord, ignoreCase = true)
            }.ifEmpty {
                allTypeAheadItems.filter { it.type == "COMMAND" }
            }
        } else {
            allTypeAheadItems.filter {
                it.token.contains(lastWord, ignoreCase = true) ||
                it.label.contains(lastWord, ignoreCase = true) ||
                it.subtitle.contains(lastWord, ignoreCase = true)
            }.ifEmpty {
                allTypeAheadItems
            }
        }
    }

    val addHistoryEntry: (String, Boolean, String) -> Unit = { cmd, isDry, out ->
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val isErr = out.contains("error", ignoreCase = true) || 
                    out.contains("failed", ignoreCase = true) || 
                    out.contains("invalid", ignoreCase = true) || 
                    out.contains("لم يتم", ignoreCase = true) || 
                    out.contains("خطأ", ignoreCase = true)
        val status = if (isErr) "ERROR" else "SUCCESS"
        val summary = if (out.length > 120) out.take(120) + "..." else out
        commandHistory.add(0, ExecutedCommandRecord(timeStr, cmd, isDry, status, summary))
    }

    val insertToken: (String) -> Unit = { token ->
        val trimmed = rawCommandStr.trim()
        if (trimmed.isEmpty()) {
            rawCommandStr = token
        } else {
            val words = trimmed.split("\\s+".toRegex()).toMutableList()
            val lastWord = words.lastOrNull() ?: ""
            if (lastWord.isNotEmpty() && (token.startsWith(lastWord, ignoreCase = true) || lastWord.startsWith("@") || lastWord.startsWith("-") || lastWord.contains("="))) {
                words[words.size - 1] = token
                rawCommandStr = words.joinToString(" ")
            } else {
                rawCommandStr = "$trimmed $token"
            }
        }
    }

    // Directory Browser states for Forms
    var showDirBrowserForSource by remember { mutableStateOf(false) }
    var showDirBrowserForDest by remember { mutableStateOf(false) }
    var currentTargetField by remember { mutableStateOf("organize_path") } // Which field to fill on directory selection

    // Dynamic Form states
    var organizePath by remember { mutableStateOf(baseDirPath) }
    var organizeOption by remember { mutableStateOf("type") } // type, extension, date
    
    var backupSource by remember { mutableStateOf(baseDirPath) }
    var backupDest by remember { mutableStateOf("") }
    
    var renamePath by remember { mutableStateOf(baseDirPath) }
    var renamePattern by remember { mutableStateOf("{name}_{date}") }

    var reportPath by remember { mutableStateOf(baseDirPath) }
    
    var cleanPath by remember { mutableStateOf(baseDirPath) }
    var cleanOption by remember { mutableStateOf("duplicates") } // duplicates, empty_dirs, temp_files

    // Scanning and loading Smart Suggestions
    LaunchedEffect(baseDirPath) {
        scope.launch(Dispatchers.IO) {
            val suggestionsList = generateSmartSuggestions(baseDirPath, context)
            withContext(Dispatchers.Main) {
                smartSuggestions = suggestionsList
            }
        }
    }

    // Direct folder select handler
    val handleFolderSelected: (String) -> Unit = { selectedPath ->
        when (currentTargetField) {
            "organize_path" -> organizePath = selectedPath
            "backup_source" -> backupSource = selectedPath
            "backup_dest" -> backupDest = selectedPath
            "rename_path" -> renamePath = selectedPath
            "report_path" -> reportPath = selectedPath
            "clean_path" -> cleanPath = selectedPath
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("executor_dashboard_scrollable"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Dashboard Banner Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(SlateBg, CardSlateBg))
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚙️", fontSize = 18.sp)
                            Text(
                                text = "لوحة المنفذ التفاعلية (Dashboard)",
                                color = BrightGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تحكّم في الملفات والمشاريع آلياً بمستويات تحكم فائقة الذكاء وبأداء مبهر للغاية.",
                            color = TextSilver,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // 2. Smart Suggestions Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("🧠", fontSize = 16.sp)
                    Text(
                        text = "أقترح عليك:",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (smartSuggestions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(GlassWhite, RoundedCornerShape(16.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "كل شيء منظم ورائع! لم يتم رصد أي ملفات مكررة أو فوضوية.",
                            color = TextSilver,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        smartSuggestions.forEach { suggestion ->
                            SuggestionCard(
                                suggestion = suggestion,
                                onExecute = {
                                    terminalOutput = "جاري تنفيذ الأمر الذكي المستهدف...\n"
                                    isExecutingCommand = true
                                    viewModel.executeSingleCommand(suggestion.command) { output ->
                                        isExecutingCommand = false
                                        terminalOutput = "📥 [مخرج الاقتراح الذكي]:\n$output"
                                        addHistoryEntry(suggestion.command, false, output)
                                        Toast.makeText(context, "🎉 تم تنفيذ الاقتراح بنجاح!", Toast.LENGTH_SHORT).show()
                                        // Refresh suggestions
                                        scope.launch(Dispatchers.IO) {
                                            val nextSugs = generateSmartSuggestions(baseDirPath, context)
                                            withContext(Dispatchers.Main) {
                                                smartSuggestions = nextSugs
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Command Ready Templates Area
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("📋", fontSize = 16.sp)
                    Text(
                        text = "قوالب أوامر جاهزة:",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable row of template badges
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        TemplateChip(
                            label = "🗂️ تنظيم ملفات",
                            isSelected = selectedTemplate == "organize",
                            onClick = { selectedTemplate = if (selectedTemplate == "organize") null else "organize" }
                        )
                    }
                    item {
                        TemplateChip(
                            label = "🛡️ نسخ احتياطي",
                            isSelected = selectedTemplate == "backup",
                            onClick = { selectedTemplate = if (selectedTemplate == "backup") null else "backup" }
                        )
                    }
                    item {
                        TemplateChip(
                            label = "✏️ تسمية ذكية",
                            isSelected = selectedTemplate == "rename",
                            onClick = { selectedTemplate = if (selectedTemplate == "rename") null else "rename" }
                        )
                    }
                    item {
                        TemplateChip(
                            label = "📊 تقرير تفصيلي",
                            isSelected = selectedTemplate == "report",
                            onClick = { selectedTemplate = if (selectedTemplate == "report") null else "report" }
                        )
                    }
                    item {
                        TemplateChip(
                            label = "🗑️ تنظيف مكررات",
                            isSelected = selectedTemplate == "clean",
                            onClick = { selectedTemplate = if (selectedTemplate == "clean") null else "clean" }
                        )
                    }
                    item {
                        TemplateChip(
                            label = "+ مخصص",
                            isSelected = selectedTemplate == "custom",
                            onClick = { selectedTemplate = if (selectedTemplate == "custom") null else "custom" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Expandable Template panels
                AnimatedVisibility(
                    visible = selectedTemplate != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .background(CardSlateBg)
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = getTemplateTitle(selectedTemplate),
                                color = MetallicGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Divider(color = GlassBorder)

                            // Load content dynamically depending on chosen template
                            when (selectedTemplate) {
                                "organize" -> {
                                    TemplateOrganizeView(
                                        pathValue = organizePath,
                                        onPathChange = { organizePath = it },
                                        optionValue = organizeOption,
                                        onOptionChange = { organizeOption = it },
                                        onBrowseFolder = {
                                            currentTargetField = "organize_path"
                                            showDirBrowserForSource = true
                                        },
                                        onCreatePlan = {
                                            val commands = generateOrganizePlan(organizePath, organizeOption)
                                            rawCommandStr = commands
                                            Toast.makeText(context, "🗒️ تم توليد خطة التنظيم ومزامنتها بالمحرر!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                "backup" -> {
                                    TemplateBackupView(
                                        sourceValue = backupSource,
                                        onSourceChange = { backupSource = it },
                                        destValue = backupDest,
                                        onDestChange = { backupDest = it },
                                        onBrowseSource = {
                                            currentTargetField = "backup_source"
                                            showDirBrowserForSource = true
                                        },
                                        onBrowseDest = {
                                            currentTargetField = "backup_dest"
                                            showDirBrowserForDest = true
                                        },
                                        onGenerateCommand = {
                                            if (backupDest.isBlank()) {
                                                backupDest = "$backupSource/Backups/Backup_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}"
                                            }
                                            rawCommandStr = "@executor:copy-safe --path=\"$backupSource\" --dest=\"$backupDest\""
                                            Toast.makeText(context, "🗒️ تم تحضير أمر النسخ الاحتياطي في المحرر!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                "rename" -> {
                                    TemplateRenameView(
                                        pathValue = renamePath,
                                        onPathChange = { renamePath = it },
                                        patternValue = renamePattern,
                                        onPatternChange = { renamePattern = it },
                                        onBrowseFolder = {
                                            currentTargetField = "rename_path"
                                            showDirBrowserForSource = true
                                        },
                                        onPreview = {
                                            val renamePlan = generateRenamePlan(renamePath, renamePattern)
                                            rawCommandStr = renamePlan
                                            Toast.makeText(context, "✏️ تمت معاينة التسميات بنجاح وإسقاط الأوامر في المحرر!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                "report" -> {
                                    TemplateReportView(
                                        pathValue = reportPath,
                                        onPathChange = { reportPath = it },
                                        onBrowseFolder = {
                                            currentTargetField = "report_path"
                                            showDirBrowserForSource = true
                                        },
                                        onGenerate = {
                                            rawCommandStr = "@executor:report --path=\"$reportPath\""
                                            Toast.makeText(context, "🗒️ تم تدوير الأمر إلى المحرر!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                "clean" -> {
                                    TemplateCleanView(
                                        pathValue = cleanPath,
                                        onPathChange = { cleanPath = it },
                                        optionValue = cleanOption,
                                        onOptionChange = { cleanOption = it },
                                        onBrowseFolder = {
                                            currentTargetField = "clean_path"
                                            showDirBrowserForSource = true
                                        },
                                        onGenerate = {
                                            rawCommandStr = if (cleanOption == "duplicates") {
                                                "@executor:scan --path=\"$cleanPath\" --format=json"
                                            } else {
                                                "@executor:delete --path=\"$cleanPath\" --recursive"
                                            }
                                            Toast.makeText(context, "🗒️ تم نقل خطوات المسح/الحذف في المحرر!", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                                "custom" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "قم بكتابة أي أمر مطلق تريده هنا، أو حدده بتبويبات القوالب أعلاه وتصرف بالمعايير الحسابية المتاحة كخيارات.",
                                            color = TextSilver,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                        Button(
                                            onClick = {
                                                rawCommandStr = "@executor:selftest"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("تعبئة بأمر الفحص الذاتي ⚡", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Manual CLI Command Input Box
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("✍️", fontSize = 16.sp)
                    Text(
                        text = "محرر الأوامر اليدوي (Developer Console):",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Injecting our newly completed custom highlighted CodeEditor
                    CodeEditor(
                        value = rawCommandStr,
                        onValueChange = { rawCommandStr = it },
                        language = "executor",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("interactive_console"),
                        placeholder = "@executor:move --path=\"downloads\" --dest=\"images\" --dry-run"
                    )

                    // Dynamic Real-time Type-ahead Suggestion system
                    var selectedTypeAheadTab by remember { mutableStateOf("ALL") } // "ALL", "COMMAND", "PARAMETER"
                    
                    val filteredSuggestions = remember(currentTypeAheadSuggestions, selectedTypeAheadTab) {
                        when (selectedTypeAheadTab) {
                            "COMMAND" -> currentTypeAheadSuggestions.filter { it.type == "COMMAND" }
                            "PARAMETER" -> currentTypeAheadSuggestions.filter { it.type == "PARAMETER" }
                            else -> currentTypeAheadSuggestions
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassBlack, RoundedCornerShape(12.dp))
                            .border(1.dp, GlassBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .animateContentSize()
                            .padding(8.dp)
                    ) {
                        // Title and Filters
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("🪄", fontSize = 13.sp)
                                Text(
                                    text = "مساعد الإكمال التلقائي الذكي:",
                                    color = BrightGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            // Category Filter Switch
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("ALL" to "الكل", "COMMAND" to "أوامر ⚡", "PARAMETER" to "معاملات ⚙️").forEach { (tabId, tabName) ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (selectedTypeAheadTab == tabId) GoldGlassBg else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (selectedTypeAheadTab == tabId) BrightGold.copy(alpha = 0.5f) else Color.Transparent,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { selectedTypeAheadTab = tabId }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = tabName,
                                            color = if (selectedTypeAheadTab == tabId) BrightGold else TextSilver,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        if (filteredSuggestions.isEmpty()) {
                            Text(
                                text = "اكتب '@' أو '-' للمطابقة التلقائية مع معجم الأوامر الذكية...",
                                color = TextGray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(vertical = 4.dp).align(Alignment.CenterHorizontally)
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(filteredSuggestions) { item ->
                                    Box(
                                        modifier = Modifier
                                            .width(220.dp)
                                            .background(GlassWhite, RoundedCornerShape(10.dp))
                                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                            .padding(8.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = item.token,
                                                    color = if (item.type == "COMMAND") BrightGold else Color(0xFF60A5FA),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp
                                                )
                                                StatusBadge(
                                                    label = if (item.type == "COMMAND") "أمر" else "معامل",
                                                    isError = false,
                                                    color = if (item.type == "COMMAND") BrightGold else Color(0xFF3B82F6)
                                                )
                                            }
                                            
                                            Text(
                                                text = item.label,
                                                color = TextSilver,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            
                                            Text(
                                                text = item.subtitle,
                                                color = TextGray,
                                                fontSize = 9.sp,
                                                lineHeight = 12.sp,
                                                maxLines = 2
                                            )
                                            
                                            Spacer(modifier = Modifier.height(2.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        insertToken(item.token)
                                                        Toast.makeText(context, "🗒️ تم إدراج الشفرة!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f).height(26.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("✍️ إدراج", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                                
                                                Button(
                                                    onClick = {
                                                        rawCommandStr = item.fullExample
                                                        Toast.makeText(context, "🚀 تم تطبيق المثال كاملاً!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = BrightGold, contentColor = SlateBg),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f).height(26.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("🗒️ مثال كامل", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Dry-Run Simulation Command Button
                        Button(
                            onClick = {
                                if (rawCommandStr.isBlank()) {
                                    Toast.makeText(context, "الرجاء تحديد نوع الأمر أولاً!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                var cmd = rawCommandStr.trim()
                                if (!cmd.contains("--dry-run")) {
                                    cmd += " --dry-run"
                                }
                                isExecutingCommand = true
                                terminalOutput = "جاري تنفيذ معامل المحاكاة الآمنة (Dry-Run)...\n"
                                viewModel.executeSingleCommand(cmd) { out ->
                                    isExecutingCommand = false
                                    terminalOutput = out
                                    addHistoryEntry(cmd, true, out)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("sim_preview_action"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CardSlateBg,
                                contentColor = BrightGold
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GlassBorder)
                        ) {
                            Text("👁️ معاينة آمنة", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }

                        // Real Immediate Execution Action button
                        Button(
                            onClick = {
                                if (rawCommandStr.isBlank()) {
                                    Toast.makeText(context, "الرجاء إدخال أمر للمنفذ أولاً!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isExecutingCommand = true
                                terminalOutput = "جاري استنطاق وتحليل الكتل للأمر المطلق...\n"
                                viewModel.executeSingleCommand(rawCommandStr) { out ->
                                    isExecutingCommand = false
                                    terminalOutput = out
                                    addHistoryEntry(rawCommandStr, false, out)
                                    Toast.makeText(context, "⚡ تمت عملية التنفيذ المباشر!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(42.dp)
                                .testTag("sim_exec_action"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrightGold,
                                contentColor = SlateBg
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isExecutingCommand) {
                                    CircularProgressIndicator(color = SlateBg, modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                                } else {
                                    Text("⚡ تنفيذ فوري", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4b. Quick Command Toolbar & Helper Presets
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("💡", fontSize = 15.sp)
                    Text(
                        text = "اختصارات سريعة للتعبئة الفورية:",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ActionPresetChip(
                            label = "@executor:scan --recursive",
                            onClick = {
                                rawCommandStr = "@executor:scan --path=\"$baseDirPath\" --recursive --format=json"
                                Toast.makeText(context, "🗒️ تم ملء أمر الفحص الشامل!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    item {
                        ActionPresetChip(
                            label = "@executor:selftest",
                            onClick = {
                                rawCommandStr = "@executor:selftest"
                                Toast.makeText(context, "🗒️ تم ملء أمر الاختبار الذاتي!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    item {
                        ActionPresetChip(
                            label = "@executor:report --path",
                            onClick = {
                                rawCommandStr = "@executor:report --path=\"$baseDirPath\""
                                Toast.makeText(context, "🗒️ تم ملء أمر توليد التقرير!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    item {
                        ActionPresetChip(
                            label = "@executor:move --dry-run",
                            onClick = {
                                rawCommandStr = "@executor:move --path=\"$baseDirPath\" --dest=\"$baseDirPath/Archive\" --dry-run"
                                Toast.makeText(context, "🗒️ تم ملء أمر التجربة الآمنة للنقل!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // 4c. Interactive Tips and Advice for Advanced Users
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .background(CardSlateBg)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🚀", fontSize = 16.sp)
                        Text(
                            text = "نصائح المنفذ للمحترفين (Pro Tips):",
                            color = BrightGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TipItem(text = "استخدم معامل --dry-run دوماً قبل تنفيذ أوامر الحذف أو النقل لمعاينة النتائج دون تعديل حقيقي.")
                        TipItem(text = "يدعم المنفذ الهيكلي التسمية بالأنماط الذكية مثل {name}_{date} لتلقيم التواريخ ديناميكياً.")
                        TipItem(text = "تصفح المجلدات من أي زر تصفح 📁 لتعبئة المسارات الطويلة تلقائياً وتفادي أخطاء الإملاء.")
                        TipItem(text = "يمكنك تشغيل خدمات ميزة التقاط الحافظة (Clipboard) لتنفيذ الأوامر بمجرد نسخها من أي تطبيق!")
                    }
                }
            }
        }

        // 4d. Command History log box
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📜", fontSize = 15.sp)
                        Text(
                            text = "سجل الأوامر المنفذة مؤخراً (Command History):",
                            color = BrightGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    if (commandHistory.isNotEmpty()) {
                        Text(
                            text = "مسح الكل",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    commandHistory.clear()
                                    Toast.makeText(context, "🧹 تم مسح سجل الأوامر!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (commandHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(GlassWhite, RoundedCornerShape(18.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("⚡", fontSize = 20.sp)
                            Text(
                                text = "لم تقم بتنفيذ أي أمر برمجياً بعد.",
                                color = TextSilver,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "الأوامر التي تنفذها ستظهر هنا مع حالتها ومخرجاتها لسهولة المتابعة والتكرار.",
                                color = TextGray,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        commandHistory.forEach { record ->
                            var isExpanded by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GlassWhite, RoundedCornerShape(16.dp))
                                    .border(BorderStroke(1.dp, if (record.status == "ERROR") Color(0xFFEF4444).copy(alpha = 0.5f) else GlassBorder), RoundedCornerShape(16.dp))
                                    .clickable { isExpanded = !isExpanded }
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (record.status == "ERROR") {
                                                Text("❌", fontSize = 12.sp)
                                            } else if (record.isDryRun) {
                                                Text("👁️", fontSize = 12.sp)
                                            } else {
                                                Text("✅", fontSize = 12.sp)
                                            }
                                            
                                            Text(
                                                text = record.command,
                                                color = if (record.status == "ERROR") Color(0xFFFCA5A5) else TextSilver,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = if (isExpanded) 5 else 1
                                            )
                                        }
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = record.timestamp,
                                                color = TextGray,
                                                fontSize = 9.sp
                                            )
                                            Text(
                                                text = if (isExpanded) "▲" else "▼",
                                                color = BrightGold,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = isExpanded) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                        ) {
                                            Divider(color = GlassBorder)
                                            
                                            // Status Badge row
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                StatusBadge(
                                                    label = if (record.status == "ERROR") "فشل التنفيذ" else "تم بنجاح",
                                                    isError = record.status == "ERROR"
                                                )
                                                if (record.isDryRun) {
                                                    StatusBadge(
                                                        label = "محاكاة آمنة (Dry-Run)",
                                                        isError = false,
                                                        color = Color(0xFF3B82F6)
                                                    )
                                                }
                                            }

                                            // Feedback log box
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(GlassBlack, RoundedCornerShape(8.dp))
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = record.outputSummary,
                                                    color = if (record.status == "ERROR") Color(0xFFFCA5A5) else EmeraldGlow,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp
                                                )
                                            }

                                            // Action controls
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Load/Copy command
                                                Button(
                                                    onClick = {
                                                        rawCommandStr = record.command
                                                        Toast.makeText(context, "🗒️ تم نسخ الأمر إلى المحرر!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("📋 نسخ للمحرر", fontSize = 10.sp)
                                                }

                                                // Run command immediately
                                                Button(
                                                    onClick = {
                                                        isExecutingCommand = true
                                                        terminalOutput = "جاري إعادة تشغيل الأمر المؤرشف...\n"
                                                        viewModel.executeSingleCommand(record.command) { out ->
                                                            isExecutingCommand = false
                                                            terminalOutput = out
                                                            addHistoryEntry(record.command, record.isDryRun, out)
                                                            Toast.makeText(context, "⚡ تم تنفيذ السجل بنجاح!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = BrightGold, contentColor = SlateBg),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("⚡ تشغيل فوري", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Console output logs logger terminal view
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "شاشة كاشف مخرجات الطرفية (Output Log):",
                    color = TextSilver,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(GlassBlack, RoundedCornerShape(18.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = terminalOutput,
                        color = EmeraldGlow,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    // Modal Browsers trigger dialog hooks using the MainActivity's DirectoryBrowserDialog Composable!
    if (showDirBrowserForSource) {
        DirectoryBrowserDialog(
            isOpen = showDirBrowserForSource,
            onDismiss = { showDirBrowserForSource = false },
            initialPath = baseDirPath,
            onConfirm = { path ->
                handleFolderSelected(path)
                showDirBrowserForSource = false
            }
        )
    }

    if (showDirBrowserForDest) {
        DirectoryBrowserDialog(
            isOpen = showDirBrowserForDest,
            onDismiss = { showDirBrowserForDest = false },
            initialPath = baseDirPath,
            onConfirm = { path ->
                handleFolderSelected(path)
                showDirBrowserForDest = false
            }
        )
    }
}

// =====================================================================
// Custom Reusable Sub-Views and Widgets for templates configuration
// =====================================================================

@Composable
fun SuggestionCard(
    suggestion: SmartSuggestion,
    onExecute: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassWhite, RoundedCornerShape(18.dp))
            .border(BorderStroke(1.dp, GlassBorder), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(GlassWhiteMedium, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(suggestion.icon, fontSize = 16.sp)
                }

                Column {
                    Text(
                        text = suggestion.title,
                        color = TextSilver,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = suggestion.description,
                        color = TextGray,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick Execution Action circle button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Brush.linearGradient(listOf(BrightGold, MetallicGold)),
                        CircleShape
                    )
                    .clickable { onExecute() },
                contentAlignment = Alignment.Center
            ) {
                Text("▶️", fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

@Composable
fun TemplateChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerBg = if (isSelected) GoldGlassBg else GlassWhite
    val borderClr = if (isSelected) MetallicGold else GlassBorder
    val labelClr = if (isSelected) BrightGold else TextSilver

    Box(
        modifier = Modifier
            .background(containerBg, RoundedCornerShape(10.dp))
            .border(1.dp, borderClr, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = labelClr,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// 1. Template: Organize files configuration view
@Composable
fun TemplateOrganizeView(
    pathValue: String,
    onPathChange: (String) -> Unit,
    optionValue: String,
    onOptionChange: (String) -> Unit,
    onBrowseFolder: () -> Unit,
    onCreatePlan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("مسار المجلد المراد تنظيمه وتقسيمه:", color = TextSilver, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = pathValue,
                onValueChange = onPathChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextSilver, fontSize = 10.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MetallicGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassBlack,
                    unfocusedContainerColor = GlassBlack
                )
            )
            Button(
                onClick = onBrowseFolder,
                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("تصفح 📁", fontSize = 11.sp)
            }
        }

        Text("طريقة التقسيم والتنظيم:", color = TextSilver, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("type" to "حسب النوع", "extension" to "حسب الامتداد", "date" to "حسب التاريخ").forEach { (key, title) ->
                val isSelected = optionValue == key
                val bg = if (isSelected) GoldGlassBg else GlassWhite
                val border = if (isSelected) MetallicGold else GlassBorder
                val textCol = if (isSelected) BrightGold else TextSilver

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(bg, RoundedCornerShape(6.dp))
                        .border(1.dp, border, RoundedCornerShape(6.dp))
                        .clickable { onOptionChange(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = textCol, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onCreatePlan,
            modifier = Modifier.fillMaxWidth().height(38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("🗒️ إنشاء خطة تنظيم الملفات", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// 2. Template: Backup configuration view
@Composable
fun TemplateBackupView(
    sourceValue: String,
    onSourceChange: (String) -> Unit,
    destValue: String,
    onDestChange: (String) -> Unit,
    onBrowseSource: () -> Unit,
    onBrowseDest: () -> Unit,
    onGenerateCommand: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("مسار المجلد المطلوب نسخه احتياطياً (المصدر):", color = TextSilver, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = sourceValue,
                onValueChange = onSourceChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextSilver, fontSize = 10.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MetallicGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassBlack,
                    unfocusedContainerColor = GlassBlack
                )
            )
            Button(
                onClick = onBrowseSource,
                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("تصفح 📁", fontSize = 11.sp)
            }
        }

        Text("مسار مجلد الوجهة للنسخ الاحتياطي (اختياري):", color = TextSilver, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = destValue,
                onValueChange = onDestChange,
                placeholder = { Text("توليد تلقائي لاسم فريد داخل Backups...", color = TextMuted, fontSize = 10.sp) },
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextSilver, fontSize = 10.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MetallicGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassBlack,
                    unfocusedContainerColor = GlassBlack
                )
            )
            Button(
                onClick = onBrowseDest,
                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("تصفح 📁", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onGenerateCommand,
            modifier = Modifier.fillMaxWidth().height(38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("📁 نسخ احتياطي الآن وصياغة الكود", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// 3. Template: Rename files view
@Composable
fun TemplateRenameView(
    pathValue: String,
    onPathChange: (String) -> Unit,
    patternValue: String,
    onPatternChange: (String) -> Unit,
    onBrowseFolder: () -> Unit,
    onPreview: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("مجلد إعادة التسمية المستهدف:", color = TextSilver, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = pathValue,
                onValueChange = onPathChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextSilver, fontSize = 10.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MetallicGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassBlack,
                    unfocusedContainerColor = GlassBlack
                )
            )
            Button(
                onClick = onBrowseFolder,
                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("تصفح 📁", fontSize = 11.sp)
            }
        }

        Text("شكل أو نمط التسمية المقترحة:", color = TextSilver, fontSize = 11.sp)
        OutlinedTextField(
            value = patternValue,
            onValueChange = onPatternChange,
            placeholder = { Text("{name}_{date}  أو  {title}_{index}", color = TextMuted, fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MetallicGold,
                unfocusedBorderColor = GlassBorder,
                focusedContainerColor = GlassBlack,
                unfocusedContainerColor = GlassBlack
            )
        )

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth().height(38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("👁️ معاينة الأسماء الجديدة المقترحة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// 4. Template: Detailed Report generator View
@Composable
fun TemplateReportView(
    pathValue: String,
    onPathChange: (String) -> Unit,
    onBrowseFolder: () -> Unit,
    onGenerate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("مسار المجلد المطلوب توليد التقرير له:", color = TextSilver, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = pathValue,
                onValueChange = onPathChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextSilver, fontSize = 10.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MetallicGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassBlack,
                    unfocusedContainerColor = GlassBlack
                )
            )
            Button(
                onClick = onBrowseFolder,
                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("تصفح 📁", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth().height(38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("📋 جرد وتوليد تقرير هيكلي شامل", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// 5. Template: Clean duplicates view
@Composable
fun TemplateCleanView(
    pathValue: String,
    onPathChange: (String) -> Unit,
    optionValue: String,
    onOptionChange: (String) -> Unit,
    onBrowseFolder: () -> Unit,
    onGenerate: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text("مسار المجلد للتنظيف التلقائي:", color = TextSilver, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = pathValue,
                onValueChange = onPathChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = TextSilver, fontSize = 10.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MetallicGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassBlack,
                    unfocusedContainerColor = GlassBlack
                )
            )
            Button(
                onClick = onBrowseFolder,
                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("تصفح 📁", fontSize = 11.sp)
            }
        }

        Text("خيار التنظيف:", color = TextSilver, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("duplicates" to "البحث عن المكررات", "temp" to "حذف الملفات المؤقتة").forEach { (key, title) ->
                val isSelected = optionValue == key
                val bg = if (isSelected) GoldGlassBg else GlassWhite
                val border = if (isSelected) MetallicGold else GlassBorder
                val textCol = if (isSelected) BrightGold else TextSilver

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(bg, RoundedCornerShape(6.dp))
                        .border(1.dp, border, RoundedCornerShape(6.dp))
                        .clickable { onOptionChange(key) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = textCol, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth().height(38.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("🗑️ بحث وتوزيع خطة التنظيف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Get proper title for selected templates
fun getTemplateTitle(template: String?): String {
    return when (template) {
        "organize" -> "🗂️ تهيئة خيار تنظيم وتقسيم الملفات"
        "backup" -> "🛡️ حماية ونسخ احتياطي فوري للمشروع"
        "rename" -> "✏️ واجهة إعادة التسمية السريعة بالأنماط"
        "report" -> "📊 توليد جرد وإحصائيات تفصيلية"
        "clean" -> "🗑️ رصد المكررات وتطهير المجلدات"
        else -> "⚡ محرر التوجيهات المخصص"
    }
}

// =====================================================================
// Interactive Smart Logic Algorithms for analyzing plans & files
// =====================================================================

/**
 * 💡 Scans the project directory (and commonly downloads) for cluttered states
 */
fun generateSmartSuggestions(projectPath: String, context: Context): List<SmartSuggestion> {
    val result = mutableListOf<SmartSuggestion>()
    val rootFile = File(projectPath)
    if (!rootFile.exists() || !rootFile.isDirectory) {
        return getFallbackSmartSuggestions(projectPath)
    }

    // Scan files in workspace root
    val filesList = rootFile.listFiles()?.filter { it.isFile } ?: emptyList()
    
    // Group files by type/extension and search for duplicates
    val extensions = filesList.map { it.extension.lowercase(Locale.ROOT) }.distinct()
    
    // Check Clutter
    if (filesList.size >= 3 && extensions.size >= 2) {
        result.add(
            SmartSuggestion(
                title = "📁 تنظيم مجلد المشروع الحالي",
                description = "تم رصد ${filesList.size} ملفات متنوعة مبعثرة في المجلد الرئيسي. سأنقلهم إلى مجلدات فرعية منسقة حسب نوعها.",
                icon = "📁",
                command = generateOrganizePlan(projectPath, "type")
            )
        )
    }

    // Check Downloads directory safety if accessible
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir.exists() && downloadsDir.isDirectory) {
            val dFiles = downloadsDir.listFiles()?.filter { it.isFile } ?: emptyList()
            if (dFiles.size >= 4) {
                result.add(
                    SmartSuggestion(
                        title = "📁 تنظيم مجلد 'التنزيلات' الشائع",
                        description = "تم العثور على ملفات غير مصنفة بمجلد التنزيلات (${dFiles.size} ملفات). سأنظمهم لك فدرالياً للراحة.",
                        icon = "⚡",
                        command = generateOrganizePlan(downloadsDir.absolutePath, "type")
                    )
                )
            }
        }
    } catch (e: Exception) {
        // Safe skip downloads check on restriction
    }

    // Suggest a project backup (Always useful to have one)
    val formattedDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    result.add(
        SmartSuggestion(
            title = "🛡️ نسخ احتياطي لمشروعك الحالي",
            description = "أنشئ نسخة من كل محتويات مجلد العمل لتوفير حماية كاملة ضد ضياع التعديلات أو الأخطاء.",
            icon = "🛡️",
            command = "@executor:copy-safe --path=\"$projectPath\" --dest=\"${rootFile.parent ?: projectPath}/Backups/Backup_$formattedDate\""
        )
    )

    // Suggest look for duplicates/temp files
    val tempFiles = filesList.filter { it.name.contains(".tmp") || it.name.contains("temp") || it.name.contains("_copy") }
    if (tempFiles.isNotEmpty()) {
        result.add(
            SmartSuggestion(
                title = "🗑️ تنظيف ملفات مؤقتة ومكررة",
                description = "تم رصد ملفات مؤقتة مسجلة ببادئات تكرار. دعنا نطهر المساحة آلياً.",
                icon = "🗑️",
                command = "@executor:delete --path=\"${tempFiles.first().absolutePath}\""
            )
        )
    }

    // Return maximum 3 suggestions to avoid screen overflow
    return result.take(3)
}

/**
 * Fallback recommendations if folder is completely empty or access is restricted
 */
fun getFallbackSmartSuggestions(projectPath: String): List<SmartSuggestion> {
    val formattedDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
    return listOf(
        SmartSuggestion(
            title = "📁 تنظيم مجلد 'التنزيلات' الافتراضي",
            description = "تحليل مجلد التنزيلات وتصنيف الملفات المبعثرة به تلقائياً حسب نوعها (صور ومستندات).",
            icon = "📁",
            command = "@executor:mkdir --path=\"/sdcard/Download/OrganizedImages\"\n@executor:mkdir --path=\"/sdcard/Download/OrganizedDocs\""
        ),
        SmartSuggestion(
            title = "🛡️ نسخ احتياطي دوري لبياناتك",
            description = "احمِ ملفاتك الهامة عبر أخذ نسخة متطابقة آمنة بضغطة واحدة إلى مسار الاحتفاظ.",
            icon = "🛡️",
            command = "@executor:copy-safe --path=\"$projectPath\" --dest=\"$projectPath/Backups/Backup_$formattedDate\""
        ),
        SmartSuggestion(
            title = "🔍 فحص الأداء الذاتي للمنظومة",
            description = "تشغيل الفحص الذاتي للمنفذ للتأكد من جاهزية المحرك وصلاحيات نظام التشغيل.",
            icon = "⚡",
            command = "@executor:selftest"
        )
    )
}

/**
 * Helper to produce complex organize script based on real files in folder
 */
fun generateOrganizePlan(folderPath: String, option: String): String {
    val dir = File(folderPath)
    if (!dir.exists() || !dir.isDirectory) {
        return "@executor:mkdir --path=\"$folderPath/Documents\"\n@executor:mkdir --path=\"$folderPath/Images\""
    }

    val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
    if (files.isEmpty()) {
        return "# المجلد فارغ حالياً! ولكن الهيكل النظري للتنظيم:\n@executor:mkdir --path=\"$folderPath/Documents\"\n@executor:mkdir --path=\"$folderPath/Images\"\n@executor:mkdir --path=\"$folderPath/Archives\""
    }

    val commands = StringBuilder()
    commands.appendLine("# خطة أتمتة تنظيم لـ: ${dir.name}")
    
    val targetDirs = mutableSetOf<String>()
    val movesList = mutableListOf<String>()

    files.forEach { file ->
        val ext = file.extension.lowercase(Locale.ROOT)
        val classification = when (option) {
            "type" -> {
                when {
                    ext in listOf("png", "jpg", "jpeg", "gif", "webp", "svg") -> "Images"
                    ext in listOf("pdf", "doc", "docx", "txt", "xlsx", "pptx", "csv") -> "Documents"
                    ext in listOf("zip", "rar", "7z", "tar", "gz") -> "Archives"
                    ext in listOf("mp3", "wav", "ogg", "mp4", "mkv", "avi") -> "Media"
                    else -> "Others"
                }
            }
            "extension" -> ext.uppercase(Locale.ROOT)
            "date" -> {
                val sdf = SimpleDateFormat("yyyy_MM", Locale.getDefault())
                sdf.format(Date(file.lastModified()))
            }
            else -> "Organized"
        }

        val targetSubpath = "$folderPath/$classification"
        targetDirs.add(targetSubpath)
        movesList.add("@executor:move --path=\"${file.absolutePath}\" --dest=\"$targetSubpath/${file.name}\"")
    }

    // Add mkdir commands
    targetDirs.forEach { d ->
        commands.appendLine("@executor:mkdir --path=\"$d\"")
    }
    
    // Add Move commands
    movesList.forEach { m ->
        commands.appendLine(m)
    }

    return commands.toString()
}

/**
 * Helper to generate smart renaming plan commands
 */
fun generateRenamePlan(folderPath: String, pattern: String): String {
    val dir = File(folderPath)
    if (!dir.exists() || !dir.isDirectory) {
        return "@executor:rename --path=\"$folderPath/doc.txt\" --new-name=\"Document.txt\""
    }

    val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
    if (files.isEmpty()) {
        return "# لا تتوفر أي ملفات تخصص إعادة التسمية بالمجلد الحالي الكائن!"
    }

    val commands = java.lang.StringBuilder()
    commands.appendLine("# خطة إعادة التسمية التلقائية بالنمط ($pattern)")
    
    val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    val dateStr = sdf.format(Date())

    files.forEachIndexed { index, file ->
        val ext = file.extension
        val baseName = file.nameWithoutExtension
        
        var nextName = pattern
            .replace("{name}", baseName)
            .replace("{date}", dateStr)
            .replace("{index}", (index + 1).toString())

        if (ext.isNotEmpty()) {
            nextName = "$nextName.$ext"
        }

        commands.appendLine("@executor:rename --path=\"${file.absolutePath}\" --new-name=\"$nextName\"")
    }

    return commands.toString()
}

// =====================================================================
// Custom Reusable Sub-components for advanced logging & tooltips
// =====================================================================

@Composable
fun ActionPresetChip(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(GlassWhiteMedium, RoundedCornerShape(8.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = BrightGold,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TipItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("✨", fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        Text(
            text = text,
            color = TextSilver,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

@Composable
fun StatusBadge(
    label: String,
    isError: Boolean,
    color: Color? = null
) {
    val containerBg = color?.copy(alpha = 0.15f) ?: if (isError) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f)
    val borderClr = color ?: if (isError) Color(0xFFEF4444) else Color(0xFF10B981)
    val labelClr = color ?: if (isError) Color(0xFFFCA5A5) else EmeraldGlow

    Box(
        modifier = Modifier
            .background(containerBg, RoundedCornerShape(6.dp))
            .border(1.dp, borderClr.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = labelClr,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
