package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.db.FileEntity
import com.example.db.LogEntity
import com.example.service.ClipboardMonitorService
import com.example.service.ClipboardAccessibilityService
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.AiTaskRecord
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.os.PowerManager
import android.os.Environment
import android.view.inputmethod.InputMethodManager

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val service = "${context.packageName}/${ClipboardAccessibilityService::class.java.canonicalName}"
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return enabled.contains(service) || enabled.contains("ClipboardAccessibilityService")
}

enum class AppFlowState {
    SPLASH,
    ONBOARDING,
    MAIN_APP
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize custom theme manager
        com.example.ui.theme.ThemeManager.init(applicationContext)
        val smartPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val goldenBubbleEnabled = smartPrefs.getBoolean("golden_bubble_enabled", true)
        if (goldenBubbleEnabled && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this))) {
            try {
                startService(Intent(this, com.example.service.GoldenBubbleService::class.java))
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to auto-start GoldenBubbleService: ${e.message}")
            }
        }
        
        // Dynamic orientation support and status bar adjustment
        setContent {
            MyApplicationTheme {
                val printHtmlTrigger = viewModel.printHtmlTrigger.collectAsState().value
                LaunchedEffect(printHtmlTrigger) {
                    if (printHtmlTrigger != null) {
                        com.example.util.PdfPrintHelper.printHtml(this@MainActivity, printHtmlTrigger)
                        viewModel.resetPrintHtmlTrigger()
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SlateBg
                ) {
                    val sharedPrefs = LocalContext.current.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                    var appState by remember {
                        val hasSeenOnboarding = sharedPrefs.getBoolean("has_seen_onboarding", false)
                        mutableStateOf(if (hasSeenOnboarding) AppFlowState.SPLASH else AppFlowState.ONBOARDING)
                    }

                    AnimatedContent(
                        targetState = appState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(600)) + slideInHorizontally(initialOffsetX = { it }) togetherWith
                            fadeOut(animationSpec = tween(600)) + slideOutHorizontally(targetOffsetX = { -it })
                        },
                        label = "app_flow_transitions"
                    ) { targetState ->
                        when (targetState) {
                            AppFlowState.ONBOARDING -> {
                                OnboardingScreen(
                                    onFinish = {
                                        appState = AppFlowState.SPLASH
                                    }
                                )
                            }
                            AppFlowState.SPLASH -> {
                                SplashScreen(
                                    onSplashComplete = {
                                        appState = AppFlowState.MAIN_APP
                                    }
                                )
                            }
                            AppFlowState.MAIN_APP -> {
                                var simulatedGoldenFrame by remember {
                                    mutableStateOf(sharedPrefs.getBoolean("simulate_golden_frame", true))
                                }

                                MainAppContent(
                                    viewModel = viewModel,
                                    simulatedGoldenFrame = simulatedGoldenFrame,
                                    onToggleSimulatedFrame = { newState ->
                                        simulatedGoldenFrame = newState
                                        sharedPrefs.edit().putBoolean("simulate_golden_frame", newState).apply()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkServiceStatus()
        viewModel.navigateToBaseDir() // refresh browser

        // Check clipboard for auto-processing on application focus
        viewModel.checkClipboard { resultMsg ->
            Toast.makeText(this, resultMsg, Toast.LENGTH_LONG).show()
        }
    }
}

// Tab selections state representation
enum class MainTab(val ltrTitle: String, val rtlTitle: String) {
    MONITOR("Monitor", "المراقب"),
    STORYTELLER("Storyteller", "القصة المصورة"),
    TREEDOC("TreeDoc", "الشجرية"),
    EXECUTOR("Executor", "المنفذ"),
    GEMINI("Gemini AI", "جمناي الذكي"),
    LINKS("Links", "الروابط"),
    PROJECTS("Projects", "المشاريع"),
    SETTINGS("Settings", "الإعدادات")
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    simulatedGoldenFrame: Boolean,
    onToggleSimulatedFrame: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(MainTab.MONITOR) }
    var showAIPromptHub by remember { mutableStateOf(false) }
    var showQuickActionsHub by remember { mutableStateOf(false) }
    var showHelpCenter by remember { mutableStateOf(false) }
    var showSourceExport by remember { mutableStateOf(false) }
    var showStyleBank by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val sharedPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
    var showBubbleTutorial by remember {
        mutableStateOf(!sharedPrefs.getBoolean("has_seen_bubble_tutorial", false))
    }

    // Permissions State variables
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPermissionsDashboard by remember { mutableStateOf(false) }
    var hasPostNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    // Permission launcher
    val requestNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPostNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "تم منح صلاحية الإشعارات بنجاح!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        val isAccessibility = isAccessibilityServiceEnabled(context)
        val isAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBattery = pm.isIgnoringBatteryOptimizations(context.packageName)
        val isOverlay = Settings.canDrawOverlays(context)

        if (!isAccessibility || !isAllFiles || !isBattery || !isOverlay) {
            showPermissionsDashboard = true
        } else if (!hasPostNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showPermissionDialog = true
        }
    }

    // Layout frame wrapper
    val frameModifier = if (simulatedGoldenFrame) {
        Modifier
            .fillMaxSize()
            .padding(8.dp)
            .border(3.2.dp, Brush.linearGradient(listOf(MetallicGold, BrightGold, DarkGold)), RoundedCornerShape(42.dp))
            .shadow(12.dp, RoundedCornerShape(42.dp))
            .clip(RoundedCornerShape(42.dp))
            .background(Color.Transparent)
    } else {
        Modifier.fillMaxSize()
    }

    Box(modifier = Modifier.fillMaxSize().background(SlateBg)) {
        // Dynamic Slow Flow Waves Background
        WaveBackground(waveColor = MaterialTheme.colorScheme.primary)

        Column(modifier = frameModifier) {
            // Android Status Bar Simulation if golden frame is active
            if (simulatedGoldenFrame) {
                SimulatedStatusBar(isServiceActive = viewModel.isServiceRunning.collectAsState().value)
            }

            // Interactive Dynamic App Header
            AppHeader(
                isServiceRunning = viewModel.isServiceRunning.collectAsState().value,
                viewModel = viewModel,
                onTabSelected = { currentTab = it }
            )

            // Dynamic Main Screen Switcher
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                val treeDashboardPath = viewModel.showTreeDocDashboardPath.collectAsState().value
                if (treeDashboardPath != null) {
                    TreeDocDashboardScreen(viewModel = viewModel)
                } else if (showAIPromptHub) {
                    AIPromptHubScreen(onNavigateBack = { showAIPromptHub = false })
                } else if (showQuickActionsHub) {
                    QuickActionsHubScreen(onNavigateBack = { showQuickActionsHub = false })
                } else if (showHelpCenter) {
                    HelpCenterScreen(onNavigateBack = { showHelpCenter = false })
                } else if (showSourceExport) {
                    SourceExportScreen(onNavigateBack = { showSourceExport = false })
                } else if (showStyleBank) {
                    StyleBankScreen(onNavigateBack = { showStyleBank = false })
                } else {
                    when (currentTab) {
                        MainTab.MONITOR -> MonitorScreen(viewModel)
                        MainTab.STORYTELLER -> StorytellerScreen(viewModel)
                        MainTab.TREEDOC -> TreeDocScreen(viewModel)
                        MainTab.EXECUTOR -> ExecutorDashboardScreen(viewModel)
                        MainTab.GEMINI -> GeminiScreen(viewModel)
                        MainTab.LINKS -> LinkAutomatorScreen()
                        MainTab.PROJECTS -> ProjectsScreen(viewModel)
                        MainTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            goldenFrameEnabled = simulatedGoldenFrame,
                            onToggleGoldenFrame = onToggleSimulatedFrame,
                            onNavigateToAIPromptHub = { showAIPromptHub = true },
                            onNavigateToQuickActionsHub = { showQuickActionsHub = true },
                            onNavigateToHelpCenter = { showHelpCenter = true },
                            onNavigateToSourceExport = { showSourceExport = true },
                            onNavigateToStyleBank = { showStyleBank = true }
                        )
                    }
                }
            }

            // Bottom Navigation bar
            AppBottomNavigation(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }

        // Floating Interactive Golden Bubble (in-app version for consistent reliable Web preview streaming)
        var showBubbleDialog by remember { mutableStateOf(false) }
        var isReportingBubble by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 90.dp, end = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(
                        Brush.radialGradient(listOf(BrightGold, MetallicGold, DarkGold)),
                        CircleShape
                    )
                    .clickable {
                        showBubbleDialog = true
                    }
                    .testTag("floating_bubble"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Quick Report Bubble",
                    tint = SlateBg,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        if (showBubbleDialog) {
            Dialog(
                onDismissRequest = { showBubbleDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(16.dp)
                        .border(1.dp, MetallicGold, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = SlateBg
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡", fontSize = 18.sp)
                                Text(
                                    "الفقاعة الذهبية - تحكم سريع",
                                    color = BrightGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            IconButton(onClick = { showBubbleDialog = false }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "اغلاق", tint = TextSilver)
                            }
                        }

                        Divider(color = GlassBorder)

                        var quickCommandText by remember { mutableStateOf("") }
                        var isBubbleExecuting by remember { mutableStateOf(false) }
                        var bubbleStatusMsg by remember { mutableStateOf("") }

                        Text(
                            "أدخل أوامر التوليد السريعة أو كتل `@builder` أو موجهات الذكاء لتعديل الملفات والتحسين التلقائي:",
                            color = TextSilver,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        OutlinedTextField(
                            value = quickCommandText,
                            onValueChange = { quickCommandText = it },
                            placeholder = { Text("أدخل أمرًا سريعًا أو تلقين للـ AI... ⚡", color = TextMuted, fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .testTag("quick_command_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = GlassBorder,
                                cursorColor = MetallicGold
                            ),
                            textStyle = TextStyle(color = TextSilver, fontSize = 11.sp)
                        )

                        if (bubbleStatusMsg.isNotBlank()) {
                            Text(
                                bubbleStatusMsg,
                                color = BrightGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isBubbleExecuting) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = MetallicGold, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("جاري التنفيذ والمزامنة على القرص...", color = TextSilver, fontSize = 11.sp)
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Gemini Execute
                            Button(
                                onClick = {
                                    if (quickCommandText.trim().isEmpty()) {
                                        Toast.makeText(context, "الرجاء كتابة تلقين أو أمر سريع أولاً!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isBubbleExecuting = true
                                    bubbleStatusMsg = "جاري استشارة المساعد الذكي وتوليد الاستجابة..."
                                    scope.launch {
                                        try {
                                            val geminiRes = com.example.service.GeminiService(context).generateContent(quickCommandText)
                                            if (geminiRes.isSuccess) {
                                                val content = geminiRes.getOrThrow()
                                                bubbleStatusMsg = "تم توليد الاستجابة بنجاح. جاري شحنها وتطبيقها على المشروع الحسابي..."
                                                viewModel.runManualProcess(content) { outMsg ->
                                                    isBubbleExecuting = false
                                                    bubbleStatusMsg = ""
                                                    quickCommandText = ""
                                                    Toast.makeText(context, "🎉 تم بنجاح معالجة وتطبيق المخرج الذكي بالتزامن!", Toast.LENGTH_LONG).show()
                                                    showBubbleDialog = false
                                                }
                                            } else {
                                                isBubbleExecuting = false
                                                bubbleStatusMsg = ""
                                                Toast.makeText(context, "❌ فشل عمل محرك Gemini: ${geminiRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            isBubbleExecuting = false
                                            bubbleStatusMsg = ""
                                            Toast.makeText(context, "❌ خطأ أثناء الاتصال: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(38.dp).testTag("gemini_quick_exec_btn"),
                                enabled = !isBubbleExecuting
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = SlateBg, modifier = Modifier.size(14.dp))
                                    Text("معالجة بذكاء بـ Gemini 🧠", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Local Execution
                                Button(
                                    onClick = {
                                        if (quickCommandText.trim().isEmpty()) {
                                            Toast.makeText(context, "الرجاء كتابة كتل @builder هنا!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isBubbleExecuting = true
                                        bubbleStatusMsg = "جاري الحفظ والتطبيق محلياً..."
                                        viewModel.runManualProcess(quickCommandText) { outMsg ->
                                            isBubbleExecuting = false
                                            bubbleStatusMsg = ""
                                            quickCommandText = ""
                                            Toast.makeText(context, "⚡ تم التنفيذ الفوري للملف بنجاح!", Toast.LENGTH_LONG).show()
                                            showBubbleDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = MetallicGold),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp).border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                                    enabled = !isBubbleExecuting
                                ) {
                                    Text("أمر محلي مباشر ⚡", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                // Tree report
                                Button(
                                    onClick = {
                                        isBubbleExecuting = true
                                        bubbleStatusMsg = "جاري استخراج شجرة المشروع للحافظة..."
                                        viewModel.generateTreeReport(".", "txt", true) { msg ->
                                            isBubbleExecuting = false
                                            bubbleStatusMsg = ""
                                            Toast.makeText(context, "📋 تم نسخ تلخيص الشجرة بالكامل للحافظة!", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(38.dp).border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                                    enabled = !isBubbleExecuting
                                ) {
                                    Text("تقرير الشجرة 📊", fontSize = 11.sp)
                                }
                            }

                            // Chat Link Automator Shortcut
                            Button(
                                onClick = {
                                    currentTab = MainTab.LINKS
                                    showBubbleDialog = false
                                    Toast.makeText(context, "🔗 تم الانتقال إلى مؤتمت الروابط الذكي!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(38.dp).border(1.dp, MetallicGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                enabled = !isBubbleExecuting
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = BrightGold, modifier = Modifier.size(14.dp))
                                    Text("مُؤتمت الروابط 🔗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Explanation dialogue for permissions
        PermissionsDashboardDialog(
            isOpen = showPermissionsDashboard,
            onDismiss = { showPermissionsDashboard = false },
            viewModel = viewModel
        )

        if (showPermissionDialog) {
            Dialog(
                onDismissRequest = { showPermissionDialog = false },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(CardSlateBg, RoundedCornerShape(28.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(GoldGlassBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(28.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(18.dp))
                        
                        Text(
                            text = "طلب صلاحية الإشعارات",
                            color = TextSilver,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "يطلب المساعد الذكي الذهبي تفعيل الإشعارات لتتمكن من إظهار حالة المحاكي قيد التشغيل في الخلفية ومراقبة وتحديث الملفات المكتوبة فوراً.",
                            color = TextGray,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { showPermissionDialog = false }) {
                                Text("تخطي", color = TextGray)
                            }
                            
                            Button(
                                onClick = {
                                    showPermissionDialog = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("السماح بالوصول", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (showBubbleTutorial) {
            Dialog(
                onDismissRequest = {
                    showBubbleTutorial = false
                    sharedPrefs.edit().putBoolean("has_seen_bubble_tutorial", true).apply()
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(CardSlateBg, RoundedCornerShape(26.dp))
                        .border(1.5.dp, Brush.linearGradient(listOf(MetallicGold, BrightGold)), RoundedCornerShape(26.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "👑 الفقاعة الذهبية V2",
                            color = BrightGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Text(
                            text = "مرحباً بك في الفقاعة الذهبية V2، رفيقك الذكي الدائم:\n\n• يمكنك سحب الفقاعة وتكبيرها/تصغيرها بأي مكان على الشاشة.\n• تقوم بنسخ وقراءة الحافظة وتوليد الملفات آلياً فوق أي تطبيق دون الحاجة للكيبورد.\n• يتيح لك الزر إمكانية تبديل لوحة المفاتيح بسرعة وبدون أي تعقيد بضغطة واحدة.",
                            color = TextSilver,
                            fontSize = 13.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Right
                        )
                        
                        Button(
                            onClick = {
                                showBubbleTutorial = false
                                sharedPrefs.edit().putBoolean("has_seen_bubble_tutorial", true).apply()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrightGold,
                                contentColor = SlateBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Text(
                                text = "فهمت، ابدأ الآن!",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extension to avoid styling shadow helper error compiler references
fun Modifier.shadowGradient(color: Color = Color.Black, size: Dp = 10.dp): Modifier = this

// =====================================================================
// 1. Simulated Android Phone Elements (Frosted Glass and Mock Status Bar)
// =====================================================================

@Composable
fun SimulatedStatusBar(isServiceActive: Boolean) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var timeString by remember { mutableStateOf(formatter.format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            timeString = formatter.format(Date())
            kotlinx.coroutines.delay(10000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeString,
            color = TextSilver,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        // Rounded Speaker Mock
        Box(
            modifier = Modifier
                .width(55.dp)
                .height(6.dp)
                .background(Color(0xFF2E3033), RoundedCornerShape(3.dp))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Service Status Dot Glow
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(if (isServiceActive) EmeraldGlow else TextMuted, CircleShape)
            )

            // Simulated Battery
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .border(1.dp, TextSilver.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    .padding(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(13.dp)
                        .height(6.dp)
                        .background(if (isServiceActive) EmeraldGlow else TextSilver, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@Composable
fun AppHeader(
    isServiceRunning: Boolean,
    viewModel: MainViewModel,
    onTabSelected: (MainTab) -> Unit
) {
    var showDashboard by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "المساعد الذكي الذهبي",
                color = TextSilver,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Golden Smart Assistant",
                color = MetallicGold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stats dashboard button
            IconButton(
                onClick = { showDashboard = true },
                modifier = Modifier
                    .size(38.dp)
                    .background(GlassWhite, CircleShape)
                    .border(1.dp, GlassBorder, CircleShape)
                    .testTag("open_status_dashboard_btn")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Box(modifier = Modifier.width(3.dp).height(10.dp).background(MetallicGold, RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(3.dp).height(16.dp).background(BrightGold, RoundedCornerShape(1.dp)))
                    Box(modifier = Modifier.width(3.dp).height(12.dp).background(MetallicGold, RoundedCornerShape(1.dp)))
                }
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(GlassWhite, CircleShape)
                    .border(1.dp, GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isServiceRunning) EmeraldGlow else DangerRed, CircleShape)
                )
            }
        }
    }

    if (showDashboard) {
        StatusDashboardDialog(
            viewModel = viewModel,
            onDismiss = { showDashboard = false },
            onNavigateToTab = onTabSelected
        )
    }
}

@Composable
fun AppBottomNavigation(
    currentTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardSlateBg)
            .border(BorderStroke(0.6.dp, GlassBorder))
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MainTab.values().forEach { tab ->
            val isSelected = currentTab == tab
            val tintColor = if (isSelected) MetallicGold else TextGray.copy(alpha = 0.6f)
            val backgroundAccent = if (isSelected) GoldGlassBg else Color.Transparent

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundAccent)
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("tab_${tab.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = getTabIcon(tab),
                    contentDescription = tab.rtlTitle,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tab.rtlTitle,
                    color = tintColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun getTabIcon(tab: MainTab): ImageVector {
    return when (tab) {
        MainTab.MONITOR -> Icons.Default.Home
        MainTab.STORYTELLER -> Icons.Default.Share
        MainTab.TREEDOC -> Icons.Default.List
        MainTab.EXECUTOR -> Icons.Default.PlayArrow
        MainTab.GEMINI -> Icons.Default.Star
        MainTab.LINKS -> Icons.Default.Share
        MainTab.PROJECTS -> Icons.Default.Menu
        MainTab.SETTINGS -> Icons.Default.Settings
    }
}

// =====================================================================
// 2. MONITOR TAB - Clipboard Monitoring list, Text processing, Files & Logs Tables
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitorScreen(viewModel: MainViewModel) {
    var manualText by remember { mutableStateOf("") }
    var operationResultMsg by remember { mutableStateOf("") }
    var showResultDialog by remember { mutableStateOf(false) }
    var isProcessingManual by remember { mutableStateOf(false) }

    // File Editor State
    var editingFileEntity by remember { mutableStateOf<FileEntity?>(null) }
    var editingFileContent by remember { mutableStateOf("") }
    var showFileEditorDialog by remember { mutableStateOf(false) }

    val isServiceRunning = viewModel.isServiceRunning.collectAsState().value
    val isServicePaused = viewModel.isServicePaused.collectAsState().value
    val createdFiles = viewModel.createdFiles.collectAsState(initial = emptyList()).value
    val eventLogs = viewModel.eventLogs.collectAsState(initial = emptyList()).value

    // New Event Logs Premium Dashboard States
    var selectedLimit by remember { mutableStateOf(20) } 
    var selectedTypeFilter by remember { mutableStateOf("الكل") } 
    var selectedSeverityFilter by remember { mutableStateOf("الكل") } 
    var sortNewestFirst by remember { mutableStateOf(true) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedLogIds by remember { mutableStateOf(emptySet<Int>()) }
    val editedLogsMap = remember { mutableStateMapOf<Int, String>() }
    var editingLogForDetails by remember { mutableStateOf<LogEntity?>(null) }
    var editingLogTextState by remember { mutableStateOf("") }

    // Advanced Event Logs Premium States (Enhanced Filtering & Formats)
    var selectedDurationFilter by remember { mutableStateOf("الكل") } // "الكل", "آخر ساعة", "آخر 24 ساعة", "آخر 7 أيام"
    var selectedSourceFilter by remember { mutableStateOf("الكل") } // "الكل", "منظم المجلد", "محرك الأوامر", "الذكاء الاصطناعي", "النظام", "الحافظة"
    var searchLogQuery by remember { mutableStateOf("") }
    
    // Export Option States
    var exportIncludeDetails by remember { mutableStateOf(true) }
    var exportAnonymizeSensitive by remember { mutableStateOf(false) }
    var exportHtmlTheme by remember { mutableStateOf("dark") } // "dark", "light", "gold"
    var exportToSettingsDir by remember { mutableStateOf(true) } // true: settings path, false: external/default
    var exportTxtStyle by remember { mutableStateOf("detailed") } // "detailed", "simple", "markdown"
    var exportCsvDelimiter by remember { mutableStateOf(",") } // ",", ";", "tab"
    var exportJsonIndent by remember { mutableStateOf(4) } // 2, 4, 0 (compressed)

    // --- Smart Clipboard & AI Task Hub States ---
    var activeHubTab by remember { mutableStateOf("clipboard") }
    var clipboardSearch by remember { mutableStateOf("") }
    var clipboardFilterType by remember { mutableStateOf("ALL") }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskType by remember { mutableStateOf("تحليل ذكي") }
    var newTaskCommand by remember { mutableStateOf("") }
    var selectedTaskDetail by remember { mutableStateOf<AiTaskRecord?>(null) }
    var editingClipboardLog by remember { mutableStateOf<LogEntity?>(null) }
    var editingClipboardText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val smartPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
    var minLogChars by remember { mutableStateOf(smartPrefs.getInt("clipboard_min_log_chars", 8)) }
    var autoProcessQueue by remember { mutableStateOf(smartPrefs.getBoolean("auto_process_ai_queue", true)) }
    var notifyOnComplete by remember { mutableStateOf(smartPrefs.getBoolean("notify_on_task_completion", true)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Status Widget
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (isServiceRunning) GoldGlassBg else GlassWhite, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isServiceRunning) MetallicGold else TextGray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "مراقبة الحافظة",
                                color = TextSilver,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isServiceRunning) {
                                    if (isServicePaused) "المراقبة متوقفة مؤقتاً ⏸️" else "نشط ومتحفز للتوجيهات @"
                                } else "المراقبة متوقفة في الوقت الحالي",
                                color = if (isServiceRunning) {
                                    if (isServicePaused) Color(0xFFFF9800) else EmeraldGlow
                                } else TextGray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isServiceRunning) {
                            TextButton(
                                onClick = {
                                    viewModel.toggleServicePause()
                                    if (isServicePaused) {
                                        Toast.makeText(context, "تم استئناف المراقبة بنجاح", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "تم إيقاف المراقبة مؤقتاً", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (isServicePaused) EmeraldGlow else Color(0xFFFF9800)
                                ),
                                modifier = Modifier.testTag("pause_resume_button")
                            ) {
                                Text(
                                    text = if (isServicePaused) "استئناف" else "إيقاف مؤقت",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Switch(
                            checked = isServiceRunning,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    viewModel.startMonitorService()
                                    Toast.makeText(context, "تم تفعيل مراقبة الحافظة في الخلفية", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.stopMonitorService()
                                    Toast.makeText(context, "تم إيقاف خدمة مراقبة الحافظة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            ),
                            modifier = Modifier.testTag("service_switch")
                        )
                    }
                }
            }
        }

        // Direct Manual Directives Paste Box
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "منفذ التوجيهات اليدوي",
                            color = MetallicGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    manualText = clip.getItemAt(0).text?.toString() ?: ""
                                    Toast.makeText(context, "تم لصق محتويات الحافظة!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "الحافظة فارغة حالياً.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Paste Clipboard", tint = TextGray, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        placeholder = {
                            Text(
                                text = "الصق التوجيه هنا...\nمثال:\n// @builder:file code.py\nprint('Hello World')\n// @builder:end",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("manual_input_field"),
                        textStyle = TextStyle(color = TextSilver, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold.copy(alpha = 0.5f),
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassBlack,
                            unfocusedContainerColor = GlassBlack
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (manualText.isBlank()) {
                                Toast.makeText(context, "الرجاء إدخال نص التوجيه أولاً", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isProcessingManual = true
                            viewModel.runManualProcess(manualText) { res ->
                                isProcessingManual = false
                                operationResultMsg = res
                                showResultDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("process_directives_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isProcessingManual) {
                            CircularProgressIndicator(color = SlateBg, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("معالجة النص والتعليمات", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    var showFileOrFolderMenu by remember { mutableStateOf(false) }
                    var selectedFileOrFolder by remember { mutableStateOf<File?>(null) }

                    OutlinedButton(
                        onClick = {
                            com.example.service.CustomFileExplorerDialog.showForProcessing(context) { file ->
                                selectedFileOrFolder = file
                                showFileOrFolderMenu = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("process_file_or_folder_button"),
                        border = BorderStroke(1.dp, MetallicGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MetallicGold),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(20.dp))
                            Text("📂 معالجة ملف/مجلد", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (showFileOrFolderMenu && selectedFileOrFolder != null) {
                        val file = selectedFileOrFolder!!
                        AlertDialog(
                            onDismissRequest = { showFileOrFolderMenu = false },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { showFileOrFolderMenu = false }) {
                                    Text("إلغاء", color = MetallicGold)
                                }
                            },
                            title = {
                                Text(
                                    text = if (file.isDirectory) "📁 مجلد: ${file.name}" else "📄 ملف: ${file.name}",
                                    color = MetallicGold,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "اختر الإجراء الذي ترغب في تطبيقه على ${if (file.isDirectory) "المجلد" else "الملف"}:",
                                        color = TextSilver,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (file.isDirectory) {
                                        Button(
                                            onClick = {
                                                showFileOrFolderMenu = false
                                                scope.launch {
                                                    val result = com.example.engine.UniversalActionHandler.handleAction(
                                                        context,
                                                        "execute_commands",
                                                        "@treedoc --format=html"
                                                    )
                                                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg)
                                        ) {
                                            Text("📊 إنشاء تقرير شجري", color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                showFileOrFolderMenu = false
                                                scope.launch {
                                                    try {
                                                        val wrapped = com.example.engine.BuildPackExporter.wrapDirectory(context, file)
                                                        if (wrapped.isNotEmpty()) {
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Build Pack Folder", wrapped))
                                                            Toast.makeText(context, "📦 تم تجميع المجلد كحزمة بناء ونسخه للحافظة!", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            Toast.makeText(context, "⚠️ المجلد فارغ أو لا يحتوي على ملفات صالحة للتجميع", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "⚠️ خطأ أثناء تجميع المجلد: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg)
                                        ) {
                                            Text("📦 حزمة بناء للمجلد", color = Color.White)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                showFileOrFolderMenu = false
                                                scope.launch {
                                                    try {
                                                        val content = file.readText(Charsets.UTF_8)
                                                        com.example.engine.LargeTextProcessor.processLargeText(context, content, "smart_capture")
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "⚠️ خطأ في قراءة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg)
                                        ) {
                                            Text("🧠 تحليل ذكي", color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                showFileOrFolderMenu = false
                                                scope.launch {
                                                    try {
                                                        val content = file.readText(Charsets.UTF_8)
                                                        com.example.engine.LargeTextProcessor.processLargeText(context, content, "build_pack")
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "⚠️ خطأ في قراءة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg)
                                        ) {
                                            Text("📦 حزمة بناء", color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                showFileOrFolderMenu = false
                                                scope.launch {
                                                    try {
                                                        val content = file.readText(Charsets.UTF_8)
                                                        com.example.engine.LargeTextProcessor.processLargeText(context, content, "convert_beautify")
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "⚠️ خطأ في قراءة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg)
                                        ) {
                                            Text("🎨 تحويل وتجميل", color = Color.White)
                                        }
                                    }
                                }
                            },
                            containerColor = SlateBg,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }

        // Local File Browser and Processor Widget
        item {
            LocalFileBrowserWidget(
                viewModel = viewModel,
                onLoadToEditor = { text ->
                    manualText = text
                }
            )
        }

        // Created Files Grid List
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "آخر الملفات المكتوبة",
                        color = TextSilver,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (createdFiles.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearCreatedFilesList() }) {
                            Text("تصفير القائمة", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (createdFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(GlassWhite, RoundedCornerShape(20.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا توجد ملفات منشأة في السجلات حالياً.", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 2
                    ) {
                        val limitValues = createdFiles.take(8)
                        limitValues.forEach { fileEntity ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .background(CardSlateBg, RoundedCornerShape(18.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                                    .clickable {
                                        // Open inline File Editor Dialogue!
                                        val realFile = File(fileEntity.fullPath)
                                        if (realFile.exists()) {
                                            try {
                                                editingFileContent = realFile.readText()
                                                editingFileEntity = fileEntity
                                                showFileEditorDialog = true
                                            } catch (e: Exception) {
                                                Toast
                                                    .makeText(context, "فشل قراءة الملف: ${e.message}", Toast.LENGTH_SHORT)
                                                    .show()
                                            }
                                        } else {
                                            Toast
                                                .makeText(context, "الملف لم يعد متواجداً في المسار الحقيقي.", Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(GoldGlassBg, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Build, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(15.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = fileEntity.path,
                                        color = TextSilver,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${fileEntity.size} Bytes • ${if(fileEntity.mode == "w") "كتابة" else "إلحاق"}",
                                        color = TextGray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Smart Clipboard & AI Task Hub Widget ---
        item {
            val smartPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
            
            val clipboardLogs = remember(eventLogs) {
                eventLogs.filter { it.type == "clipboard_history" }
            }
            
            val aiTasksQueueState = viewModel.aiTasksQueue.collectAsState().value

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .background(GlassWhite, RoundedCornerShape(24.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                // Header of the Hub
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GoldGlassBg, CircleShape)
                                .border(1.dp, MetallicGold.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MetallicGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "لوحة الحافظة والمهام الذكية",
                                color = TextSilver,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مراقبة الحافظة، إدارة طابور المهام وتصنيفاتها المعززة",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(GoldGlassBg, RoundedCornerShape(8.dp))
                            .border(1.dp, MetallicGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "مساعد مفعّل",
                            color = MetallicGold,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab buttons (Horizontal Row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardSlateBg.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "clipboard" to "📋 الحافظة (${clipboardLogs.size})",
                        "tasks" to "⚡ المهام (${aiTasksQueueState.size})",
                        "settings" to "⚙️ تخصيص"
                    )

                    tabs.forEach { (tabId, label) ->
                        val isSelected = activeHubTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (isSelected) MetallicGold else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { activeHubTab = tabId }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) SlateBg else TextSilver,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- TAB 1: CLIPBOARD HISTORY ---
                if (activeHubTab == "clipboard") {
                    OutlinedTextField(
                        value = clipboardSearch,
                        onValueChange = { clipboardSearch = it },
                        placeholder = { Text("بحث في الحافظة...", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextSilver,
                            unfocusedTextColor = TextSilver,
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = CardSlateBg.copy(alpha = 0.3f),
                            unfocusedContainerColor = CardSlateBg.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(fontSize = 11.sp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = TextGray,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        trailingIcon = {
                            if (clipboardSearch.isNotEmpty()) {
                                IconButton(onClick = { clipboardSearch = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = null,
                                        tint = TextGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filterChips = listOf(
                            "ALL" to "الكل",
                            "DIRECTIVE" to "توجيهات 🚀",
                            "URL" to "روابط 🌐",
                            "CODE" to "أكواد برمجية 💻",
                            "TEXT" to "نصوص 📝"
                        )

                        filterChips.forEach { (typeKey, label) ->
                            val isSelected = clipboardFilterType == typeKey
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) GoldGlassBg else CardSlateBg.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MetallicGold else GlassBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { clipboardFilterType = typeKey }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) MetallicGold else TextGray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val filteredClipboard = remember(clipboardLogs, clipboardSearch, clipboardFilterType) {
                        clipboardLogs.filter { log ->
                            val matchesSearch = log.details?.contains(clipboardSearch, ignoreCase = true) == true || 
                                                log.message.contains(clipboardSearch, ignoreCase = true)
                            
                            val details = log.details ?: ""
                            val computedType = when {
                                details.startsWith("@builder") || details.startsWith("@executor") || details.startsWith("@treedoc") -> "DIRECTIVE"
                                details.contains("http://") || details.contains("https://") -> "URL"
                                details.contains("class ") || details.contains("def ") || details.contains("fun ") || details.contains("import ") || details.contains("{") -> "CODE"
                                else -> "TEXT"
                            }
                            
                            val matchesType = clipboardFilterType == "ALL" || clipboardFilterType == computedType
                            matchesSearch && matchesType
                        }
                    }

                    if (filteredClipboard.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(CardSlateBg.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "سجل الحافظة فارغ أو لم يتم العثور على نتائج تطابق معايير التصفية.",
                                color = TextMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            filteredClipboard.take(6).forEach { log ->
                                val text = log.details ?: ""
                                
                                val (itemIcon, iconColor, categoryLabel) = when {
                                    text.startsWith("@builder") || text.startsWith("@executor") || text.startsWith("@treedoc") -> 
                                        Triple(Icons.Default.Star, Color(0xFF60A5FA), "توجيه ذكي")
                                    text.contains("http://") || text.contains("https://") -> 
                                        Triple(Icons.Default.Send, Color(0xFFFBBF24), "رابط ويب")
                                    text.contains("class ") || text.contains("def ") || text.contains("fun ") || text.contains("import ") || text.contains("{") -> 
                                        Triple(Icons.Default.Create, Color(0xFFA3E635), "كود برمجبي")
                                    else -> 
                                        Triple(Icons.Default.List, Color(0xFFA78BFA), "نص عام")
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardSlateBg.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .border(1.dp, GlassBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            editingClipboardLog = log
                                            editingClipboardText = text
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(iconColor.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = itemIcon,
                                            contentDescription = null,
                                            tint = iconColor,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = categoryLabel,
                                                color = iconColor,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                                                color = TextMuted,
                                                fontSize = 8.sp
                                            )
                                        }
                                        Text(
                                            text = text,
                                            color = TextSilver,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                viewModel.runManualProcess(text) { result ->
                                                    Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                                                }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "نفذ كتوجيه",
                                                tint = EmeraldGlow,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Copied History", text))
                                                Toast.makeText(context, "📋 تم نسخ النص للحافظة ثانيةً!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "نسخ",
                                                tint = MetallicGold,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.deleteLogById(log.id)
                                                Toast.makeText(context, "🗑️ تم الحذف من السجل", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "حذف",
                                                tint = DangerRed,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            TextButton(
                                onClick = {
                                    viewModel.deleteLogsByType("clipboard_history")
                                    Toast.makeText(context, "🗑️ تم إفراغ سجل الحافظة بنجاح", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = "إفراغ سجل الحافظة بالكامل",
                                    color = DangerRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // --- TAB 2: AI TASK QUEUE ---
                if (activeHubTab == "tasks") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val activeCount = aiTasksQueueState.count { it.status == "RUNNING" || it.status == "PENDING" }
                            Text(
                                text = "طابور الأتمتة: ${aiTasksQueueState.size} مهام",
                                color = TextSilver,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "مهام معلقة/نشطة حالياً: $activeCount",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { showAddTaskDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = SlateBg, modifier = Modifier.size(12.dp))
                                    Text("مهمة ذكية", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = { viewModel.clearCompletedAiTasks() },
                                colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("تنظيف", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (aiTasksQueueState.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(CardSlateBg.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "طابور المهام فارغ. انقر على 'مهمة ذكية' لإضافة وتجربة طابور التشغيل.",
                                color = TextMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            aiTasksQueueState.forEach { task ->
                                val (statusText, statusColor, borderClr) = when (task.status) {
                                    "PENDING" -> Triple("معلقة بالصف", TextGray, GlassBorder)
                                    "RUNNING" -> Triple("جاري التنفيذ...", MetallicGold, MetallicGold.copy(alpha = 0.6f))
                                    "SUCCESS" -> Triple("اكتملت بنجاح", EmeraldGlow, EmeraldGlow.copy(alpha = 0.5f))
                                    "FAILED" -> Triple("فشلت المهمة", DangerRed, DangerRed.copy(alpha = 0.5f))
                                    else -> Triple("مجهول", TextGray, GlassBorder)
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardSlateBg.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                        .border(1.dp, borderClr, RoundedCornerShape(14.dp))
                                        .clickable { selectedTaskDetail = task }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = task.type,
                                                    color = statusColor,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = task.title,
                                                color = TextSilver,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = statusText,
                                                color = statusColor,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            if (task.status == "FAILED") {
                                                IconButton(
                                                    onClick = { viewModel.runAiTask(task.id) },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = MetallicGold, modifier = Modifier.size(12.dp))
                                                }
                                            } else if (task.status == "PENDING") {
                                                IconButton(
                                                    onClick = { viewModel.runAiTask(task.id) },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = EmeraldGlow, modifier = Modifier.size(12.dp))
                                                }
                                            }

                                            IconButton(
                                                onClick = { viewModel.deleteAiTask(task.id) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }

                                    if (task.status == "RUNNING") {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = task.progress,
                                            color = MetallicGold,
                                            trackColor = CardSlateBg,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                        )
                                    }

                                    if (task.logs.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = task.logs,
                                            color = TextGray,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- TAB 3: CUSTOMIZATION ---
                if (activeHubTab == "settings") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "تخصيص الحافظة والمهام الذكية",
                            color = MetallicGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "الحد الأدنى لطول الحفظ: $minLogChars حرفاً",
                                    color = TextSilver,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "تجاهل النصوص المنسوخة بالغة الصغر لمنع الاكتظاظ",
                                    color = TextMuted,
                                    fontSize = 8.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(onClick = { 
                                    if (minLogChars > 1) {
                                        minLogChars -= 1
                                        smartPrefs.edit().putInt("clipboard_min_log_chars", minLogChars).apply()
                                    }
                                }) {
                                    Text("-", color = MetallicGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { 
                                    if (minLogChars < 50) {
                                        minLogChars += 1
                                        smartPrefs.edit().putInt("clipboard_min_log_chars", minLogChars).apply()
                                    }
                                }) {
                                    Text("+", color = MetallicGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Divider(color = GlassBorder.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "التشغيل التلقائي للمهام",
                                    color = TextSilver,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "معالجة المهام الجديدة المضافة فوراً بدون انتظار نقرة",
                                    color = TextMuted,
                                    fontSize = 8.sp
                                )
                            }
                            Switch(
                                checked = autoProcessQueue,
                                onCheckedChange = {
                                    autoProcessQueue = it
                                    smartPrefs.edit().putBoolean("auto_process_ai_queue", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SlateBg,
                                    checkedTrackColor = MetallicGold,
                                    uncheckedThumbColor = TextGray,
                                    uncheckedTrackColor = CardSlateBg
                                )
                            )
                        }

                        Divider(color = GlassBorder.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "تنبيه عند انتهاء المهمة",
                                    color = TextSilver,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "إظهار إشعار Toast عند اكتمال أو فشل معالجة المهام",
                                    color = TextMuted,
                                    fontSize = 8.sp
                                )
                            }
                            Switch(
                                checked = notifyOnComplete,
                                onCheckedChange = {
                                    notifyOnComplete = it
                                    smartPrefs.edit().putBoolean("notify_on_task_completion", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SlateBg,
                                    checkedTrackColor = MetallicGold,
                                    uncheckedThumbColor = TextGray,
                                    uncheckedTrackColor = CardSlateBg
                                )
                            )
                        }
                    }
                }
            }
        }

        // --- Dialogs supporting the Hub ---
        item {
            if (showAddTaskDialog) {
                Dialog(onDismissRequest = { showAddTaskDialog = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateBg, RoundedCornerShape(24.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "إضافة مهمة أتمتة ذكية",
                                color = MetallicGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = newTaskTitle,
                                onValueChange = { newTaskTitle = it },
                                label = { Text("عنوان المهمة", color = TextGray) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextSilver,
                                    unfocusedTextColor = TextSilver,
                                    focusedBorderColor = MetallicGold,
                                    unfocusedBorderColor = GlassBorder
                                )
                            )

                            Text("نوع المهمة:", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val types = listOf("تحليل ذكي", "كتابة كود", "أتمتة أوامر", "فحص كشوفات")
                                types.forEach { type ->
                                    val isSelected = newTaskType == type
                                    Box(
                                        modifier = Modifier
                                            .background(if (isSelected) GoldGlassBg else CardSlateBg, RoundedCornerShape(8.dp))
                                            .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(8.dp))
                                            .clickable { newTaskType = type }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(type, color = if (isSelected) MetallicGold else TextGray, fontSize = 10.sp)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = newTaskCommand,
                                onValueChange = { newTaskCommand = it },
                                label = { Text("الأمر البرمجي (اختياري)", color = TextGray) },
                                placeholder = { Text("مثال: @treedoc", color = TextMuted) },
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextSilver,
                                    unfocusedTextColor = TextSilver,
                                    focusedBorderColor = MetallicGold,
                                    unfocusedBorderColor = GlassBorder
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (newTaskTitle.isNotBlank()) {
                                            viewModel.addAiTask(newTaskTitle, newTaskType, newTaskCommand.takeIf { it.isNotBlank() })
                                            showAddTaskDialog = false
                                            newTaskTitle = ""
                                            newTaskCommand = ""
                                        } else {
                                            Toast.makeText(context, "يرجى كتابة عنوان للمهمة", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إضافة للطابور", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { showAddTaskDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إلغاء", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (selectedTaskDetail != null) {
                val task = selectedTaskDetail!!
                Dialog(onDismissRequest = { selectedTaskDetail = null }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateBg, RoundedCornerShape(24.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "سجل تفاصيل المهمة الذكية",
                                    color = MetallicGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .background(GoldGlassBg, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(task.status, color = MetallicGold, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text("العنوان: ${task.title}", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("النوع: ${task.type}", color = TextGray, fontSize = 10.sp)
                            if (!task.command.isNullOrBlank()) {
                                Text("الأمر المرفق: ${task.command}", color = TextGray, fontSize = 10.sp)
                            }

                            Divider(color = GlassBorder.copy(alpha = 0.3f))

                            Text("سجل المخرجات المباشر (Execution Log):", color = TextSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(CardSlateBg, RoundedCornerShape(12.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = task.logs.ifBlank { "لا توجد سجلات بعد للمهمة الحالية." },
                                    color = TextSilver,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Task Logs", task.logs))
                                        Toast.makeText(context, "📋 تم نسخ سجل المخرجات!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("نسخ السجل", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { selectedTaskDetail = null },
                                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("إغلاق", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (editingClipboardLog != null) {
                val log = editingClipboardLog!!
                Dialog(onDismissRequest = { editingClipboardLog = null }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateBg, RoundedCornerShape(24.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "تعديل نص الحافظة الملتقط",
                                color = MetallicGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = editingClipboardText,
                                onValueChange = { editingClipboardText = it },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextSilver,
                                    unfocusedTextColor = TextSilver,
                                    focusedBorderColor = MetallicGold,
                                    unfocusedBorderColor = GlassBorder
                                )
                            )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val updatedLog = log.copy(details = editingClipboardText)
                                viewModel.deleteLogById(log.id)
                                viewModel.updateLog(updatedLog)
                                editingClipboardLog = null
                                Toast.makeText(context, "✅ تم حفظ التعديلات بنجاح!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("حفظ التغييرات", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = { editingClipboardLog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

        // Live Event Logs List (سجل الأحداث)
        item {
            val smartPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
            var logViewMode by remember {
                mutableStateOf(smartPrefs.getString("log_view_mode", "technical") ?: "technical")
            }

            // Sync with SharedPreferences dynamically when the UI is shown
            LaunchedEffect(Unit) {
                logViewMode = smartPrefs.getString("log_view_mode", "technical") ?: "technical"
            }

            // Apply filtering and sorting dynamically
            val filteredLogs = remember(
                eventLogs, selectedLimit, selectedTypeFilter, selectedSeverityFilter, sortNewestFirst, editedLogsMap,
                selectedDurationFilter, selectedSourceFilter, searchLogQuery
            ) {
                var list = eventLogs.filter { log ->
                    val matchesType = when (selectedTypeFilter) {
                        "الكل" -> true
                        "الملفات" -> log.type == "builder" || log.type == "treedoc"
                        "الأوامر" -> log.type == "executor"
                        "الذكاء الاصطناعي" -> log.type == "gemini"
                        "النظام" -> log.type == "system" || log.type == "clipboard_service" || log.type == "bubble"
                        else -> true
                    }

                    val detail = editedLogsMap[log.id] ?: log.details ?: ""
                    val isFail = log.message.contains("❌") || log.message.contains("فشل") || detail.contains("❌") || detail.contains("فشل")
                    val matchesSeverity = when (selectedSeverityFilter) {
                        "الكل" -> true
                        "ناجح" -> !isFail
                        "فشل" -> isFail
                        else -> true
                    }

                    val currentTime = System.currentTimeMillis()
                    val matchesDuration = when (selectedDurationFilter) {
                        "الكل" -> true
                        "آخر ساعة" -> (currentTime - log.timestamp) <= 3600_000L
                        "آخر 24 ساعة" -> (currentTime - log.timestamp) <= 86400_000L
                        "آخر 7 أيام" -> (currentTime - log.timestamp) <= 7 * 86400_000L
                        else -> true
                    }

                    val matchesSource = when (selectedSourceFilter) {
                        "الكل" -> true
                        "🫧 الفقاعة الذهبية" -> log.source == "bubble"
                        "⌨️ لوحة المفاتيح IME" -> log.source == "ime"
                        "🟢 تلقائي" -> log.source == "auto"
                        "✍️ يدوي" -> log.source == "manual"
                        "📦 حزمة البناء" -> log.source == "buildpack"
                        "🧠 الالتقاط الذكي" -> log.source == "smartcapture"
                        else -> true
                    }

                    val matchesSearch = if (searchLogQuery.isBlank()) {
                        true
                    } else {
                        log.message.contains(searchLogQuery, ignoreCase = true) ||
                                detail.contains(searchLogQuery, ignoreCase = true)
                    }

                    matchesType && matchesSeverity && matchesDuration && matchesSource && matchesSearch
                }

                list = if (sortNewestFirst) {
                    list.sortedByDescending { it.timestamp }
                } else {
                    list.sortedBy { it.timestamp }
                }

                val limit = if (selectedLimit == 9999) 9999 else selectedLimit
                if (limit < 9999) {
                    list.take(limit)
                } else {
                    list
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlateBg.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(GoldGlassBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = null,
                                tint = MetallicGold,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "لوحة تحكم وتحليل السجلات",
                            color = TextSilver,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Quick mode cycle button
                        val modeLabel = when (logViewMode) {
                            "technical" -> "⚙️ تقني"
                            "developer" -> "💻 مطور"
                            "academic" -> "🎓 أكاديمي"
                            "user" -> "👥 مستخدم"
                            else -> "⚙️ تقني"
                        }
                        
                        Button(
                            onClick = {
                                val nextMode = when (logViewMode) {
                                    "technical" -> "developer"
                                    "developer" -> "academic"
                                    "academic" -> "user"
                                    "user" -> "technical"
                                    else -> "technical"
                                }
                                logViewMode = nextMode
                                smartPrefs.edit().putString("log_view_mode", nextMode).apply()
                                Toast.makeText(context, "الوضع النشط: $modeLabel", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetallicGold.copy(alpha = 0.15f),
                                contentColor = MetallicGold
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp).testTag("quick_log_mode_toggle")
                        ) {
                            Text(modeLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        if (eventLogs.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearDatabaseLogs() }) {
                                Text("تصفير السجل", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- PREMIUM EVENT LOGS ANALYSIS DASHBOARD ---
                val totalCount = eventLogs.size
                val failCount = eventLogs.count { log ->
                    val detail = editedLogsMap[log.id] ?: log.details ?: ""
                    log.message.contains("❌") || log.message.contains("فشل") || detail.contains("❌") || detail.contains("فشل")
                }
                val successCount = totalCount - failCount
                val healthPercentage = if (totalCount > 0) ((successCount.toFloat() / totalCount.toFloat()) * 100).toInt() else 100
                
                // Group by type to find most active source
                val typesList = eventLogs.map { it.type }
                val mostActiveType = if (typesList.isNotEmpty()) {
                    typesList.groupBy { it }.maxByOrNull { it.value.size }?.key ?: "system"
                } else "system"
                
                val mostActiveLabel = when (mostActiveType) {
                    "builder" -> "منظم المجلد"
                    "executor" -> "محرك الأوامر"
                    "treedoc" -> "مستكشف الملفات"
                    "gemini" -> "الذكاء الاصطناعي"
                    "clipboard_service", "clipboard_history" -> "الحافظة"
                    else -> "النظام"
                }

                // Color codes
                val builderColor = Color(0xFF60A5FA)
                val executorColor = Color(0xFFFBBF24)
                val geminiColor = Color(0xFFA78BFA)
                val clipboardColor = Color(0xFF34D399)
                val systemColor = Color(0xFF9CA3AF)

                // Sub-counts for visual progress bar
                val builderC = eventLogs.count { it.type == "builder" || it.type == "treedoc" }
                val executorC = eventLogs.count { it.type == "executor" }
                val geminiC = eventLogs.count { it.type == "gemini" }
                val clipboardC = eventLogs.count { it.type == "clipboard_service" || it.type == "clipboard_history" }
                val systemC = totalCount - builderC - executorC - geminiC - clipboardC

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .border(1.dp, GlassBorder.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    // Row 1: Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("السجلات", color = TextMuted, fontSize = 9.sp)
                            Text("$totalCount", color = TextSilver, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("معدل النجاح", color = TextMuted, fontSize = 9.sp)
                            Text("$healthPercentage%", color = if (healthPercentage >= 90) EmeraldGlow else DangerRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("الفشل", color = TextMuted, fontSize = 9.sp)
                            Text("$failCount", color = if (failCount > 0) DangerRed else TextSilver, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.3f)) {
                            Text("النشاط الأكبر", color = TextMuted, fontSize = 9.sp)
                            Text(mostActiveLabel, color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Segmented horizontal distribution bar
                    if (totalCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(CardSlateBg)
                        ) {
                            if (builderC > 0) {
                                Box(modifier = Modifier.weight(builderC.toFloat()).fillMaxHeight().background(builderColor))
                            }
                            if (executorC > 0) {
                                Box(modifier = Modifier.weight(executorC.toFloat()).fillMaxHeight().background(executorColor))
                            }
                            if (geminiC > 0) {
                                Box(modifier = Modifier.weight(geminiC.toFloat()).fillMaxHeight().background(geminiColor))
                            }
                            if (clipboardC > 0) {
                                Box(modifier = Modifier.weight(clipboardC.toFloat()).fillMaxHeight().background(clipboardColor))
                            }
                            if (systemC > 0) {
                                Box(modifier = Modifier.weight(systemC.toFloat()).fillMaxHeight().background(systemColor))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Legends with counts
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (builderC > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(builderColor, CircleShape))
                                    Text("مجلد ($builderC)", color = TextMuted, fontSize = 8.sp)
                                }
                            }
                            if (executorC > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(executorColor, CircleShape))
                                    Text("أوامر ($executorC)", color = TextMuted, fontSize = 8.sp)
                                }
                            }
                            if (geminiC > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(geminiColor, CircleShape))
                                    Text("ذكاء ($geminiC)", color = TextMuted, fontSize = 8.sp)
                                }
                            }
                            if (clipboardC > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(clipboardColor, CircleShape))
                                    Text("حافظة ($clipboardC)", color = TextMuted, fontSize = 8.sp)
                                }
                            }
                            if (systemC > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(systemColor, CircleShape))
                                    Text("نظام ($systemC)", color = TextMuted, fontSize = 8.sp)
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(CardSlateBg)
                        )
                    }

                    // Dynamic Warning Alert inside Dashboard
                    val lastHourFail = remember(eventLogs) {
                        eventLogs.any { log ->
                            val isRecent = (System.currentTimeMillis() - log.timestamp) <= 3600_000L
                            val detail = editedLogsMap[log.id] ?: log.details ?: ""
                            isRecent && (log.message.contains("❌") || log.message.contains("فشل") || detail.contains("❌") || detail.contains("فشل"))
                        }
                    }
                    if (lastHourFail) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x15EF4444), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(10.dp))
                            Text("تنبيه نشط: تم رصد فشل في العمليات خلال الساعة الأخيرة!", color = DangerRed, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Input Field
                OutlinedTextField(
                    value = searchLogQuery,
                    onValueChange = { searchLogQuery = it },
                    placeholder = { Text("ابحث في رسائل السجلات وتفاصيلها...", color = TextMuted, fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchLogQuery.isNotEmpty()) {
                            IconButton(onClick = { searchLogQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextSilver,
                        unfocusedTextColor = TextSilver,
                        focusedBorderColor = MetallicGold,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = CardSlateBg.copy(alpha = 0.4f),
                        unfocusedContainerColor = CardSlateBg.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Filters Pane in styled glass containers
                Text("تصفية وفلترة الأحداث:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                // 1. LIMIT & SEVERITY Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Limit Filter Buttons
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الحد الأقصى:", color = TextGray, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(10, 25, 50, 100, 9999).forEach { limit ->
                                val isSelected = selectedLimit == limit
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MetallicGold else GlassWhite)
                                        .clickable { selectedLimit = limit }
                                        .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (limit == 9999) "الكل" else limit.toString(),
                                        color = if (isSelected) SlateBg else TextSilver,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Severity Filter Buttons
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الحالة والأهمية:", color = TextGray, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("الكل", "ناجح", "فشل").forEach { severity ->
                                val isSelected = selectedSeverityFilter == severity
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MetallicGold else GlassWhite)
                                        .clickable { selectedSeverityFilter = severity }
                                        .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = severity,
                                        color = if (isSelected) SlateBg else TextSilver,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. TYPE & DURATION & SOURCE filters in multi-level scrollable row
                Text("نوع العمليات:", color = TextGray, fontSize = 9.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(listOf("الكل", "الملفات", "الأوامر", "الذكاء الاصطناعي", "النظام")) { type ->
                        val isSelected = selectedTypeFilter == type
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MetallicGold else GlassWhite)
                                .clickable { selectedTypeFilter = type }
                                .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type,
                                color = if (isSelected) SlateBg else TextSilver,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("المدة الزمنية:", color = TextGray, fontSize = 9.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(listOf("الكل", "آخر ساعة", "آخر 24 ساعة", "آخر 7 أيام")) { dur ->
                        val isSelected = selectedDurationFilter == dur
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MetallicGold else GlassWhite)
                                .clickable { selectedDurationFilter = dur }
                                .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dur,
                                color = if (isSelected) SlateBg else TextSilver,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("مصدر المعالجة:", color = TextGray, fontSize = 9.sp)
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(listOf("الكل", "🫧 الفقاعة الذهبية", "⌨️ لوحة المفاتيح IME", "🟢 تلقائي", "✍️ يدوي", "📦 حزمة البناء", "🧠 الالتقاط الذكي")) { src ->
                        val isSelected = selectedSourceFilter == src
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MetallicGold else GlassWhite)
                                .clickable { selectedSourceFilter = src }
                                .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = src,
                                color = if (isSelected) SlateBg else TextSilver,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. SORT & MODE Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sorting Switch button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlassWhite)
                            .clickable { sortNewestFirst = !sortNewestFirst }
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MetallicGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (sortNewestFirst) "الترتيب: الأحدث أولاً" else "الترتيب: الأقدم أولاً",
                            color = TextSilver,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Multi-select toggle button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMultiSelectMode) MetallicGold.copy(alpha = 0.2f) else GlassWhite)
                            .clickable {
                                isMultiSelectMode = !isMultiSelectMode
                                if (isMultiSelectMode) {
                                    // Prepopulate selected log IDs with all currently filtered log IDs
                                    selectedLogIds = filteredLogs.map { it.id }.toSet()
                                    Toast.makeText(context, "تم تحديد ${filteredLogs.size} سجلات تلقائياً.", Toast.LENGTH_SHORT).show()
                                } else {
                                    selectedLogIds = emptySet()
                                }
                            }
                            .border(1.dp, if (isMultiSelectMode) MetallicGold else GlassBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Default.CheckCircle else Icons.Default.List,
                            contentDescription = null,
                            tint = if (isMultiSelectMode) MetallicGold else TextSilver,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "وضع التحديد المتعدد",
                            color = if (isMultiSelectMode) MetallicGold else TextSilver,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // EXPORT BAR: Show if isMultiSelectMode is active OR any logs are selected
                val activeSelectedLogs = if (isMultiSelectMode) {
                    filteredLogs.filter { selectedLogIds.contains(it.id) }
                } else {
                    filteredLogs
                }

                if (isMultiSelectMode && filteredLogs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                selectedLogIds = filteredLogs.map { it.id }.toSet()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تحديد الكل", color = MetallicGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = { selectedLogIds = emptySet() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء التحديد", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (activeSelectedLogs.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GoldGlassBg, RoundedCornerShape(12.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "تصدير السجلات المحددة: (${activeSelectedLogs.size} من أصل ${filteredLogs.size})",
                            color = MetallicGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        // Expandable Advanced Export Settings
                        var showAdvancedExportSettings by remember { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedExportSettings = !showAdvancedExportSettings }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (showAdvancedExportSettings) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MetallicGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "خيارات التنسيق المتقدمة للتصدير والحفظ",
                                color = MetallicGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (showAdvancedExportSettings) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardSlateBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Toggle Include Details
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("تضمين تفاصيل العمليات:", color = TextSilver, fontSize = 9.sp)
                                    Switch(
                                        checked = exportIncludeDetails,
                                        onCheckedChange = { exportIncludeDetails = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SlateBg,
                                            checkedTrackColor = MetallicGold,
                                            uncheckedThumbColor = TextGray,
                                            uncheckedTrackColor = GlassWhite
                                        ),
                                        modifier = Modifier.scale(0.7f).height(24.dp)
                                    )
                                }
                                
                                // 2. Toggle Anonymize
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("تصفية وتعمية البيانات الحساسة (مفاتيح API، البريد):", color = TextSilver, fontSize = 9.sp)
                                    Switch(
                                        checked = exportAnonymizeSensitive,
                                        onCheckedChange = { exportAnonymizeSensitive = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SlateBg,
                                            checkedTrackColor = MetallicGold,
                                            uncheckedThumbColor = TextGray,
                                            uncheckedTrackColor = GlassWhite
                                        ),
                                        modifier = Modifier.scale(0.7f).height(24.dp)
                                    )
                                }
                                
                                // 3. HTML Theme Selector
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("نمط مظهر تقرير HTML التفاعلي:", color = TextSilver, fontSize = 9.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf(
                                            Pair("dark", "داكن كوني"),
                                            Pair("light", "مشرق فضي"),
                                            Pair("gold", "ذهبي فاخر")
                                        ).forEach { (themeKey, themeLabel) ->
                                            val isSelected = exportHtmlTheme == themeKey
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(24.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) MetallicGold else CardSlateBg)
                                                    .clickable { exportHtmlTheme = themeKey }
                                                    .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = themeLabel,
                                                    color = if (isSelected) SlateBg else TextSilver,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // 4. Toggle Target Save Directory
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text("مكان حفظ الملف المصدر:", color = TextSilver, fontSize = 9.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf(
                                            Pair(true, "المجلد المختار في الإعدادات"),
                                            Pair(false, "مجلد التطبيق الافتراضي")
                                        ).forEach { (isSettingsDir, label) ->
                                            val isSelected = exportToSettingsDir == isSettingsDir
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(24.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSelected) MetallicGold else CardSlateBg)
                                                    .clickable { exportToSettingsDir = isSettingsDir }
                                                    .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) SlateBg else TextSilver,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val currentPathLabel = if (exportToSettingsDir) viewModel.baseDirSetting.collectAsState().value else context.getExternalFilesDir(null)?.absolutePath ?: ""
                                    Text(
                                        text = "مسار الحفظ: $currentPathLabel",
                                        color = TextMuted,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 5. Custom Formatting selectors (TXT, CSV, JSON)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("نمط وصيغة مخرجات TXT:", color = TextSilver, fontSize = 9.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            listOf(
                                                Pair("detailed", "تفصيلي"),
                                                Pair("simple", "مبسط"),
                                                Pair("markdown", "Markdown 📝")
                                            ).forEach { (styleKey, styleLabel) ->
                                                val isSelected = exportTxtStyle == styleKey
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(24.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) MetallicGold else CardSlateBg)
                                                        .clickable { exportTxtStyle = styleKey }
                                                        .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(6.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = styleLabel,
                                                        color = if (isSelected) SlateBg else TextSilver,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("فاصل مخرجات CSV (الجدولية):", color = TextSilver, fontSize = 9.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            listOf(
                                                Pair(",", "فاصلة ( , )"),
                                                Pair(";", "منقوطة ( ; )"),
                                                Pair("tab", "علامة Tab ⇥")
                                            ).forEach { (delimKey, delimLabel) ->
                                                val isSelected = exportCsvDelimiter == delimKey
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(24.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) MetallicGold else CardSlateBg)
                                                        .clickable { exportCsvDelimiter = delimKey }
                                                        .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(6.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = delimLabel,
                                                        color = if (isSelected) SlateBg else TextSilver,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("تنسيق ومسافات JSON:", color = TextSilver, fontSize = 9.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            listOf(
                                                Pair(4, "مسافة بادئة (4)"),
                                                Pair(2, "مسافة بادئة (2)"),
                                                Pair(0, "مضغوط (Compact)")
                                             ).forEach { (indentKey, indentLabel) ->
                                                 val isSelected = exportJsonIndent == indentKey
                                                 Box(
                                                     modifier = Modifier
                                                         .weight(1f)
                                                         .height(24.dp)
                                                         .clip(RoundedCornerShape(6.dp))
                                                         .background(if (isSelected) MetallicGold else CardSlateBg)
                                                         .clickable { exportJsonIndent = indentKey }
                                                         .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(6.dp)),
                                                     contentAlignment = Alignment.Center
                                                 ) {
                                                     Text(
                                                         text = indentLabel,
                                                         color = if (isSelected) SlateBg else TextSilver,
                                                         fontSize = 8.sp,
                                                         fontWeight = FontWeight.Bold
                                                     )
                                                 }
                                             }
                                         }
                                     }
                                 }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // 1. Copy & Share Rows
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    val logText = AppReportHelper.generateTxtReport(
                                        activeSelectedLogs, 
                                        editedLogsMap,
                                        includeDetails = exportIncludeDetails,
                                        anonymize = exportAnonymizeSensitive,
                                        style = exportTxtStyle
                                    )
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Event Logs", logText))
                                    Toast.makeText(context, "📋 تم نسخ سجل المخرجات التام!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(12.dp))
                                    Text("نسخ TXT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    val text = AppReportHelper.generateTxtReport(
                                        activeSelectedLogs, 
                                        editedLogsMap,
                                        includeDetails = exportIncludeDetails,
                                        anonymize = exportAnonymizeSensitive,
                                        style = exportTxtStyle
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    val chooser = Intent.createChooser(shareIntent, "مشاركة السجل")
                                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(chooser)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(12.dp))
                                    Text("مشاركة", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 2. Save options HTML + TXT, CSV, JSON
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    val html = AppReportHelper.generateInteractiveHtmlReport(
                                        activeSelectedLogs, 
                                        editedLogsMap,
                                        theme = exportHtmlTheme,
                                        includeDetails = exportIncludeDetails,
                                        anonymize = exportAnonymizeSensitive
                                    )
                                    val saveDir = if (exportToSettingsDir) File(viewModel.baseDirSetting.value) else null
                                    AppReportHelper.saveAndOpenHtmlReport(context, html, saveDir)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1.3f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = SlateBg, modifier = Modifier.size(12.dp))
                                    Text("🌐 HTML تفاعلي", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }

                            listOf("TXT", "CSV", "JSON").forEach { fmt ->
                                Button(
                                    onClick = {
                                        val saveDir = if (exportToSettingsDir) File(viewModel.baseDirSetting.value) else null
                                        when (fmt) {
                                            "TXT" -> {
                                                val txt = AppReportHelper.generateTxtReport(
                                                    activeSelectedLogs, 
                                                    editedLogsMap,
                                                    includeDetails = exportIncludeDetails,
                                                    anonymize = exportAnonymizeSensitive,
                                                    style = exportTxtStyle
                                                )
                                                AppReportHelper.saveAndShareFile(context, txt, "logs_export.txt", "text/plain", saveDir)
                                            }
                                            "CSV" -> {
                                                val csv = AppReportHelper.generateCsvReport(
                                                    activeSelectedLogs, 
                                                    editedLogsMap,
                                                    includeDetails = exportIncludeDetails,
                                                    anonymize = exportAnonymizeSensitive,
                                                    delimiter = exportCsvDelimiter
                                                )
                                                AppReportHelper.saveAndShareFile(context, csv, "logs_export.csv", "text/csv", saveDir)
                                            }
                                            "JSON" -> {
                                                val json = AppReportHelper.generateJsonReport(
                                                    activeSelectedLogs, 
                                                    editedLogsMap,
                                                    includeDetails = exportIncludeDetails,
                                                    anonymize = exportAnonymizeSensitive,
                                                    indent = exportJsonIndent
                                                )
                                                AppReportHelper.saveAndShareFile(context, json, "logs_export.json", "application/json", saveDir)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = TextSilver),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(0.9f).height(32.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(fmt, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Show empty indicator if logs list is empty
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(GlassWhite, RoundedCornerShape(20.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد سجلات مطابقة لمعايير الفلترة المحددة.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (logViewMode != "technical") {
                    // STORYBOARD VIEW (Story Cards)
                    val stories = remember(filteredLogs, logViewMode) {
                        com.example.LogAggregator.generateStoryCards(filteredLogs, logViewMode)
                    }
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        stories.forEach { story ->
                            StoryCardView(
                                story = story,
                                onCopyClick = { summaryText ->
                                    try {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Story Summary", summaryText))
                                        Toast.makeText(context, "📋 تم نسخ ملخص القصة بنجاح!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "فشل نسخ النص: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onFileClick = { path ->
                                    try {
                                        com.example.engine.FileUtils.openFile(context, path)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "فشل فتح الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // Logs rendering layout
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        filteredLogs.forEach { log ->
                            val isSelected = selectedLogIds.contains(log.id)
                            val finalDetails = editedLogsMap[log.id] ?: log.details ?: ""
                            
                            val sourceColor = when (log.source) {
                                "bubble" -> Color(0xFFD97706)
                                "ime" -> Color(0xFF3B82F6)
                                "auto" -> Color(0xFF10B981)
                                "manual" -> Color(0xFF8B5CF6)
                                "buildpack" -> Color(0xFFF59E0B)
                                "smartcapture" -> Color(0xFFEC4899)
                                else -> Color(0xFF10B981)
                            }
                            val sourceIcon = when (log.source) {
                                "bubble" -> "🫧"
                                "ime" -> "⌨️"
                                "auto" -> "🟢"
                                "manual" -> "✍️"
                                "buildpack" -> "📦"
                                "smartcapture" -> "🧠"
                                else -> "🟢"
                            }
                            val sourceName = when (log.source) {
                                "bubble" -> "الفقاعة الذهبية"
                                "ime" -> "لوحة المفاتيح IME"
                                "auto" -> "تلقائي"
                                "manual" -> "يدوي"
                                "buildpack" -> "حزمة البناء"
                                "smartcapture" -> "الالتقاط الذكي"
                                else -> "تلقائي"
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .background(
                                        if (isMultiSelectMode && isSelected) GoldGlassBg else GlassWhite,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isMultiSelectMode && isSelected) MetallicGold.copy(alpha = 0.5f) else GlassBorder.copy(alpha = 0.5f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        if (isMultiSelectMode) {
                                            selectedLogIds = if (isSelected) selectedLogIds - log.id else selectedLogIds + log.id
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left Border indicator bar colored with source color
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(5.dp)
                                            .background(sourceColor, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                                    )
                                    
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Checkbox if multi select mode is on
                                        if (isMultiSelectMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedLogIds = if (checked) selectedLogIds + log.id else selectedLogIds - log.id
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = MetallicGold,
                                                    checkmarkColor = SlateBg,
                                                    uncheckedColor = TextGray
                                                ),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        // Type Icon badge
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(
                                                    when (log.type) {
                                                        "builder" -> Color(0x203B82F6)
                                                        "executor" -> Color(0x20F59E0B)
                                                        "treedoc" -> Color(0x2084CC16)
                                                        "gemini" -> Color(0x208B5CF6)
                                                        else -> GoldGlassBg
                                                    },
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (log.type) {
                                                    "builder" -> Icons.Default.Create
                                                    "executor" -> Icons.Default.PlayArrow
                                                    "treedoc" -> Icons.Default.List
                                                    "gemini" -> Icons.Default.Star
                                                    else -> Icons.Default.Info
                                                },
                                                contentDescription = null,
                                                tint = when (log.type) {
                                                    "builder" -> Color(0xFF60A5FA)
                                                    "executor" -> Color(0xFFFBBF24)
                                                    "treedoc" -> Color(0xFFA3E635)
                                                    "gemini" -> Color(0xFFA78BFA)
                                                    else -> MetallicGold
                                                },
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }

                                        val isFailedLog = log.message.contains("❌") || log.message.contains("فشل") || finalDetails.contains("❌") || finalDetails.contains("فشل")
                                        val textColor = if (isFailedLog) DangerRed else TextSilver
                                        val detailsColor = if (isFailedLog) DangerRed.copy(alpha = 0.8f) else TextGray

                                        val foundPath = remember(log.message, finalDetails) {
                                            findExistingFilePath(context, log.message + " " + finalDetails)
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            // Source indicator tag inside card
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = sourceIcon,
                                                    fontSize = 10.sp
                                                )
                                                Text(
                                                    text = "المصدر: $sourceName",
                                                    color = sourceColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = log.message,
                                                    color = textColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                
                                                // ✏️ Edit Action Button
                                                IconButton(
                                                    onClick = {
                                                        editingLogForDetails = log
                                                        editingLogTextState = finalDetails
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit event description",
                                                        tint = MetallicGold.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                            if (finalDetails.isNotBlank()) {
                                                Text(
                                                    text = finalDetails,
                                                    color = detailsColor,
                                                    fontSize = 10.sp,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (foundPath != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(MetallicGold.copy(alpha = 0.15f))
                                                        .clickable {
                                                            try {
                                                                com.example.engine.FileUtils.openFile(context, foundPath)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "لا يمكن فتح الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        .border(0.5.dp, MetallicGold, RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.List,
                                                        contentDescription = null,
                                                        tint = MetallicGold,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Text(
                                                        text = "فتح الملف المرتبط: ${File(foundPath).name} ↗",
                                                        color = BrightGold,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)),
                                            color = TextMuted,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Log Details Dialog
    if (editingLogForDetails != null) {
        Dialog(onDismissRequest = { editingLogForDetails = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlateBg, RoundedCornerShape(24.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "✏️ تحرير وصف الحدث",
                        color = MetallicGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "الحدث الأصلي:\n${editingLogForDetails?.message}",
                        color = TextSilver,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editingLogTextState,
                        onValueChange = { editingLogTextState = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        label = { Text("الوصف أو التفاصيل الجديدة المحدثة", color = TextGray, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextSilver,
                            unfocusedTextColor = TextSilver
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                editingLogForDetails?.let { log ->
                                    editedLogsMap[log.id] = editingLogTextState
                                }
                                editingLogForDetails = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تحديث")
                        }
                        OutlinedButton(
                            onClick = { editingLogForDetails = null },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSilver),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء")
                        }
                    }
                }
            }
        }
    }

    // Modal outcomes
    if (showResultDialog) {
        Dialog(onDismissRequest = { showResultDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlateBg, RoundedCornerShape(24.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "تفاصيل العملية والمخرجات",
                        color = MetallicGold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = operationResultMsg,
                        color = TextSilver,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 250.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showResultDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حسنًا", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Interactive Inline File Editor Dialog
    if (showFileEditorDialog && editingFileEntity != null) {
        Dialog(onDismissRequest = { showFileEditorDialog = false }) {
            var fileSearchQuery by remember { mutableStateOf("") }
            val linesCount = remember(editingFileContent) { editingFileContent.lines().size }
            val wordsCount = remember(editingFileContent) { editingFileContent.split(Regex("\\s+")).filter { it.isNotBlank() }.size }
            val charsCount = remember(editingFileContent) { editingFileContent.length }
            val searchMatchesCount = remember(editingFileContent, fileSearchQuery) {
                if (fileSearchQuery.isBlank()) 0 else {
                    val matches = Regex(Regex.escape(fileSearchQuery), RegexOption.IGNORE_CASE).findAll(editingFileContent)
                    matches.count()
                }
            }
            
            val openFileInExternalApp = {
                try {
                    val entity = editingFileEntity!!
                    val file = File(entity.fullPath)
                    if (file.exists()) {
                        val authority = "${context.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(context, authority, file)
                        val mimeType = when (file.extension.lowercase(Locale.ROOT)) {
                            "html", "htm" -> "text/html"
                            "txt", "log", "properties" -> "text/plain"
                            "json" -> "application/json"
                            "csv" -> "text/csv"
                            "pdf" -> "application/pdf"
                            "png", "jpg", "jpeg", "webp" -> "image/*"
                            else -> "text/plain"
                        }
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooserIntent = Intent.createChooser(viewIntent, "فتح باستخدام")
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooserIntent)
                    } else {
                        Toast.makeText(context, "الملف غير موجود في القرص", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "فشل فتح الملف: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            val shareFile = {
                try {
                    val entity = editingFileEntity!!
                    val file = File(entity.fullPath)
                    if (file.exists()) {
                        file.writeText(editingFileContent)
                        val authority = "${context.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(context, authority, file)
                        val mimeType = when (file.extension.lowercase(Locale.ROOT)) {
                            "html", "htm" -> "text/html"
                            "txt", "log", "properties" -> "text/plain"
                            "json" -> "application/json"
                            "csv" -> "text/csv"
                            "pdf" -> "application/pdf"
                            else -> "text/plain"
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooserIntent = Intent.createChooser(shareIntent, "مشاركة الملف")
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooserIntent)
                    } else {
                        Toast.makeText(context, "الملف غير موجود لمشاركته", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "فشل مشاركة الملف: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            val copyToClipboard = {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("File Content", editingFileContent))
                    Toast.makeText(context, "📋 تم نسخ كامل محتوى الملف للحافظة!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "فشل نسخ المحتوى: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlateBg, RoundedCornerShape(24.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📁 مستكشف ومعاين الملفات",
                            color = MetallicGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .background(GoldGlassBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = editingFileEntity?.path?.substringAfterLast('.') ?: "ملف",
                                color = MetallicGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Text(
                        text = "المسار: ${editingFileEntity?.fullPath}",
                        color = TextMuted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Info Statistics row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val sizeKb = remember(editingFileContent) { 
                            try {
                                String.format(Locale.getDefault(), "%.2f KB", editingFileContent.toByteArray().size / 1024f)
                            } catch (e: Exception) {
                                "0 KB"
                            }
                        }
                        listOf(
                            "الأسطر: $linesCount",
                            "الكلمات: $wordsCount",
                            "الحروف: $charsCount",
                            "الحجم: $sizeKb"
                        ).forEach { stat ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(GlassWhite.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                    .border(0.5.dp, GlassBorder.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stat, color = TextSilver, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    // Inline Search Bar
                    OutlinedTextField(
                        value = fileSearchQuery,
                        onValueChange = { fileSearchQuery = it },
                        placeholder = { Text("بحث سريع داخل محتوى الملف...", color = TextMuted, fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(14.dp)) },
                        trailingIcon = {
                            if (fileSearchQuery.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = "$searchMatchesCount مطابقة",
                                        color = MetallicGold,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { fileSearchQuery = "" }, modifier = Modifier.size(16.dp)) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassBlack,
                            unfocusedContainerColor = GlassBlack
                        ),
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(fontSize = 11.sp),
                        singleLine = true
                    )

                    // Text Editor Area
                    OutlinedTextField(
                        value = editingFileContent,
                        onValueChange = { editingFileContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        textStyle = TextStyle(color = TextSilver, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassBlack,
                            unfocusedContainerColor = GlassBlack
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Helper Action buttons (Copy, Share, Open External)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = copyToClipboard,
                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg.copy(alpha = 0.8f), contentColor = TextSilver),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(30.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(12.dp))
                                Text("نسخ المحتوى", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = shareFile,
                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg.copy(alpha = 0.8f), contentColor = TextSilver),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(30.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(12.dp))
                                Text("مشاركة الملف", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = openFileInExternalApp,
                            colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg.copy(alpha = 0.8f), contentColor = TextSilver),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(30.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(12.dp))
                                Text("فتح ببرنامج خارجي", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom actions row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                editingFileEntity?.let {
                                    val f = File(it.fullPath)
                                    if (f.exists()) {
                                        f.delete()
                                    }
                                    scope.launch {
                                        viewModel.clearCreatedFilesList() // refresh
                                    }
                                    showFileEditorDialog = false
                                    Toast.makeText(context, "تم حذف الملف بنجاح من القرص واللوحة", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("حذف الملف", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showFileEditorDialog = false }) {
                                Text("إلغاء", color = TextGray, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    editingFileEntity?.let { entity ->
                                        try {
                                            File(entity.fullPath).writeText(editingFileContent)
                                            Toast.makeText(context, "تم حفظ التعديلات بنجاح!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "فشل الحفظ: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        showFileEditorDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("حفظ التعديلات", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// 3. TREEDOC TAB - Report generator with nested Interactive Directory Chooser
// =====================================================================

@Composable
fun TreeDocScreen(viewModel: MainViewModel) {
    var chosenFolder by remember { mutableStateOf(".") }
    var chosenFormat by remember { mutableStateOf("html") }
    var includeSizes by remember { mutableStateOf(true) }
    var includeMtime by remember { mutableStateOf(true) }
    var isGenerating by remember { mutableStateOf(false) }

    val treedocReportText = viewModel.treedocReport.collectAsState().value
    val browserCurrentPath = viewModel.currentBrowserPath.collectAsState().value
    val browserFiles = viewModel.browserFilesList.collectAsState().value

    var showDirectoryChooser by remember { mutableStateOf(false) }
    var newDirName by remember { mutableStateOf("") }
    var showNewDirDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Form Configuration Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("إعداد تقارير الشجرة (TreeDoc)", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Choose Folder Button Input
                    Text("المجلد المستهدف للمسح", color = TextGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = chosenFolder,
                            onValueChange = { chosenFolder = it },
                            modifier = Modifier.weight(1f).testTag("folder_input_field"),
                            textStyle = TextStyle(color = TextSilver, fontSize = 12.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MetallicGold.copy(alpha = 0.5f),
                                unfocusedBorderColor = GlassBorder,
                                focusedContainerColor = GlassBlack,
                                unfocusedContainerColor = GlassBlack
                            )
                        )

                        Button(
                            onClick = {
                                val resolvedPath = if (chosenFolder == "/") "/storage/emulated/0" else chosenFolder
                                val enteredFile = File(resolvedPath.ifBlank { viewModel.baseDirSetting.value })
                                viewModel.navigateToDir(enteredFile)
                                showDirectoryChooser = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("browse_button")
                        ) {
                            Text("تصفح", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Format Picker
                    Text("صيغة التقرير الإخراجية", color = TextGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("html" to "🖥️ موقع HTML", "txt" to "📝 نص شجري", "json" to "⚙️ بيانات JSON").forEach { (fmt, label) ->
                                val isSelected = chosenFormat == fmt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (isSelected) GoldGlassBg else GlassWhite, RoundedCornerShape(12.dp))
                                        .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable { chosenFormat = fmt }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (isSelected) MetallicGold else TextSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("pdf" to "🎓 مستند PDF", "csv" to "📊 جداول CSV").forEach { (fmt, label) ->
                                val isSelected = chosenFormat == fmt
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (isSelected) GoldGlassBg else GlassWhite, RoundedCornerShape(12.dp))
                                        .border(1.dp, if (isSelected) MetallicGold else GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable { chosenFormat = fmt }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, color = if (isSelected) MetallicGold else TextSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Advanced Parameters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = includeSizes,
                                onCheckedChange = { includeSizes = it },
                                colors = CheckboxDefaults.colors(checkedColor = MetallicGold, uncheckedColor = TextGray)
                            )
                            Text("أحجام الملفات", color = TextSilver, fontSize = 11.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = includeMtime,
                                onCheckedChange = { includeMtime = it },
                                colors = CheckboxDefaults.colors(checkedColor = MetallicGold, uncheckedColor = TextGray)
                            )
                            Text("تواريخ التعديل", color = TextSilver, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isGenerating = true
                                viewModel.generateTreeReport(chosenFolder, chosenFormat, true) { message ->
                                    isGenerating = false
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(46.dp).testTag("generate_report_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(color = SlateBg, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("توليد ونسخ التقرير", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Output Monospace Terminal View
        if (treedocReportText.isNotBlank()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("التقرير المولّد الحاضر", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(treedocReportText))
                                Toast.makeText(context, "تم النسخ بنجاح للحافظة", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Copy Report text", tint = MetallicGold, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .background(GlassBlack, RoundedCornerShape(18.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = treedocReportText,
                            color = TextSilver,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }

    // Interactive Internal File Explorer Dialog Drawer
    if (showDirectoryChooser && browserCurrentPath != null) {
        Dialog(
            onDismissRequest = { showDirectoryChooser = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(CardSlateBg, RoundedCornerShape(26.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(26.dp))
                    .padding(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("المستكشف الداخلي للمنصة", color = MetallicGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        IconButton(
                            onClick = { showNewDirDialog = true }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "New Directory", tint = TextSilver, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "المسار الحالي: ${browserCurrentPath.absolutePath}",
                        color = TextGray,
                        fontSize = 10.sp,
                        maxLines = 2,
                        lineHeight = 14.sp,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val memoryShortcuts = listOf(
                            Pair("الرئيسية", "/storage/emulated/0"),
                            Pair("المستندات", "/storage/emulated/0/Documents"),
                            Pair("التنزيلات", "/storage/emulated/0/Download"),
                            Pair("مجلد المشاريع", File(viewModel.baseDirSetting.value).absolutePath)
                        )
                        memoryShortcuts.forEach { (label, pStr) ->
                            Button(
                                onClick = { viewModel.navigateToDir(File(pStr)) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (browserCurrentPath.absolutePath == pStr) MetallicGold else GoldGlassBg,
                                    contentColor = if (browserCurrentPath.absolutePath == pStr) SlateBg else TextSilver
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(label, fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Root navigation path lists
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(GlassBlack, RoundedCornerShape(16.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    ) {
                        if (browserFiles.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("هذا المجلد فارغ حالياً.", color = TextMuted, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                // Up level option
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.navigateUp() }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(16.dp))
                                        Text(".. (الرجوع للمجلد الأعلى)", color = MetallicGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))
                                }

                                items(browserFiles) { file ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (file.isDirectory) {
                                                    viewModel.navigateToDir(file)
                                                }
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (file.isDirectory) Icons.Default.Menu else Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (file.isDirectory) MetallicGold else TextSilver,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(file.name, color = TextSilver, fontSize = 12.sp)
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteFileFromBrowser(file) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete File", tint = DangerRed.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showDirectoryChooser = false }) {
                            Text("إلغاء", color = TextGray)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                chosenFolder = browserCurrentPath.name.ifBlank { "." }
                                showDirectoryChooser = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("تحديد المجلد الحالي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Modal Directory Creation dialogue
    if (showNewDirDialog) {
        Dialog(onDismissRequest = { showNewDirDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlateBg, RoundedCornerShape(20.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text("إنشاء مجلد فرعي جديد", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newDirName,
                        onValueChange = { newDirName = it },
                        placeholder = { Text("مثلاً: designs", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showNewDirDialog = false }) {
                            Text("إلغاء", color = TextGray)
                        }
                        Button(
                            onClick = {
                                if (newDirName.isNotBlank()) {
                                    viewModel.createDirectoryInBrowser(newDirName)
                                    newDirName = ""
                                }
                                showNewDirDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg)
                        ) {
                            Text("إنشاء", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// 4. EXECUTOR TAB - Preloaded simulation actions & live terminal logs
// =====================================================================

@Composable
fun ExecutorScreen(viewModel: MainViewModel) {
    var rawCommandStr by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf("Ready to receive sandbox commands.") }
    var executorLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("منفذ التعليمات البرمجية والأوامر (Executor)", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))

                    // Live preloaded command cards
                    Text("أنماط تنفيذ وتوليد سريعة", color = TextGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            CommandBadge("build") {
                                rawCommandStr = "build"
                            }
                        }
                        item {
                            CommandBadge("run sample.py") {
                                rawCommandStr = "run sample.py"
                            }
                        }
                        item {
                            CommandBadge("open model.json") {
                                rawCommandStr = "open model.json"
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // CLI input box
                    OutlinedTextField(
                        value = rawCommandStr,
                        onValueChange = { rawCommandStr = it },
                        modifier = Modifier.fillMaxWidth().testTag("command_input_field"),
                        textStyle = TextStyle(color = TextSilver, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        placeholder = { Text("أدخل اسم الأمر المطلق هنا...", color = TextMuted, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassBlack,
                            unfocusedContainerColor = GlassBlack
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (rawCommandStr.isBlank()) {
                                Toast.makeText(context, "الرجاء تحديد نوع الأمر أولاً", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            executorLoading = true
                            viewModel.executeSingleCommand(rawCommandStr) { out ->
                                executorLoading = false
                                terminalOutput = out
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("execute_command_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (executorLoading) {
                            CircularProgressIndicator(color = SlateBg, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("تنفيذ الأمر الصادر الرديف", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // CLI Output display console
        item {
            Column {
                Text("شاشة كاشف مخرجات الطرفية", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
}

@Composable
fun CommandBadge(cmd: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(GoldGlassBg, RoundedCornerShape(8.dp))
            .border(1.dp, MetallicGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(cmd, color = MetallicGold, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// =====================================================================
// 5. GEMINI CHAT TAB - Custom chatbot with direct deployment buttons
// =====================================================================

@Composable
fun GeminiScreen(viewModel: MainViewModel) {
    var chatPromptStr by remember { mutableStateOf("") }
    val geminiReply = viewModel.geminiResponse.collectAsState().value
    val isLoading = viewModel.geminiLoading.collectAsState().value
    val isKeyAvailable = viewModel.getGeminiKeyAvailable()

    // Interactive Deploy State
    var isDeployingCode by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Warning API Key alert if empty
        if (!isKeyAvailable) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x20EF4444), RoundedCornerShape(18.dp))
                        .border(1.dp, DangerRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("مفتاح جمناي غير محدد", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "لاستخدام ميزات الذكاء الاصطناعي الكاملة، يرجى تهيئة مفتاح GEMINI_API_KEY في لوحة Secrets للمنصة، أو إضافته يدوياً بتبويب الإعدادات بالأسفل.",
                            color = TextSilver.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Preset prompts selection
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("توجيه محدد مسبقاً للرفيق", color = TextGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "انشئ تطبيق Python تجريبي" to "انشئ تطبيق Python تجريبي يطبع معلومات النظام والمنصة الذكية.",
                            "لد سكريبت Kotlin بسيط" to "اكتب سكريبت Kotlin يحتوي على توجيه @builder:file مع كود يقرأ الحافظة للأندرويد."
                        ).forEach { (caption, fullPrompt) ->
                            item {
                                Box(
                                    modifier = Modifier
                                        .background(GlassWhite, RoundedCornerShape(10.dp))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                        .clickable { chatPromptStr = fullPrompt }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(caption, color = TextSilver, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chat Input form
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("محادثة وكيل جمناي المخصص", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = chatPromptStr,
                        onValueChange = { chatPromptStr = it },
                        placeholder = { Text("اكتب طلبك ذكياً، ليرد جمناي ويبني لك التوجيهات الحية المباشرة فوراً...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("gemini_prompt_field"),
                        maxLines = 4,
                        textStyle = TextStyle(color = TextSilver, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassBlack,
                            unfocusedContainerColor = GlassBlack
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (chatPromptStr.isBlank()) return@Button
                            viewModel.sendGeminiRequest(chatPromptStr) { Toast.makeText(context, "وصلت الاستجابة الذكية!", Toast.LENGTH_SHORT).show() }
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp).testTag("gemini_send_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = SlateBg, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("إرسال للنموذج ذكياً", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // AI Response display bubble layout with embedded DIRECT DEPLOY Action Button
        if (geminiReply.isNotBlank()) {
            item {
                Column {
                    Text("محادثة جمناي الرديف ومقترحات الكود", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassBlack, RoundedCornerShape(18.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Text(
                                text = geminiReply,
                                color = TextSilver,
                                fontSize = 12.sp,
                                modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 280.dp)
                            )

                            // Detect builder directives inside the response and show GOLD DEPLOY BUTTON
                            if (geminiReply.contains("@builder:file")) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        isDeployingCode = true
                                        viewModel.runManualProcess(geminiReply) { out ->
                                            isDeployingCode = false
                                            Toast.makeText(context, out, Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGlow, contentColor = SlateBg),
                                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("deploy_gemini_directives_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isDeployingCode) {
                                        CircularProgressIndicator(color = SlateBg, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Build, contentDescription = null, tint = SlateBg, modifier = Modifier.size(16.dp))
                                            Text("تطبيق توجيهات الملفات المكتشفة فورا", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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

// =====================================================================
// 6. SETTINGS TAB - custom configuration settings
// =====================================================================

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    goldenFrameEnabled: Boolean,
    onToggleGoldenFrame: (Boolean) -> Unit,
    onNavigateToAIPromptHub: () -> Unit,
    onNavigateToQuickActionsHub: () -> Unit,
    onNavigateToHelpCenter: () -> Unit,
    onNavigateToSourceExport: () -> Unit,
    onNavigateToStyleBank: () -> Unit
) {
    var bPrefix by remember { mutableStateOf(viewModel.prefixBuilder.value) }
    var ePrefix by remember { mutableStateOf(viewModel.prefixExecutor.value) }
    var tPrefix by remember { mutableStateOf(viewModel.prefixTreedoc.value) }
    var bDir by remember { mutableStateOf(viewModel.baseDirSetting.value) }
    var apiKeyManual by remember { mutableStateOf("") }
    
    var showDirBrowser by remember { mutableStateOf(false) }
    var showProjectFolderBrowser by remember { mutableStateOf(false) }
    var showManualPermissionsDashboard by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var previewThemeToUse by remember { mutableStateOf("dark") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs = remember(context) { context.getSharedPreferences("SmartCapturePrefs", android.content.Context.MODE_PRIVATE) }
    var currentProjectPath by remember {
        mutableStateOf(com.example.engine.ProjectContextManager.getCurrentProjectPath(context))
    }

    androidx.compose.runtime.DisposableEffect(context) {
        com.example.engine.UnifiedPathManager.init(context)
        val observer = androidx.lifecycle.Observer<String> { path ->
            currentProjectPath = path
        }
        com.example.engine.UnifiedPathManager.activePath.observeForever(observer)
        onDispose {
            try {
                com.example.engine.UnifiedPathManager.activePath.removeObserver(observer)
            } catch (e: Exception) {}
        }
    }
    var enableContextManager by remember {
        mutableStateOf(prefs.getBoolean("enable_context_manager", true))
    }
    var folderNamingMode by remember {
        mutableStateOf(prefs.getString("folder_naming_mode", "SMART") ?: "SMART")
    }
    var fileNamingMode by remember {
        mutableStateOf(prefs.getString("file_naming_mode", "CLEAN") ?: "CLEAN")
    }
    var customNamingPattern by remember {
        mutableStateOf(prefs.getString("custom_naming_pattern", "{date}_{title}") ?: "{date}_{title}")
    }
    var developerMode by remember {
        mutableStateOf(prefs.getBoolean("developer_mode", false))
    }
    var autoUpdateEnabled by remember {
        mutableStateOf(prefs.getBoolean("auto_update_enabled", false))
    }

    val currentSavedBaseDir = viewModel.baseDirSetting.collectAsState().value

    LaunchedEffect(currentSavedBaseDir) {
        bDir = currentSavedBaseDir
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Developer Mode Settings Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("developer_mode_card")) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔓 تفعيل وضع المطور", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                com.example.ui.components.SettingsTooltip(
                                    title = "وضع المطور",
                                    description = "التحكم المتقدم بجميع سمات وأساليب الترجمة البرمجية وتخصيص CSS المتقدم ومدير السياق الفعال للبيئة الكودية.",
                                    example = "تفعيل هذا الخيار سيظهر لوحات معقدة مثل تخصيص النمط وحقن الـ CSS وإعدادات سياق المجلدات."
                                )
                            }
                            Text("إظهار خيارات التكوين والتحكم المتقدمة لملفات الـ CSS وأساليب التسمية العميقة والمنفذ", color = TextGray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = developerMode,
                            onCheckedChange = { isChecked ->
                                developerMode = isChecked
                                prefs.edit().putBoolean("developer_mode", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            ),
                            modifier = Modifier.testTag("developer_mode_switch")
                        )
                    }
                    if (developerMode) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "⚙️ أنت الآن في وضع المطور. هذه الخيارات للمستخدمين المتقدمين.",
                            color = BrightGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Chat Link Automator settings
        item {
            var sExtractCode by remember { mutableStateOf(context.getSharedPreferences("LinkAutomatorPrefs", Context.MODE_PRIVATE).getBoolean("extract_code", true)) }
            var sExtractText by remember { mutableStateOf(context.getSharedPreferences("LinkAutomatorPrefs", Context.MODE_PRIVATE).getBoolean("extract_text", true)) }
            var sApplySmartCapture by remember { mutableStateOf(context.getSharedPreferences("LinkAutomatorPrefs", Context.MODE_PRIVATE).getBoolean("apply_smart_capture", true)) }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔗 مؤتمت روابط المحادثات", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        com.example.ui.components.SettingsTooltip(
                            title = "مؤتمت روابط المحادثات",
                            description = "خيارات استخراج وتوجيه الكود والنص عند معالجة روابط ChatGPT أو DeepSeek أو Claude العامة.",
                            example = "مثلاً: إذا قمت بتعطيل استخراج النصوص، فسيتم تجاهل النصوص داخل <p> وحفظ الأكواد فقط."
                        )
                    }
                    Text("اضبط سلوك التقسيم والتوجيه التلقائي للملفات المستخرجة من روابط المحادثات العامة.", color = TextGray, fontSize = 10.sp)

                    Divider(color = GlassBorder, thickness = 0.6.dp)

                    // Switch 1: Extract Code
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("استخراج الأكواد تلقائياً", color = Color.White, fontSize = 12.sp)
                        Switch(
                            checked = sExtractCode,
                            onCheckedChange = {
                                sExtractCode = it
                                context.getSharedPreferences("LinkAutomatorPrefs", Context.MODE_PRIVATE).edit().putBoolean("extract_code", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SlateBg, checkedTrackColor = MetallicGold)
                        )
                    }

                    // Switch 2: Extract Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("استخراج النصوص والشروحات", color = Color.White, fontSize = 12.sp)
                        Switch(
                            checked = sExtractText,
                            onCheckedChange = {
                                sExtractText = it
                                context.getSharedPreferences("LinkAutomatorPrefs", Context.MODE_PRIVATE).edit().putBoolean("extract_text", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SlateBg, checkedTrackColor = MetallicGold)
                        )
                    }

                    // Switch 3: Apply Smart Capture on Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تطبيق الالتقاط الذكي على النصوص", color = Color.White, fontSize = 12.sp)
                        Switch(
                            checked = sApplySmartCapture,
                            onCheckedChange = {
                                sApplySmartCapture = it
                                context.getSharedPreferences("LinkAutomatorPrefs", Context.MODE_PRIVATE).edit().putBoolean("apply_smart_capture", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = SlateBg, checkedTrackColor = MetallicGold),
                            enabled = sExtractText
                        )
                    }
                }
            }
        }

        // Quick Actions section
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡ الإجراءات السريعة", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        com.example.ui.components.SettingsTooltip(
                            title = "الإجراءات السريعة",
                            description = "أزرار وأتمتة فعالة لتصنيف ونمذجة وتحليل وإعداد كتل النصوص بنقرة واحدة وتجنب أي تعارض في الأوضاع التشغيلية.",
                            example = "مثلاً: إعداد مجلدات المخرجات لـ 'التحليل الذكي' ليكون SmartInbox أو ضمن المجلد الفعال الحالي للمشروع."
                        )
                    }
                    Text("مسارات الإجراءات الخمسة الرائدة للتخصيص اليدوي، إعدادات الحفظ وتجاوز تداخل الأدوار والأوامر.", color = TextGray, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToQuickActionsHub,
                        modifier = Modifier.fillMaxWidth().height(40.dp).testTag("open_quick_actions_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold.copy(alpha = 0.15f), contentColor = MetallicGold)
                    ) {
                        Text("فتح مركز الإجراءات السريعة ⚡", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Automation Shield Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("مركز صلاحيات الأتمتة الخلفية", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            com.example.ui.components.SettingsTooltip(
                                title = "صلاحيات الأتمتة الخلفية",
                                description = "ضمان استمرارية الاستماع للأوامر بالخلفية ومنع توقف خدمة المراقبة أو الالتقاط تحت وضع حفظ الطاقة.",
                                example = "يتطلب تفعيل صلاحيات كيبورد الأتمتة (Accessibility)، المراقبة الفورية، والواجهة العائمة للفقاعة."
                            )
                        }
                        Text("مراجعة وتفعيل خدمات إمكانية الوصول، استثناء البطارية وصلاحية الملفات للأتمتة الكاملة", color = TextGray, fontSize = 10.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { showManualPermissionsDashboard = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إدارة الصلاحيات", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // AI Prompt Hub card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("ai_prompt_hub_card")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("تعليمات المساعدين الذكية (AI Prompt Hub)", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            com.example.ui.components.SettingsTooltip(
                                title = "تعليمات المساعدين الذكية",
                                description = "التحكم بتوجيهات السيستم والتحفيز البرمجي للمحرك الذكي، مخصص لبرمجة كتل تصغير الأكواد والقوالب الموجهة.",
                                example = "تحديد أسلوب صياغة المبرمج للملفات الفعالة، والتحكم بمخرجات المراجعة السريعة وحيازة السياق."
                            )
                        }
                        Text("تثبيت وتعديل التلقينات الأساسية (System Instructions) وتغيير سلوك الذكاء للمساعد تلقائياً", color = TextGray, fontSize = 10.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = onNavigateToAIPromptHub,
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("فتح المركز", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 🏛️ بنك الأنماط الفاخر (Style Bank)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("style_bank_card")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("بنك الأنماط الفاخر (Style Bank)", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            com.example.ui.components.SettingsTooltip(
                                title = "بنك الأنماط الفاخر",
                                description = "إدارة وتعديل أنماط CSS المخصصة والمستخرجة تلقائياً من الملفات وحقنها في مستنداتك.",
                                example = "تطبيق سمة أزرار ذهبية مخصصة أو بطاقات زجاجية فاخرة بنقرة زر."
                            )
                        }
                        Text("إضافة وتصفح وتطبيق أنماط الـ CSS المخصصة على قوالب المحتوى والمستندات الذكية", color = TextGray, fontSize = 10.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = onNavigateToStyleBank,
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("فتح البنك", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 🔄 التحقق من التحديثات تلقائيًا
        item {
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("auto_update_card")) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄 التحقق من التحديثات تلقائيًا", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            com.example.ui.components.SettingsTooltip(
                                title = "التحقق من التحديثات",
                                description = "التحقق من وجود تحديثات جديدة للتطبيق تلقائيًا في الخلفية مرة واحدة يوميًا.",
                                example = "عند العثور على إصدار أحدث، سيتلقى جهازك إشعارًا لتنزيل الـ APK مباشرة."
                            )
                        }
                        Text("جدولة فحص دوري في الخلفية لتحميل أحدث المزايا والتحسينات للمنصة.", color = TextGray, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = autoUpdateEnabled,
                        onCheckedChange = { isChecked ->
                            autoUpdateEnabled = isChecked
                            prefs.edit().putBoolean("auto_update_enabled", isChecked).apply()
                            if (isChecked) {
                                com.example.service.UpdateService.scheduleDailyUpdateCheck(context)
                                Toast.makeText(context, "🔄 تم تفعيل التحقق التلقائي من التحديثات", Toast.LENGTH_SHORT).show()
                            } else {
                                com.example.service.UpdateService.cancelDailyUpdateCheck(context)
                                Toast.makeText(context, "❌ تم إلغاء التحقق التلقائي من التحديثات", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SlateBg,
                            checkedTrackColor = MetallicGold,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = GlassWhite
                        ),
                        modifier = Modifier.testTag("auto_update_switch")
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("تهيئة البادئات المخصصة", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = bPrefix,
                        onValueChange = { bPrefix = it },
                        label = { Text("بادئة المنشئ (Builder Prefix)", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("prefix_builder_field"),
                        textStyle = TextStyle(color = TextSilver, fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = ePrefix,
                        onValueChange = { ePrefix = it },
                        label = { Text("بادئة المنفذ (Executor Prefix)", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = TextSilver, fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = tPrefix,
                        onValueChange = { tPrefix = it },
                        label = { Text("بادئة TreeDoc (TreeDoc Prefix)", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = TextSilver, fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            viewModel.savePrefixes(bPrefix, ePrefix, tPrefix)
                            Toast.makeText(context, "تم حفظ تغييرات البادئات بنجاح!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("save_prefixes_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("حفظ البادئات والرموز", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Base directory preferences card with Folder Browser select
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("المجلد الافتراضي لعمليات التصدير والأتمتة", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = bDir,
                            onValueChange = { bDir = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = { showDirBrowser = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(54.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp)
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "تصفح المجلدات", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("تصفح", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            viewModel.saveBaseDir(bDir)
                            Toast.makeText(context, "تم تحديث جذر الحفظ الافتراضي!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تثبيت مسار المجلد يدويًا", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dialogue modals inside Settings
        item {
            PermissionsDashboardDialog(
                isOpen = showManualPermissionsDashboard,
                onDismiss = { showManualPermissionsDashboard = false },
                viewModel = viewModel
            )

            DirectoryBrowserDialog(
                isOpen = showDirBrowser,
                onDismiss = { showDirBrowser = false },
                initialPath = bDir,
                onConfirm = { chosenPath ->
                    bDir = chosenPath
                    viewModel.saveBaseDir(chosenPath)
                    Toast.makeText(context, "تم حفظ وتثبيت مجلد العمل الجديد!", Toast.LENGTH_SHORT).show()
                }
            )

            DirectoryBrowserDialog(
                isOpen = showProjectFolderBrowser,
                onDismiss = { showProjectFolderBrowser = false },
                initialPath = currentProjectPath,
                onConfirm = { chosenPath ->
                    com.example.engine.UnifiedPathManager.setActivePath(context, chosenPath)
                    Toast.makeText(context, "تم تغيير مجلد المشروع الحالي إلى: $chosenPath", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Golden Frame Toggle Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("الوضع المُحاكى (إطار ذهبي)", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("تفعيل إطار الهاتف الفاخر المزود بشريط الحالة", color = TextGray, fontSize = 10.sp)
                    }

                    Switch(
                        checked = goldenFrameEnabled,
                        onCheckedChange = { onToggleGoldenFrame(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SlateBg,
                            checkedTrackColor = MetallicGold,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = GlassWhite
                        ),
                        modifier = Modifier.testTag("frame_simulation_switch")
                    )
                }
            }
        }

        // Central Live Theme Swapper Card (Task 7 Dynamic Visual Styles)
        item {
            val activeThemeId = com.example.ui.theme.ThemeManager.currentTheme.value

            GlassCard(modifier = Modifier.fillMaxWidth().testTag("visual_theme_selector_card")) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎨 سمة التطبيق والهوية البصرية النشطة", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        com.example.ui.components.SettingsTooltip(
                            title = "سمة التطبيق وهويته",
                            description = "اختر السمة التي تتناسب تماماً مع مهمتك الحالية ومع عملك اليومي. يتم تحديث كامل ألوان التطبيق والخطوط والظلال فورًا.",
                            example = "النمط الأكاديمي يعطيك تباينًا مريحًا للمذاكرة الطويلة بينما النمط التقني يعطيك تباينًا عاليًا مناسبًا لتطوير الكود البرمجي."
                        )
                    }
                    Text(
                        "حدد النمط المناسب لمهامك (التقني، الأكاديمي، البحثي، الإداري، اليومي) لتغيير ألوان التطبيق بالكامل فورياً وعرض واجهات متفردة.",
                        color = TextGray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    com.example.ui.theme.ThemeManager.themes.forEach { theme ->
                        val isSelected = activeThemeId == theme.id
                        val borderColor = if (isSelected) theme.primaryColor else GlassBorder
                        val backgroundGlow = if (isSelected) theme.primaryColor.copy(alpha = 0.12f) else Color.Transparent

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(backgroundGlow, RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.8.dp,
                                    color = borderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    com.example.ui.theme.ThemeManager.setTheme(theme.id, context)
                                    Toast.makeText(context, "تم تبديل السمة إلى: ${theme.nameRtl}", Toast.LENGTH_SHORT).show()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(theme.icon, fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = theme.nameRtl,
                                    color = if (isSelected) theme.primaryColor else TextSilver,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = theme.description,
                                    color = TextGray,
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp
                                )
                            }
                            if (isSelected) {
                                Text("✨ نشط", color = theme.primaryColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Luxurious Ivory Warmth Customizer & Calm Zen Breathing Guide (Premium features for Ivory Theme)
        if (com.example.ui.theme.ThemeManager.currentTheme.value == "theme_ivory") {
            item {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ivory_theme_premium_features")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🍯 ميزات النمط العاجي الفاخر الحصرية", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "يتحكم هذا الشريط بمزيج دفء الحليب والعسل لضبط درجة الخلفية والألوان العاجية لتريح بصرك أثناء القراءة الطويلة وتمنع إجهاد العين.",
                            color = TextGray,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )

                        // Warmth Slider
                        var currentWarmth by remember { mutableStateOf(com.example.ui.theme.ThemeManager.ivoryWarmthLevel.value) }
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("درجة دفء العاج (كريمي هادئ):", color = TextSilver, fontSize = 10.5.sp)
                                Text(
                                    text = when {
                                        currentWarmth < 0.25f -> "⚪ لؤلؤي بارد"
                                        currentWarmth < 0.55f -> "🥛 عاج الحليب"
                                        currentWarmth < 0.85f -> "🍯 عصير العسل"
                                        else -> "🍂 ذهبي فخاري دافئ"
                                    },
                                    color = BrightGold,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = currentWarmth,
                                onValueChange = {
                                    currentWarmth = it
                                    com.example.ui.theme.ThemeManager.updateIvoryWarmth(it, context)
                                },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = BrightGold,
                                    activeTrackColor = BrightGold.copy(alpha = 0.8f),
                                    inactiveTrackColor = GlassWhite
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Divider(color = GlassBorder.copy(alpha = 0.5f), thickness = 0.5.dp)

                        // Zen Breathing/Quiet Assistant (Task Focus Flow Helper)
                        var isBreathingActive by remember { mutableStateOf(false) }
                        var breathingStateText by remember { mutableStateOf("ابدأ تمرين التنفس") }
                        var breathCycle by remember { mutableStateOf(0) } // 0 = inhale, 1 = hold, 2 = exhale

                        // Self-trigger cycle
                        if (isBreathingActive) {
                            LaunchedEffect(key1 = breathCycle) {
                                when (breathCycle) {
                                    0 -> {
                                        breathingStateText = "شهيق... خذ نفسًا عميقًا 🌸"
                                        kotlinx.coroutines.delay(4000)
                                        breathCycle = 1
                                    }
                                    1 -> {
                                        breathingStateText = "احبس النفس... هدوء داخلي 🧘"
                                        kotlinx.coroutines.delay(4000)
                                        breathCycle = 2
                                    }
                                    2 -> {
                                        breathingStateText = "زفير... اطرد جميع التوترات 🍃"
                                        kotlinx.coroutines.delay(4000)
                                        breathCycle = 0
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GoldGlassBg.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .border(0.5.dp, GoldGlassBg.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "🧘 مساعد الهدوء والتركيز الفائق (Zen Flow)",
                                color = BrightGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "مدمج مع النمط العاجي لمساعدتك على تقليل مستويات الكورتيزول والتوتر والاندماج في مهام الأتمتة بهدوء تام.",
                                color = TextGray,
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                textAlign = TextAlign.Center
                            )

                            if (isBreathingActive) {
                                val scale by animateFloatAsState(
                                    targetValue = when (breathCycle) {
                                        0 -> 1.5f
                                        1 -> 1.5f
                                        else -> 0.9f
                                    },
                                    animationSpec = tween(4000),
                                    label = "scale"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .scale(scale)
                                        .background(BrightGold.copy(alpha = 0.12f), CircleShape)
                                        .border(1.5.dp, BrightGold, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(45.dp)
                                            .background(BrightGold.copy(alpha = 0.25f), CircleShape)
                                    )
                                }

                                Text(
                                    text = breathingStateText,
                                    color = TextSilver,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Button(
                                onClick = {
                                    isBreathingActive = !isBreathingActive
                                    if (isBreathingActive) {
                                        breathCycle = 0
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isBreathingActive) DangerRed.copy(alpha = 0.2f) else BrightGold.copy(alpha = 0.2f),
                                    contentColor = if (isBreathingActive) DangerRed else BrightGold
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isBreathingActive) DangerRed else BrightGold)
                            ) {
                                Text(
                                    if (isBreathingActive) "🛑 إيقاف تمرين التنفس" else "✨ تشغيل تمرين هدوء العاج (Zen Cycle)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Golden Bubble Service Activator Card (Problem 3)
        item {
            val smartPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
            var goldenBubbleActive by remember {
                mutableStateOf(smartPrefs.getBoolean("golden_bubble_enabled", true))
            }
            
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("الفقاعة الذهبية العائلة V2", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("تشغيل أو إيقاف خدمة الكرة المساعدة الذهبية العائمة على الشاشة لسهولة الأتمتة السريعة", color = TextGray, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = goldenBubbleActive,
                        onCheckedChange = { isChecked ->
                            goldenBubbleActive = isChecked
                            smartPrefs.edit().putBoolean("golden_bubble_enabled", isChecked).apply()
                            
                            val serviceIntent = Intent(context, com.example.service.GoldenBubbleService::class.java)
                            if (isChecked) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "الرجاء منح صلاحية الظهور فوق التطبيقات لتفعيل الفقاعة!", Toast.LENGTH_LONG).show()
                                    try {
                                        val intent = Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "خطأ فتح صلاحيات النظام: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    goldenBubbleActive = false
                                    smartPrefs.edit().putBoolean("golden_bubble_enabled", false).apply()
                                } else {
                                    try {
                                        context.startService(serviceIntent)
                                        Toast.makeText(context, "تم تشغيل الفقاعة الذهبية V2!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "فشل بدء الخدمة: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                try {
                                    context.stopService(serviceIntent)
                                    Toast.makeText(context, "تم إيقاف الفقاعة الذهبية.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "فشل إيقاف الخدمة: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SlateBg,
                            checkedTrackColor = MetallicGold,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = GlassWhite
                        ),
                        modifier = Modifier.testTag("golden_bubble_service_switch")
                    )
                }
            }
        }

        // Log Copy Count Config Card (Problem 5)
        item {
            val smartPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
            var logCopyCount by remember {
                mutableStateOf(smartPrefs.getInt("log_copy_count", 5).toString())
            }
            
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("إعدادات نسخ سجل الأحداث", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = logCopyCount,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                logCopyCount = newValue
                                val parsed = newValue.toIntOrNull() ?: 5
                                smartPrefs.edit().putInt("log_copy_count", parsed).apply()
                            }
                        },
                        label = { Text("عدد الأحداث المراد نسخها (log_copy_count)", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("log_copy_count_field"),
                        textStyle = TextStyle(color = TextSilver, fontSize = 12.sp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "حدد عدد آخر أحداث سيتم نسخها إلى الحافظة دفعة واحدة عند النقر على زر 'نسخ آخر الأحداث' بالفقاعة.",
                        color = TextGray,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 🔄 نمط عرض السجل
        item {
            val smartPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
            var currentLogViewMode by remember {
                mutableStateOf(smartPrefs.getString("log_view_mode", "technical") ?: "technical")
            }
            
            GlassCard(modifier = Modifier.fillMaxWidth().testTag("log_view_mode_card")) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("نمط عرض سجل الأحداث", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("اختر طريقة عرض وتجميع سجلات العمليات والأحداث التلقائية:", color = TextGray, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    val modes = listOf(
                        Triple("technical", "⚙️ تقني (مفصل)", "عرض السجل التفصيلي الخام كما ورد من بيئة العمل."),
                        Triple("developer", "💻 قصة مصورة (مطور)", "عرض مجمع يركز على أسماء الملفات والعمليات."),
                        Triple("academic", "🎓 قصة مصورة (أكاديمي)", "عرض ملخص باستخدام مسميات وصياغات هيكلية أكاديمية دقيقة."),
                        Triple("user", "👥 قصة مصورة (مستخدم)", "عرض مبسط مريح بجمل واضحة للمستخدم.")
                    )

                    modes.forEach { (modeKey, modeTitle, modeDesc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentLogViewMode = modeKey
                                    smartPrefs.edit().putString("log_view_mode", modeKey).apply()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentLogViewMode == modeKey,
                                onClick = {
                                    currentLogViewMode = modeKey
                                    smartPrefs.edit().putString("log_view_mode", modeKey).apply()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MetallicGold,
                                    unselectedColor = TextGray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(modeTitle, color = TextSilver, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(modeDesc, color = TextGray, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // Clipboard Auto-Processing Toggle Card
        item {
            val autoProcessEnabled = viewModel.autoProcessClipboard.collectAsState().value
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("المعالجة التلقائية للحافظة", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("توليد الملفات والتعليمات تلقائياً بمجرد نسخ التوجيهات وحفظها في المجلد الافتراضي دون تدخل يدوي", color = TextGray, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = autoProcessEnabled,
                        onCheckedChange = { viewModel.setAutoProcessClipboard(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SlateBg,
                            checkedTrackColor = MetallicGold,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = GlassWhite
                        ),
                        modifier = Modifier.testTag("auto_process_clipboard_switch")
                    )
                }
            }
        }

        // Project Context Card
        if (developerMode) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("مدير سياق المشروع", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("مقارنة النصوص الملتقطة مع سياق الكلمات المفتاحية للمشروع الحالي لمنع تداخل سياقات المحادثات وتحديد مجلدات منفصلة تلقائياً بمرونة تامة.", color = TextGray, fontSize = 10.sp)
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تفعيل مدير السياق", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("تنبيهك وطلب قرار الحفظ عند نسخ نصوص بعيدة عن سياق المجلد الحالي", color = TextGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = enableContextManager,
                            onCheckedChange = { isChecked ->
                                enableContextManager = isChecked
                                prefs.edit().putBoolean("enable_context_manager", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            )
                        )
                    }
                    
                    if (enableContextManager) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = GlassBorder.copy(alpha = 0.2f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("مسار المشروع الحالي والنشط للالتقاط:", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = currentProjectPath,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.weight(1f),
                                textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextSilver,
                                    unfocusedTextColor = TextSilver,
                                    focusedBorderColor = MetallicGold,
                                    unfocusedBorderColor = GlassBorder,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            
                            Button(
                                onClick = { showProjectFolderBrowser = true },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(54.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "تغيير مجلد المشروع", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تغيير", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        }

        // Advanced Naming Options Card
        if (developerMode) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("إعدادات التسمية المتقدمة والمخصصة", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("تخصيص طريقة اقتراح أسماء المجلدات ونمط تسمية مستنداتك وملفات كود البرمجة الملتقطة بمرونة مطلقة.", color = TextGray, fontSize = 10.sp)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 1. Folder naming Mode Selector
                    Text("💡 طريقة تسمية مجلدات المشاريع الجديدة:", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            Triple("SMART", "ذكية (تلقائي)", "تستخرج الكلمات المفتاحية الذكية"),
                            Triple("FIRST_LINE", "أول سطر", "تأخذ أول 50 حرفاً"),
                            Triple("MANUAL", "يسألني دائماً", "تطلب تسمية يدوية")
                        )
                        modes.forEach { (modeCode, modeTitle, _) ->
                            val isSelected = folderNamingMode == modeCode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .background(
                                        if (isSelected) MetallicGold.copy(alpha = 0.25f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MetallicGold else GlassBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        folderNamingMode = modeCode
                                        prefs.edit().putString("folder_naming_mode", modeCode).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeTitle,
                                    color = if (isSelected) BrightGold else TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // 2. File naming Pattern Selector
                    Text("📝 نمط تسمية الملفات والمستندات:", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fileModes = listOf(
                            Triple("CLEAN", "نظيفة وآمنة", "تنظيف كامل وحذف رموز Markdown وكلمات النظام الشائعة"),
                            Triple("RAW", "خام", "حذف رموز نظام الملفات الممنوعة فقط مع الحفاظ على البقية"),
                            Triple("CUSTOM", "نمط مخصص", "استخدام قالب مخصص محدد من قبلك")
                        )
                        fileModes.forEach { (modeCode, modeTitle, _) ->
                            val isSelected = fileNamingMode == modeCode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .background(
                                        if (isSelected) MetallicGold.copy(alpha = 0.25f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MetallicGold else GlassBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        fileNamingMode = modeCode
                                        prefs.edit().putString("file_naming_mode", modeCode).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeTitle,
                                    color = if (isSelected) BrightGold else TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    
                    if (fileNamingMode == "CUSTOM") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("اكتب نمط التسمية المخصص الخاص بك (يمكنك استخدام {date} للتاريخ و {title} للعنوان):", color = TextGray, fontSize = 9.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        OutlinedTextField(
                            value = customNamingPattern,
                            onValueChange = { newVal ->
                                customNamingPattern = newVal
                                prefs.edit().putString("custom_naming_pattern", newVal).apply()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextSilver,
                                unfocusedTextColor = TextSilver,
                                focusedBorderColor = MetallicGold,
                                unfocusedBorderColor = GlassBorder,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
        }

        // Smart Capture Master Toggle & Configurations Panel
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = remember { context.getSharedPreferences("SmartCapturePrefs", android.content.Context.MODE_PRIVATE) }
            val smartCaptureEnabled = remember { 
                androidx.compose.runtime.mutableStateOf(prefs.getBoolean("smart_capture_enabled", false)) 
            }
            val autoImportTemplates = remember {
                androidx.compose.runtime.mutableStateOf(prefs.getBoolean("auto_import_templates", true))
            }
            val saveAllTexts = remember { 
                androidx.compose.runtime.mutableStateOf(prefs.getBoolean("save_all_texts", false)) 
            }
            val ignoreShortTexts = remember { 
                androidx.compose.runtime.mutableStateOf(prefs.getBoolean("ignore_short_texts", true)) 
            }
            val applyAllThemes = remember { 
                androidx.compose.runtime.mutableStateOf(prefs.getBoolean("apply_all_themes", false)) 
            }
            val customCss = remember { 
                androidx.compose.runtime.mutableStateOf(prefs.getString("custom_css", "") ?: "") 
            }
            val activeThemesCsv = remember {
                androidx.compose.runtime.mutableStateOf(prefs.getString("active_themes", "dark,light,academic,oasis,space") ?: "dark,light,academic,oasis,space")
            }
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Master Toggle Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الالتقاط الذكي للنصوص", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("تحليل النصوص المنسوخة التي لا تحتوي على توجيهات، تصنيف نوعها تلقائياً وحفظها في مجلدات مخصصة", color = TextGray, fontSize = 10.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = smartCaptureEnabled.value,
                            onCheckedChange = { isChecked ->
                                smartCaptureEnabled.value = isChecked
                                prefs.edit().putBoolean("smart_capture_enabled", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            ),
                            modifier = Modifier.testTag("smart_capture_enabled_switch")
                        )
                    }
                }

                // Sub-Settings Cards (Animated / Conditional Visibility)
                if (smartCaptureEnabled.value) {
                    // Control Settings Card
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MetallicGold.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MetallicGold,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text("إعدادات التحكم المتقدمة بالالتقاط", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            // 1. Save All Texts
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("حفظ كل النصوص المنسوخة (Save All)", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text("حفظ النصوص غير المعرفة تلقائياً كملفات عادية دون تجاهلها", color = TextGray, fontSize = 9.sp)
                                }
                                Switch(
                                    checked = saveAllTexts.value,
                                    onCheckedChange = { isChecked ->
                                        saveAllTexts.value = isChecked
                                        prefs.edit().putBoolean("save_all_texts", isChecked).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SlateBg,
                                        checkedTrackColor = MetallicGold,
                                        uncheckedThumbColor = TextGray,
                                        uncheckedTrackColor = GlassWhite
                                    ),
                                    modifier = Modifier.testTag("save_all_texts_switch")
                                )
                            }

                            Divider(color = GlassBorder.copy(alpha = 0.2f), thickness = 0.5.dp)

                            // 2. Ignore Short Texts
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("تجاهل النصوص القصيرة (Ignore Short)", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text("تجنب التقاط الكلمات والنصوص القصيرة جداً (أقل من 20 حرفاً) لمنع تكرار وحفظ القصاصات العشوائية", color = TextGray, fontSize = 9.sp)
                                }
                                Switch(
                                    checked = ignoreShortTexts.value,
                                    onCheckedChange = { isChecked ->
                                        ignoreShortTexts.value = isChecked
                                        prefs.edit().putBoolean("ignore_short_texts", isChecked).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SlateBg,
                                        checkedTrackColor = MetallicGold,
                                        uncheckedThumbColor = TextGray,
                                        uncheckedTrackColor = GlassWhite
                                    ),
                                    modifier = Modifier.testTag("ignore_short_texts_switch")
                                )
                            }

                            Divider(color = GlassBorder.copy(alpha = 0.2f), thickness = 0.5.dp)

                            // 3. Apply All Themes
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("تطبيق جميع السمات (Apply All Themes)", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text("توليد الملف وتكراره بكل القوالب النشطة وحفظه في مجلداتها بشكل متوازٍ", color = TextGray, fontSize = 9.sp)
                                }
                                Switch(
                                    checked = applyAllThemes.value,
                                    onCheckedChange = { isChecked ->
                                        applyAllThemes.value = isChecked
                                        prefs.edit().putBoolean("apply_all_themes", isChecked).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SlateBg,
                                        checkedTrackColor = MetallicGold,
                                        uncheckedThumbColor = TextGray,
                                        uncheckedTrackColor = GlassWhite
                                    ),
                                    modifier = Modifier.testTag("apply_all_themes_switch")
                                )
                            }

                            Divider(color = GlassBorder.copy(alpha = 0.2f), thickness = 0.5.dp)

                            // 3.5. Auto Import Templates from clipboard
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("الالتقاط التلقائي للقوالب", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text("تحليل واستيراد وتفعيل قوالب التصنيفات الذكية والمشاريع فور نسخها إلى الحافظة", color = TextGray, fontSize = 9.sp)
                                }
                                Switch(
                                    checked = autoImportTemplates.value,
                                    onCheckedChange = { isChecked ->
                                        autoImportTemplates.value = isChecked
                                        prefs.edit().putBoolean("auto_import_templates", isChecked).apply()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SlateBg,
                                        checkedTrackColor = MetallicGold,
                                        uncheckedThumbColor = TextGray,
                                        uncheckedTrackColor = GlassWhite
                                    ),
                                    modifier = Modifier.testTag("auto_import_templates_switch")
                                )
                            }

                            // 4. Multiple Themes selector (Only if Multi-Theme is enabled)
                            if (applyAllThemes.value) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("السمات النشطة للتوليد المتوازي:", color = MetallicGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                val activeSet = activeThemesCsv.value.split(",").filter { it.isNotBlank() }.toSet()
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val row1 = listOf("dark" to "داكن ذهبي", "light" to "فاتح نيون", "academic" to "أكاديمي عتيق")
                                    row1.forEach { (themeId, label) ->
                                        val isActive = activeSet.contains(themeId)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isActive) GoldGlassBg else GlassWhite,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    if (isActive) MetallicGold else GlassBorder,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    val newSet = if (isActive) activeSet - themeId else activeSet + themeId
                                                    val newCsv = newSet.joinToString(",")
                                                    activeThemesCsv.value = newCsv
                                                    prefs.edit().putString("active_themes", newCsv).apply()
                                                }
                                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically, 
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.align(Alignment.Center)
                                            ) {
                                                Checkbox(
                                                    checked = isActive,
                                                    onCheckedChange = {
                                                        val newSet = if (isActive) activeSet - themeId else activeSet + themeId
                                                        val newCsv = newSet.joinToString(",")
                                                        activeThemesCsv.value = newCsv
                                                        prefs.edit().putString("active_themes", newCsv).apply()
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = MetallicGold,
                                                        checkmarkColor = SlateBg
                                                    ),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(label, color = if (isActive) MetallicGold else TextSilver, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val row2 = listOf("oasis" to "واحة هادئة", "space" to "سديم فضائي")
                                    row2.forEach { (themeId, label) ->
                                        val isActive = activeSet.contains(themeId)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isActive) GoldGlassBg else GlassWhite,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    if (isActive) MetallicGold else GlassBorder,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    val newSet = if (isActive) activeSet - themeId else activeSet + themeId
                                                    val newCsv = newSet.joinToString(",")
                                                    activeThemesCsv.value = newCsv
                                                    prefs.edit().putString("active_themes", newCsv).apply()
                                                }
                                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically, 
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.align(Alignment.Center)
                                            ) {
                                                Checkbox(
                                                    checked = isActive,
                                                    onCheckedChange = {
                                                        val newSet = if (isActive) activeSet - themeId else activeSet + themeId
                                                        val newCsv = newSet.joinToString(",")
                                                        activeThemesCsv.value = newCsv
                                                        prefs.edit().putString("active_themes", newCsv).apply()
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = MetallicGold,
                                                        checkmarkColor = SlateBg
                                                    ),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(label, color = if (isActive) MetallicGold else TextSilver, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            }
                                        }
                                    }
                                    Box(modifier = Modifier.weight(1f)) // spacer weight
                                }
                            }
                        }
                    }

                    // CSS Customizer & Preview Card
                    if (developerMode) {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MetallicGold.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MetallicGold,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text("تخصيص العرض وأنماط الـ CSS المتقدمة", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            Text("أدخل أكواد الـ CSS المخصصة ليتم حقنها مباشرة في جميع مستندات الـ HTML التي يولدها النظام:", color = TextGray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            com.example.ui.components.CodeEditor(
                                value = customCss.value,
                                onValueChange = { cssVal ->
                                    customCss.value = cssVal
                                    prefs.edit().putString("custom_css", cssVal).apply()
                                },
                                language = "css",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .testTag("custom_css_input"),
                                placeholder = "/* اكتب أنماط CSS خاصة بك هنا */\nbody { font-family: sans-serif; }"
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    previewThemeToUse = prefs.getString("document_theme", "dark") ?: "dark"
                                    showPreviewDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold.copy(alpha = 0.15f), contentColor = MetallicGold)
                            ) {
                                Text("👁️ معاينة النمط للتطبيق والمظهر المباشر", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                }
            }
        }

        // Live Document Theme Selector Card (Visible whether smart capture is on/off as it is the default visual styles)
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = remember { context.getSharedPreferences("SmartCapturePrefs", android.content.Context.MODE_PRIVATE) }
            val currentTheme = remember { 
                androidx.compose.runtime.mutableStateOf(prefs.getString("document_theme", "dark") ?: "dark") 
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(MetallicGold.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MetallicGold,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text("السمة الافتراضية لعرض المستندات", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        // Small Preview button
                        TextButton(
                            onClick = {
                                previewThemeToUse = currentTheme.value
                                showPreviewDialog = true
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MetallicGold)
                        ) {
                            Text("👁️ معاينة السمة", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("اختر اللمسة الفاخرة التي يتم بها توليد صفحات HTML الخاصة بمستندات smartInbox كقالب افتراضي", color = TextGray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    val themesRow1 = listOf(
                        Triple("dark", "داكن ذهبي", androidx.compose.ui.graphics.Color(0xFFfbbf24)),
                        Triple("light", "فاتح نيون", androidx.compose.ui.graphics.Color(0xFF6366f1)),
                        Triple("academic", "أكاديمي عتيق", androidx.compose.ui.graphics.Color(0xFF7c2d12))
                    )
                    
                    val themesRow2 = listOf(
                        Triple("oasis", "واحة هادئة", androidx.compose.ui.graphics.Color(0xFF22c55e)),
                        Triple("space", "سديم فضائي", androidx.compose.ui.graphics.Color(0xFFec4899))
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            themesRow1.forEach { (themeId, label, dotColor) ->
                                val isSelected = currentTheme.value == themeId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) GoldGlassBg else GlassWhite, 
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp, 
                                            if (isSelected) MetallicGold else GlassBorder, 
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { 
                                            currentTheme.value = themeId
                                            prefs.edit().putString("document_theme", themeId).apply()
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag("document_theme_${themeId}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(dotColor, CircleShape)
                                        )
                                        Text(
                                            label, 
                                            color = if (isSelected) MetallicGold else TextSilver, 
                                            fontSize = 11.sp, 
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            themesRow2.forEach { (themeId, label, dotColor) ->
                                val isSelected = currentTheme.value == themeId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) GoldGlassBg else GlassWhite, 
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp, 
                                            if (isSelected) MetallicGold else GlassBorder, 
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { 
                                            currentTheme.value = themeId
                                            prefs.edit().putString("document_theme", themeId).apply()
                                        }
                                        .padding(vertical = 10.dp)
                                        .testTag("document_theme_${themeId}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(dotColor, CircleShape)
                                        )
                                        Text(
                                            label, 
                                            color = if (isSelected) MetallicGold else TextSilver, 
                                            fontSize = 11.sp, 
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            // Add a placeholder weight for perfect symmetry
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Clear Clipboard Option Tag Toggle
        item {
            val clearClipEnabled = viewModel.clearClipAfterSave.collectAsState().value
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("مسح الحافظة بعد الحفظ التلقائي", color = TextSilver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("حذف محتوى الحافظة تلقائياً بعد معالجتها وحفظها بنجاح لمنع التكرار وحماية خصوصية بياناتك", color = TextGray, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = clearClipEnabled,
                        onCheckedChange = { viewModel.setClearClipAfterSave(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SlateBg,
                            checkedTrackColor = MetallicGold,
                            uncheckedThumbColor = TextGray,
                            uncheckedTrackColor = GlassWhite
                        ),
                        modifier = Modifier.testTag("clear_clip_after_save_switch")
                    )
                }
            }
        }

        // Custom Gemini API key card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("مفتاح جمناي المخصص (Manual Key)", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyManual,
                        onValueChange = { apiKeyManual = it },
                        placeholder = { Text("أدخل مفتاح جمناي يدويًا هنا...", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = TextSilver, fontSize = 12.sp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (apiKeyManual.isNotBlank()) {
                                viewModel.setCustomGeminiKey(apiKeyManual)
                                apiKeyManual = ""
                                Toast.makeText(context, "تم حفظ مفتاح API جمناي بنجاح!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تهيئة المفتاح والدمج الذكي", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // System Prompt card for AI Helpers
        item {
            val localClipboardManager = LocalClipboardManager.current
            val promptText = """
أنت مساعد ذكي لتطبيق "المراقب الذكي". يمكنك إصدار أوامر للتحكم في ملفات المستخدم باستخدام الصيغة التالية:
@executor:command_name --param1=value1 --param2=value2
أو باستخدام كتلة JSON بعد اسم الأمر.
الأوامر المتاحة:
- scan: مسح مجلد. مثال: @executor:scan --path=/Downloads --format=json
- move: نقل ملف. مثال: @executor:move --path=/a.pdf --dest=/Books/
- rename: إعادة تسمية. مثال: @executor:rename --path=/a.pdf --new-name=كتاب.pdf
- report: تقرير عن مجلد. مثال: @executor:report --path=/Books --format=html
عندما يطلب منك المستخدم مهمة (مثل "نظم مجلد التنزيلات")، اشرح له خطتك أولاً، ثم ضع الأوامر في نهاية ردك ليتمكن المستخدم من نسخها وتنفيذها فوراً.
            """.trimIndent()

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("موجّه النظام للمساعدات الذكية (System Prompt)", color = MetallicGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("انسخ هذا التوجيه التأسيسي وأعطه لمساعد الذكاء الاصطناعي الخارجي (مثل ChatGPT أو DeepSeek) لتمكينه من فهم بروتوكول الأوامر وإصدارها بشكل دقيق تماماً.", color = TextGray, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(GlassWhiteMedium, RoundedCornerShape(8.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = promptText,
                            color = TextSilver,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Right
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Button(
                        onClick = {
                            localClipboardManager.setText(AnnotatedString(promptText))
                            Toast.makeText(context, "تم نسخ موجّه المساعد الذكي للحافظة!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "نسخ", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("نسخ موجّه الأوامر للمساعدات الذكية", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // ---------------------------------------------------------------------
        // Build Pack Settings Card
        // ---------------------------------------------------------------------
        item {
            val smartPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
            var scanMode by remember {
                mutableStateOf(smartPrefs.getString("scan_mode", "ACTIVE_PROJECT") ?: "ACTIVE_PROJECT")
            }
            var exportFormat by remember {
                mutableStateOf(smartPrefs.getString("export_format", "PLAIN") ?: "PLAIN")
            }
            var includeNonCode by remember {
                mutableStateOf(smartPrefs.getBoolean("include_non_code", false))
            }
            var includeSubfolders by remember {
                mutableStateOf(smartPrefs.getBoolean("include_subfolders", true))
            }
            var textPackagingMode by remember {
                mutableStateOf(smartPrefs.getString("text_packaging_mode", "bundled") ?: "bundled")
            }
            var convertMdToHtmlOnPack by remember {
                mutableStateOf(smartPrefs.getBoolean("convert_md_to_html_on_pack", false))
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("إعدادات حزمة البناء الذكي", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("تخصيص خيارات تجميع وتصدير ملفات الكود والمشاريع كحزم تصديرية منظمة وموجهة للمساعد الذكي.", color = TextGray, fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Scan Path (Scan Mode)
                    Text("🔍 مسار تجميع ملفات حزمة البناء:", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val scanModesList = listOf(
                            Pair("ACTIVE_PROJECT", "المشروع النشط"),
                            Pair("CODE_ONLY", "مجلد الكود"),
                            Pair("CUSTOM", "مسار مخصص")
                        )
                        scanModesList.forEach { (modeVal, modeLabel) ->
                            val isSelected = scanMode == modeVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .background(
                                        if (isSelected) MetallicGold.copy(alpha = 0.25f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MetallicGold else GlassBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        scanMode = modeVal
                                        smartPrefs.edit().putString("scan_mode", modeVal).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeLabel,
                                    color = if (isSelected) BrightGold else TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Export Format Selector
                    Text("⚙️ صيغة تصدير حزمة البناء التلقائية:", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val formatModesList = listOf(
                            Pair("PLAIN", "نص عادي"),
                            Pair("JSON", "JSON"),
                            Pair("MARKDOWN", "Markdown")
                        )
                        formatModesList.forEach { (formatVal, formatLabel) ->
                            val isSelected = exportFormat == formatVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .background(
                                        if (isSelected) MetallicGold.copy(alpha = 0.25f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MetallicGold else GlassBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        exportFormat = formatVal
                                        smartPrefs.edit().putString("export_format", formatVal).apply()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = formatLabel,
                                    color = if (isSelected) BrightGold else TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Include Non-Code Files Switch
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تضمين الملفات غير البرمجية", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("مثل مستندات النص، ملفات المحاكاة والملتقطات غير البرمجية", color = TextGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = includeNonCode,
                            onCheckedChange = { isChecked ->
                                includeNonCode = isChecked
                                smartPrefs.edit().putBoolean("include_non_code", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. Include Subfolders Switch
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تضمين المجلدات الفرعية", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("تجميع الملفات بشكل تراجعي من كافة المجلدات الداخلية للمشروع", color = TextGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = includeSubfolders,
                            onCheckedChange = { isChecked ->
                                includeSubfolders = isChecked
                                smartPrefs.edit().putBoolean("include_subfolders", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // 5. Select text_packaging_mode
                    Text("📦 نمط تغليف النص المُحدد:", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val packModesList = listOf(
                            Pair("bundled", "مُجمّع"),
                            Pair("smart", "ذكي"),
                            Pair("raw", "خام")
                        )
                        packModesList.forEach { (modeVal, modeLabel) ->
                            val isSelected = textPackagingMode == modeVal
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .background(
                                        if (isSelected) MetallicGold.copy(alpha = 0.25f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) MetallicGold else GlassBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        textPackagingMode = modeVal
                                        smartPrefs.edit().putString("text_packaging_mode", modeVal).apply()
                                    }
                                    .testTag("text_packaging_mode_$modeVal"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeLabel,
                                    color = if (isSelected) BrightGold else TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = GlassBorder.copy(alpha = 0.15f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 6. Convert MD to HTML Switch
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("تحويل Markdown إلى HTML منسق", color = TextSilver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("تحويل أي كتل نصية/Markdown بالتصميم المختار من سمة المستندات تلقائياً", color = TextGray, fontSize = 9.sp)
                        }
                        Switch(
                            checked = convertMdToHtmlOnPack,
                            onCheckedChange = { isChecked ->
                                convertMdToHtmlOnPack = isChecked
                                smartPrefs.edit().putBoolean("convert_md_to_html_on_pack", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SlateBg,
                                checkedTrackColor = MetallicGold,
                                uncheckedThumbColor = TextGray,
                                uncheckedTrackColor = GlassWhite
                            ),
                            modifier = Modifier.testTag("convert_md_to_html_on_pack_switch")
                        )
                    }
                }
            }
        }

        // About / Help Center Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ℹ️ حول التطبيق والخدمات", color = MetallicGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("نظام متكامل ومراقب ذكي متكامل الخدمات للأتمتة البرمجية الكاملة وتنزيل وتنفيذ الكتل وقصاصات الأكواد البرمجية مباشرة.", color = TextGray, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToHelpCenter,
                        modifier = Modifier.fillMaxWidth().height(40.dp).testTag("open_help_center_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold.copy(alpha = 0.15f), contentColor = MetallicGold)
                    ) {
                        Text("📖 فتح مركز المساعدة والتوثيق", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onNavigateToSourceExport,
                        modifier = Modifier.fillMaxWidth().height(40.dp).testTag("open_source_export_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg)
                    ) {
                        Text("📤 تصدير الكود المصدري كحزمة بناء", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showPreviewDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showPreviewDialog = false },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MetallicGold)
                ) {
                    Text("إغلاق المعاينة", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    "👁️ المعاينة الحية للنمط: ${com.example.engine.SmartCaptureEngine.getThemeDisplayName(previewThemeToUse)}",
                    color = TextSilver,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val htmlData = com.example.engine.SmartCaptureEngine.generatePreviewHtml(previewThemeToUse, context)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                        .background(Color.White)
                ) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                webViewClient = android.webkit.WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.defaultTextEncodingName = "utf-8"
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            containerColor = SlateBg,
            textContentColor = TextSilver,
            titleContentColor = TextSilver,
            shape = RoundedCornerShape(16.dp),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth(0.92f)
        )
    }
}

// =====================================================================
// Custom Reusable Layout Containers (GlassCard & general design styles)
// =====================================================================

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(GlassWhite, RoundedCornerShape(24.dp))
            .border(BorderStroke(0.8.dp, GlassBorder), RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

// =====================================================================
// Advanced Directory Browser Dialog & Setup Companion (Arabic layout)
// =====================================================================

@Composable
fun DirectoryBrowserDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    initialPath: String,
    onConfirm: (String) -> Unit
) {
    if (!isOpen) return
    val context = LocalContext.current
    
    var currentPath by remember {
        mutableStateOf(
            if (initialPath.isNotBlank()) File(initialPath).also { it.mkdirs() }
            else File("/storage/emulated/0")
        )
    }
    
    var pathInputText by remember { mutableStateOf(currentPath.absolutePath) }
    var subDirs by remember { mutableStateOf(emptyList<File>()) }
    
    fun loadDirs() {
        val path = currentPath
        if (path.exists() && path.isDirectory) {
            val list = path.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
            subDirs = list.sortedBy { it.name.lowercase() }
        } else {
            subDirs = emptyList()
        }
        pathInputText = currentPath.absolutePath
    }
    
    LaunchedEffect(currentPath) {
        loadDirs()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .background(SlateBg, RoundedCornerShape(24.dp))
                .border(1.2.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = MetallicGold)
                        Text("مستعرض ومعالج أدلة الحفظ", color = TextSilver, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextGray)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Manual path row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = pathInputText,
                        onValueChange = { pathInputText = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                        label = { Text("مسار الدليل النشط", color = TextMuted, fontSize = 10.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedLabelColor = BrightGold,
                            unfocusedLabelColor = TextMuted
                        )
                    )
                    
                    IconButton(
                        onClick = {
                            val f = File(pathInputText)
                            if (f.exists()) {
                                currentPath = f
                            } else {
                                Toast.makeText(context, "المسار المكتوب غير موجود!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.background(GoldGlassBg, RoundedCornerShape(8.dp)).size(38.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "الانتقال للمسار", tint = BrightGold, modifier = Modifier.size(18.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Quick shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val shortcuts = listOf(
                        Triple("الرئيسية", "/storage/emulated/0", Icons.Default.Home),
                        Triple("المستندات", "/storage/emulated/0/Documents", Icons.Default.Menu),
                        Triple("التنزيلات", "/storage/emulated/0/Download", Icons.Default.Menu),
                        Triple("المشاريع", "/storage/emulated/0/BuilderProjects", Icons.Default.Favorite)
                    )
                    
                    shortcuts.forEach { (label, pathStr, icon) ->
                        val selected = currentPath.absolutePath == pathStr
                        Button(
                            onClick = {
                                val folder = File(pathStr)
                                folder.mkdirs()
                                currentPath = folder
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MetallicGold else GoldGlassBg,
                                contentColor = if (selected) SlateBg else TextSilver
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 9.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Directory control header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "محتويات: ${currentPath.name.ifBlank { "الجذر الرئيسي" }}",
                        color = TextSilver,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (currentPath.parentFile != null) {
                        TextButton(
                            onClick = { currentPath = currentPath.parentFile!! },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("⬆️ صعود للأعلى", fontSize = 11.sp, color = BrightGold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Browse Directories container
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        .background(CardSlateBg)
                ) {
                    if (subDirs.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextGray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("لا توجد مجلدات فرعية في هذا المسار", color = TextGray, fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                            items(subDirs) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentPath = folder }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = folder.name,
                                        color = TextSilver,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Divider(color = GlassBorder, thickness = 0.5.dp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Action Buttons at bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            if (clipboard != null && clipboard.hasPrimaryClip()) {
                                val clipStr = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (clipStr.isNotBlank() && clipStr.startsWith("/")) {
                                    val f = File(clipStr)
                                    f.mkdirs()
                                    if (f.exists() && f.isDirectory) {
                                        currentPath = f
                                        Toast.makeText(context, "تم جلب وحزم المسار من الحافظة!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("لصق مسار من الحافظة", fontSize = 10.sp)
                    }
                    
                    Button(
                        onClick = {
                            val targetPath = pathInputText.trim().ifBlank { currentPath.absolutePath }
                            val resolvedFile = if (targetPath == "/") File("/storage/emulated/0") else File(targetPath)
                            try {
                                resolvedFile.mkdirs()
                            } catch (e: Exception) {}
                            if (resolvedFile.exists()) {
                                onConfirm(resolvedFile.absolutePath)
                            } else {
                                onConfirm(currentPath.absolutePath)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("تأكيد المجلد الافتراضي", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


// =====================================================================
// Modern Automation & Permissions Unified Dashboard (Arabic representation)
// =====================================================================

@Composable
fun PermissionsDashboardDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    viewModel: MainViewModel
) {
    if (!isOpen) return
    val context = LocalContext.current
    
    // States
    var hasNotify by remember { mutableStateOf(true) }
    var hasAccessibility by remember { mutableStateOf(false) }
    var hasAllFiles by remember { mutableStateOf(false) }
    var hasBatteryIgnore by remember { mutableStateOf(false) }
    var hasOverlay by remember { mutableStateOf(false) }
    var hasImeEnabled by remember { mutableStateOf(false) }
    var isBubbleRunning by remember { mutableStateOf(false) }
    var isGoldenBubbleRunning by remember { mutableStateOf(false) }
    
    fun refreshStates() {
        hasNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        
        hasAccessibility = isAccessibilityServiceEnabled(context)
        
        hasAllFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else true
        
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        hasBatteryIgnore = pm.isIgnoringBatteryOptimizations(context.packageName)
        
        hasOverlay = Settings.canDrawOverlays(context)

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        hasImeEnabled = imm.enabledInputMethodList.any { it.packageName == context.packageName }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        isBubbleRunning = activityManager?.getRunningServices(Integer.MAX_VALUE)?.any {
            it.service.className == "com.example.service.BubbleService"
        } ?: false

        isGoldenBubbleRunning = activityManager?.getRunningServices(Integer.MAX_VALUE)?.any {
            it.service.className == "com.example.service.GoldenBubbleService"
        } ?: false
    }
    
    LaunchedEffect(Unit) {
        refreshStates()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .background(SlateBg, RoundedCornerShape(26.dp))
                .border(1.2.dp, Brush.linearGradient(listOf(MetallicGold, BrightGold)), RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = BrightGold, modifier = Modifier.size(24.dp))
                        Text(
                            text = "مركز صلاحيات الأتمتة الكاملة",
                            color = TextSilver,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextGray)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Text(
                    text = "لجعل التطبيق يعمل في الخلفية بصمت تام ويستعرض وينسخ الملفات فور تكرار التوجيهات، قم بتمكين الصلاحيات التالية لتشغيل الأتمتة الكاملة بأمان:",
                    color = TextGray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Permissions lists
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 0. Smart Keyboard (Android 10+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        item {
                            PermissionItemCard(
                                title = "لوحة مفاتيح الأتمتة الذكية (Android 10+)",
                                description = "الحل المعتمد والآمن لتجاوز قيود أندرويد الحديثة بالخلفية. تفاعلية بالكامل، وتعالج التوجيهات فور نسخها.",
                                isGranted = hasImeEnabled,
                                onGrant = {
                                    Toast.makeText(context, "يرجى اختيار وتفعيل 'لوحة مفاتيح الأتمتة الذكية' في مدير لوحات المفاتيح.", Toast.LENGTH_LONG).show()
                                    val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    // 1. Accessibility Service
                    item {
                        PermissionItemCard(
                            title = "خدمة إمكانية الوصول لمراقبة الحافظة",
                            description = "تمنع قيود أندرويد قراءة الحافظة في الخلفية. تفعيل هذه الخدمة يمكن التطبيق من التقاط وتطبيق البادئات (@builder, @executor) فوراً بمجرد نسخها في أي تطبيق وبكل خصوصية وأمان.",
                            isGranted = hasAccessibility,
                            onGrant = {
                                Toast.makeText(context, "ابحث عن 'مساعد أتمتة الحافظة (المساعد الذكي الذهبي)' وقم بتفعيله", Toast.LENGTH_LONG).show()
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            }
                        )
                    }
                    
                    // 2. Full Storage Access (MANAGE_EXTERNAL_STORAGE)
                    item {
                        PermissionItemCard(
                            title = "الوصول الكامل إلى جميع الملفات والأشجار",
                            description = "مهم وحيوي لوضع وإنشاء المشاريع ومجلد العمل في أي مكان مخصص على جهازك (مثل /storage/emulated/0/BuilderProjects) دون حظر.",
                            isGranted = hasAllFiles,
                            onGrant = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                } else {
                                    Toast.makeText(context, "ممنوح تلقائياً على هذا النظام", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    
                    // 3. Ignore Battery Optimization
                    item {
                        PermissionItemCard(
                            title = "استثناء التطبيق من تحسين استهلاك البطارية",
                            description = "تجنب إغلاق خدمات المراقبة الذكية من قبل النظام عند سكون أو قفل شاشة الهاتف.",
                            isGranted = hasBatteryIgnore,
                            onGrant = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (x: Exception) {}
                                }
                            }
                        )
                    }
                    
                    // 4. Draw Over Other Apps (SYSTEM_ALERT_WINDOW)
                    item {
                        PermissionItemCard(
                            title = "الظهور فوق التطبيقات (الكرة العائمة الذكية)",
                            description = "ضروري لإظهار الكرة العائمة التفاعلية فوق كل التطبيقات لإنشاء وتوليد تقارير شجرية سريعة بنقرة.",
                            isGranted = hasOverlay,
                            onGrant = {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            }
                        )
                    }
                    
                    if (hasOverlay) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = GlassWhite.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "الكرة العائمة الذكية",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isBubbleRunning) "🟢 نشطة وظاهرة" else "🔴 غير مفعلة",
                                            color = if (isBubbleRunning) BrightGold else Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    Text(
                                        text = "الكرة العائمة تتيح لك تشغيل لوحة التحكم السريعة وعرض الملفات فورياً فوق أي تطبيق آخر.",
                                        color = TextSilver,
                                        fontSize = 11.sp
                                    )
                                    
                                    Button(
                                        onClick = {
                                            try {
                                                val serviceIntent = Intent(context, com.example.service.BubbleService::class.java)
                                                if (isBubbleRunning) {
                                                    serviceIntent.action = "STOP"
                                                    context.stopService(serviceIntent)
                                                    Toast.makeText(context, "تم إيقاف خدمة الكرة العائمة.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    context.startService(serviceIntent)
                                                    Toast.makeText(context, "تم تشغيل الكرة العائمة بنجاح!", Toast.LENGTH_SHORT).show()
                                                }
                                                refreshStates()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "فشل التحكم بالخدمة: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isBubbleRunning) Color(0xFFDC2626) else MetallicGold,
                                            contentColor = if (isBubbleRunning) Color.White else SlateBg
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isBubbleRunning) "إغلاق وتعطيل الكرة العائمة" else "تشغيل وتفعيل الكرة العائمة الآن",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.2.dp, Brush.linearGradient(listOf(MetallicGold, BrightGold)))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "👑 الفقاعة الذهبية V2 (جديد)",
                                            color = BrightGold,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isGoldenBubbleRunning) "🟢 نشطة وظاهرة" else "🔴 غير مفعلة",
                                            color = if (isGoldenBubbleRunning) BrightGold else Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    
                                    Text(
                                        text = "النسخة الذهبية V2 فائقة الاستقرار والسرعة. تتيح لك المراقبة التلقائية لقيم الحافظة ومعالجتها فورياً وتوليف الملفات فوق أي تطبيق بدون كيبورد.",
                                        color = TextSilver,
                                        fontSize = 11.sp
                                    )
                                    
                                    Button(
                                        onClick = {
                                            try {
                                                val serviceIntent = Intent(context, com.example.service.GoldenBubbleService::class.java)
                                                if (isGoldenBubbleRunning) {
                                                    serviceIntent.action = "STOP"
                                                    context.stopService(serviceIntent)
                                                    Toast.makeText(context, "تم إيقاف خدمة الفقاعة الذهبية.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    context.startService(serviceIntent)
                                                    Toast.makeText(context, "تم تشغيل الفقاعة الذهبية V2 بنجاح!", Toast.LENGTH_SHORT).show()
                                                }
                                                refreshStates()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "فشل التحكم بالخدمة: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isGoldenBubbleRunning) Color(0xFFDC2626) else BrightGold,
                                            contentColor = if (isGoldenBubbleRunning) Color.White else SlateBg
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (isGoldenBubbleRunning) "إيقاف الفقاعة الذهبية" else "تشغيل الفقاعة الذهبية V2 الآن",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // 5. Notifications
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        item {
                            PermissionItemCard(
                                title = "صلاحية الإشعارات اليدوية والدائمة",
                                description = "الحفاظ على بقاء محاكي الخلفية نشطاً ومستقراً ومراقباً عبر النظام باستمرار.",
                                isGranted = hasNotify,
                                onGrant = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { refreshStates() },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassWhite, contentColor = TextSilver),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("طالع التغييرات", fontSize = 11.sp)
                    }
                    
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("تم الإعداد تماماً", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItemCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSilver,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGranted) EmeraldGlow.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (isGranted) EmeraldGlow else Color.Red)
                ) {
                    Text(
                        text = if (isGranted) "مفعّل" else "مطلوب تفعيل",
                        color = if (isGranted) EmeraldGlow else Color.Red,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = description,
                color = TextGray,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
            
            if (!isGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = BrightGold),
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("انتقل للإعداد والتفعيل ↗", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun findExistingFilePath(context: Context, text: String): String? {
    try {
        val currentProjDir = com.example.engine.ProjectContextManager.getCurrentProjectDir(context)
        val baseDir = com.example.engine.ProjectContextManager.getBaseDir(context)

        // 1. Look for absolute paths matching general patterns starting with /
        val absRegex = Regex("""(/[a-zA-Z0-9_.\-]+)+?\.[a-zA-Z0-9]+""")
        val matches = absRegex.findAll(text)
        for (m in matches) {
            val path = m.value
            val file = File(path)
            if (file.exists() && file.isFile) return file.absolutePath
        }

        // 2. Split words to find matches
        text.split(Regex("[\\s\"'\\n]")).forEach { word ->
            val clean = word.trim().trim('"', '\'', ',', '[', ']', '(', ')')
            if (clean.isNotBlank() && clean.contains(".")) {
                // Check if it's a direct absolute path
                val fileAbs = File(clean)
                if (fileAbs.exists() && fileAbs.isFile) {
                    return fileAbs.absolutePath
                }

                // Check relative to project dir
                val fileProj = File(currentProjDir, clean)
                if (fileProj.exists() && fileProj.isFile) {
                    return fileProj.absolutePath
                }

                // Check relative to base dir
                val fileBase = File(baseDir, clean)
                if (fileBase.exists() && fileBase.isFile) {
                    return fileBase.absolutePath
                }
            }
        }
    } catch (e: Exception) {
        // Safe check
    }
    return null
}

