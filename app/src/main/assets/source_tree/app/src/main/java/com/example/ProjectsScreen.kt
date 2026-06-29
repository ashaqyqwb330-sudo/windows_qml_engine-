package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.*
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var projectsList by remember { mutableStateOf(ProjectManager.getAllProjects(context)) }
    var activeProjectPath by remember { mutableStateOf(ProjectContextManager.getCurrentProjectPath(context)) }
    
    // Dialog and Screen states
    var showImportEditor by remember { mutableStateOf(false) }
    var clipboardImportCandidate by remember { mutableStateOf<String?>(null) }
    var customTemplateJsonInput by remember { mutableStateOf("") }
    
    // Fetch clipboard on launch to offer auto import
    LaunchedEffect(Unit) {
        val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = systemClipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            val trimText = text.trim()
            if (trimText.startsWith("{") && (trimText.contains("template_version") || trimText.contains("templateVersion"))) {
                val parsed = TemplateParser.parse(trimText)
                if (parsed.isSuccess) {
                    clipboardImportCandidate = trimText
                }
            }
        }
    }

    // Refresh listener for external updates (like from the Golden Bubble)
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
                if (intent?.action == "com.example.ACTION_REFRESH_PROJECTS") {
                    projectsList = ProjectManager.getAllProjects(context)
                    activeProjectPath = ProjectContextManager.getCurrentProjectPath(context)
                }
            }
        }
        val filter = android.content.IntentFilter("com.example.ACTION_REFRESH_PROJECTS")
        context.registerReceiver(receiver, filter)
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "📁 مكتبة المشاريع الذكية",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            val activeProj = ProjectContextManager.getCurrentProjectPath(context)
                            val projectDir = ProjectContextManager.getProjectDir(activeProj, context)
                            val configFile = File(projectDir, "project_config.json")
                            if (configFile.exists()) {
                                try {
                                    val json = configFile.readText()
                                    clipboardManager.setText(AnnotatedString(json))
                                    Toast.makeText(context, "✅ تم تصدير هيكل المشروع الحالي للحافظة!", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "عذراً، فشل التصدير: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Fallback export for default project
                                val fallbackJson = """
                                {
                                  "projectName": "$activeProj",
                                  "template_version": 1,
                                  "folders": [
                                    {
                                      "name": "محفوظات",
                                      "path": "saved",
                                      "fileTypes": ["text"],
                                      "keywords": []
                                    }
                                  ]
                                }
                                """.trimIndent()
                                clipboardManager.setText(AnnotatedString(fallbackJson))
                                Toast.makeText(context, "✅ تم تصدير هيكل المشروع الافتراضي للحافظة!", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.testTag("export_active_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "تصدير المشروع النشط", tint = MetallicGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateBg)
            )
        },
        containerColor = SlateBg
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clipboard template candidate notification card
            clipboardImportCandidate?.let { jsonStr ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.2.dp, Brush.linearGradient(listOf(MetallicGold, BrightGold)), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = GoldGlassBg),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = BrightGold, modifier = Modifier.size(18.dp))
                                    val tempName = try { JSONObject(jsonStr).getString("projectName") } catch (e: Exception) { "قالب ذكي" }
                                    Text("تم كشف قالب مشروع: $tempName", color = BrightGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                IconButton(onClick = { clipboardImportCandidate = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "تجاهل", tint = TextGray, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                "تحتوي الحافظة على كود قالب مشروع ذكي مهيأ، هل ترغب في استيراده وتفعيله فوراً؟",
                                color = TextSilver,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val parsed = TemplateParser.parse(jsonStr)
                                        if (parsed.isSuccess) {
                                            val tObj = parsed.getOrThrow()
                                            val buildRes = ProjectBuilder.build(tObj, ProjectContextManager.getBaseDir(context).absolutePath)
                                            if (buildRes.isSuccess) {
                                                val resPath = buildRes.getOrThrow()
                                                ProjectManager.addProject(context, resPath, tObj.projectName)
                                                ProjectManager.setActiveProject(context, resPath)
                                                projectsList = ProjectManager.getAllProjects(context)
                                                activeProjectPath = resPath
                                                Toast.makeText(context, "✅ تم استيراد وتفعيل قالب: ${tObj.projectName}", Toast.LENGTH_LONG).show()
                                                clipboardImportCandidate = null
                                            } else {
                                                Toast.makeText(context, "فشل إنشاء المشروع: ${buildRes.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "فشل تحليل القالب", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGlow, contentColor = Color.Black),
                                    modifier = Modifier.weight(1f).height(30.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("برق السحاب (استيراد دافئ)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        customTemplateJsonInput = jsonStr
                                        showImportEditor = true
                                        clipboardImportCandidate = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                    modifier = Modifier.weight(1f).height(30.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("معاينة وتعديل رصين", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions Block (Top buttons)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            customTemplateJsonInput = ""
                            showImportEditor = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("import_template_action_btn"),
                        shape = RoundedCornerShape(8.dp),
                        elevation = ButtonDefaults.buttonElevation(4.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = SlateBg)
                            Text("استيراد قالب ذكي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    
                    Button(
                        onClick = {
                            // Automatically paste design pattern snippet to clipboard
                            val templateSample = """
                            {
                              "projectName": "سياق_التطوير_التكنولوجي",
                              "template_version": 1,
                              "folders": [
                                {
                                  "name": "مستندات الهندسة",
                                  "path": "engineering_docs",
                                  "fileTypes": ["txt", "md"],
                                  "keywords": ["هندسة", "معمارية", "تطوير", "قواعد_البيانات", "كود"]
                                },
                                {
                                  "name": "الواجهات البرمجية",
                                  "path": "api_specifications",
                                  "fileTypes": ["json", "yaml"],
                                  "keywords": ["واجهة", "api", "endpoint", "توثيق", "سيرفر"]
                                }
                              ]
                            }
                            """.trimIndent()
                            clipboardManager.setText(AnnotatedString(templateSample))
                            Toast.makeText(context, "📋 تم وضع قالب نموذجي في حافظتك لاستعراضه!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardSlateBg, contentColor = MetallicGold),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MetallicGold)
                            Text("نسخ نموذج تجريبي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Category title
            item {
                Text(
                    "المشاريع المتاحة والمستوردة حالياً:",
                    color = TextSilver,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Projects List
            items(projectsList) { (path, name) ->
                val isActive = activeProjectPath == path || activeProjectPath.endsWith(path)
                
                // Read subfolders count dynamically if exists
                val projectDir = ProjectContextManager.getProjectDir(path, context)
                val subfoldersCount = if (projectDir.exists()) {
                    projectDir.listFiles { _, filename -> 
                        File(projectDir, filename).isDirectory && !filename.startsWith(".")
                    }?.size ?: 0
                } else {
                    0
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (isActive) MetallicGold else GlassBorder,
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) GoldGlassBg else CardSlateBg
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Menu,
                                    contentDescription = null,
                                    tint = if (isActive) BrightGold else TextSilver,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = name,
                                    color = if (isActive) BrightGold else TextSilver,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                            
                            if (isActive) {
                                Surface(
                                    color = BrightGold.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        "نشط ومفعل",
                                        color = BrightGold,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Project Location Details
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("المسار:", color = TextMuted, fontSize = 10.sp)
                                Text(
                                    path,
                                    color = TextSilver,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("الهيكل الداخلي:", color = TextMuted, fontSize = 10.sp)
                                Text(
                                    "فولدرات مصنفة: $subfoldersCount مجلد محمي",
                                    color = TextSilver,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Divider line
                        Divider(color = GlassBorder.copy(alpha = 0.5f))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Activate Button (if not already active)
                            if (!isActive) {
                                Button(
                                    onClick = {
                                        ProjectManager.setActiveProject(context, path)
                                        activeProjectPath = path
                                        Toast.makeText(context, "🔄 تم تفعيل سياق المشروع: $name", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .testTag("activate_proj_${path.hashCode()}"),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text("تفعيل السياق", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Export template configuration
                                IconButton(
                                    onClick = {
                                        val configFile = File(projectDir, "project_config.json")
                                        if (configFile.exists()) {
                                            try {
                                                val json = configFile.readText()
                                                clipboardManager.setText(AnnotatedString(json))
                                                Toast.makeText(context, "✅ تم نسخ كود القالب للحافظة!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "فشل نسخ القالب: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            // Fallback minimal json construction
                                            val fallback = """
                                            {
                                              "projectName": "$name",
                                              "template_version": 1,
                                              "folders": []
                                            }
                                            """.trimIndent()
                                            clipboardManager.setText(AnnotatedString(fallback))
                                            Toast.makeText(context, "✅ تم نسخ القالب التقريبي للحافظة!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(GlassWhite, CircleShape)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "تصدير القالب", tint = MetallicGold, modifier = Modifier.size(13.dp))
                                }

                                // Delete (hide from current list, no actual delete of file)
                                if (path != "SmartInbox") {
                                    IconButton(
                                        onClick = {
                                            ProjectManager.removeProject(context, path)
                                            projectsList = ProjectManager.getAllProjects(context)
                                            if (isActive) {
                                                ProjectManager.setActiveProject(context, "SmartInbox")
                                                activeProjectPath = "SmartInbox"
                                            }
                                            Toast.makeText(context, "🗑️ تم إزالة المشروع من قائمة المكتبة", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(DangerRed.copy(alpha = 0.15f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف المشروع", tint = DangerRed, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Task 6: Visual template screen / Dialog view for review and edits
    if (showImportEditor) {
        ImportTemplateScreen(
            viewModel = viewModel,
            initialJson = customTemplateJsonInput,
            onDismiss = { showImportEditor = false },
            onImportSuccess = { path, name ->
                projectsList = ProjectManager.getAllProjects(context)
                activeProjectPath = path
                showImportEditor = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTemplateScreen(
    viewModel: MainViewModel,
    initialJson: String,
    onDismiss: () -> Unit,
    onImportSuccess: (String, String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var jsonText by remember { mutableStateOf(initialJson) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var previewTemplate by remember { mutableStateOf<ProjectTemplate?>(null) }

    // Toggle operational modes: Visual vs JSON
    var isVisualMode by remember { mutableStateOf(true) }

    // Editable settings backed by template design values
    var editingProjectName by remember { mutableStateOf("مشروع جديد") }
    var templateVersion by remember { mutableStateOf("1") }
    val folderTemplatesList = remember { mutableStateListOf<FolderTemplate>() }
    var selectedFolderIndex by remember { mutableStateOf(0) }

    // Synchronize or parse on initialization
    LaunchedEffect(Unit) {
        if (jsonText.isNotBlank()) {
            val parsed = TemplateParser.parse(jsonText)
            if (parsed.isSuccess) {
                val t = parsed.getOrThrow()
                previewTemplate = t
                editingProjectName = t.projectName
                templateVersion = t.templateVersion
                folderTemplatesList.clear()
                folderTemplatesList.addAll(t.folders)
            } else {
                parseError = parsed.exceptionOrNull()?.message
                // Default fallback so Visual mode starts with something readable
                folderTemplatesList.add(FolderTemplate("المجلد الرئيسي", "main_dir", listOf("text"), listOf("الرئيسية", "قاعدة")))
            }
        } else {
            // Default initial state if clipboard was blank
            folderTemplatesList.add(FolderTemplate("محفوظات", "saved", listOf("text"), listOf("حفظ", "سجل")))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(SlateBg)
                .border(1.2.dp, GlassBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = SlateBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header of dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🛠️ محرر ومعاين القوالب الذكي",
                        color = BrightGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    // Mode Switcher Buttons
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardSlateBg)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isVisualMode) MetallicGold else Color.Transparent)
                                .clickable {
                                    // Generate current json from visual state
                                    val currentTemplate = ProjectTemplate(
                                        projectName = editingProjectName,
                                        templateVersion = templateVersion,
                                        folders = folderTemplatesList.toList()
                                    )
                                    val generatedJson = JSONObject().apply {
                                        put("projectName", currentTemplate.projectName)
                                        put("template_version", currentTemplate.templateVersion)
                                        val fArr = JSONArray()
                                        for (f in currentTemplate.folders) {
                                            fArr.put(JSONObject().apply {
                                                put("name", f.name)
                                                put("path", f.path)
                                                put("fileTypes", JSONArray(f.fileTypes))
                                                put("keywords", JSONArray(f.keywords))
                                            })
                                        }
                                        put("folders", fArr)
                                    }.toString(4)
                                    jsonText = generatedJson
                                    isVisualMode = false
                                }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("وضع JSON", color = if (!isVisualMode) SlateBg else TextSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isVisualMode) MetallicGold else Color.Transparent)
                                .clickable {
                                    // Parse current JSON to visual state
                                    if (jsonText.isNotBlank()) {
                                        val parsed = TemplateParser.parse(jsonText)
                                        if (parsed.isSuccess) {
                                            val t = parsed.getOrThrow()
                                            previewTemplate = t
                                            editingProjectName = t.projectName
                                            templateVersion = t.templateVersion
                                            folderTemplatesList.clear()
                                            folderTemplatesList.addAll(t.folders)
                                            parseError = null
                                            selectedFolderIndex = 0
                                        } else {
                                            parseError = "تحذير: لا يمكن تحديث العرض بصيغة JSON غير صالحة: ${parsed.exceptionOrNull()?.message}"
                                        }
                                    }
                                    isVisualMode = true
                                }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👁️ وضع بصري", color = if (isVisualMode) SlateBg else TextSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "اغلاق", tint = TextSilver)
                    }
                }

                Divider(color = GlassBorder)

                // Render based on toggled mode
                if (!isVisualMode) {
                    // JSON Mode - Code Editor input
                    Text(
                        "قم بلصق أو تعديل محتوى الكود الخاص بالقالب بتنسيق JSON مباشرة:",
                        color = TextSilver,
                        fontSize = 11.sp
                    )

                    com.example.ui.components.CodeEditor(
                        value = jsonText,
                        onValueChange = {
                            jsonText = it
                            parseError = null
                        },
                        language = "json",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("json_template_editor_area"),
                        placeholder = "{ \"projectName\": \"...\", \"folders\": [...] }"
                    )

                    parseError?.let { err ->
                        Text(
                            "❌ خطأ: $err",
                            color = DangerRed,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (jsonText.trim().isEmpty()) {
                                    parseError = "يرجى لصق نص القالب أولاً!"
                                    return@Button
                                }
                                val parsed = TemplateParser.parse(jsonText)
                                if (parsed.isSuccess) {
                                    val t = parsed.getOrThrow()
                                    previewTemplate = t
                                    editingProjectName = t.projectName
                                    templateVersion = t.templateVersion
                                    folderTemplatesList.clear()
                                    folderTemplatesList.addAll(t.folders)
                                    parseError = null
                                    selectedFolderIndex = 0
                                    isVisualMode = true
                                    Toast.makeText(context, "✅ تم التحليل والانتقال للوضع البصري!", Toast.LENGTH_SHORT).show()
                                } else {
                                    parseError = parsed.exceptionOrNull()?.message
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("parse_template_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("تحليل وتوليد الهيكل بصرياً", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                } else {
                    // 👁️ Visual Mode with Split Screen (Left tree, Right controls)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Project General Name Editor
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GlassBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = editingProjectName,
                                    onValueChange = { editingProjectName = it },
                                    label = { Text("اسم المشروع الهيكلي", fontSize = 10.sp) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("edit_projectName_field"),
                                    textStyle = TextStyle(color = TextSilver, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MetallicGold,
                                        unfocusedBorderColor = GlassBorder,
                                        focusedLabelColor = MetallicGold
                                    ),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = templateVersion,
                                    onValueChange = { templateVersion = it },
                                    label = { Text("الإصدار", fontSize = 10.sp) },
                                    modifier = Modifier.width(70.dp),
                                    textStyle = TextStyle(color = TextSilver, fontSize = 12.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MetallicGold,
                                        unfocusedBorderColor = GlassBorder,
                                        focusedLabelColor = MetallicGold
                                    ),
                                    singleLine = true
                                )
                            }
                        }

                        // Split pane: Left (Folders Tree) / Right (Detail Editor)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left Partition: Folders Tree
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                colors = CardDefaults.cardColors(containerColor = CardSlateBg.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, GlassBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📁 شجرة المجلدات:", color = MetallicGold, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        IconButton(
                                            onClick = { viewModel.openTreeDocDashboard(".") },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("📊", fontSize = 12.sp)
                                        }
                                        
                                        // [+ مجلد جديد] button inside Left Tree
                                        Button(
                                            onClick = {
                                                val newFolder = FolderTemplate(
                                                    name = "مجلد جديد ${folderTemplatesList.size + 1}",
                                                    path = "new_folder_${folderTemplatesList.size + 1}",
                                                    fileTypes = listOf("text"),
                                                    keywords = listOf("مثال")
                                                )
                                                folderTemplatesList.add(newFolder)
                                                selectedFolderIndex = folderTemplatesList.size - 1
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.height(24.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = SlateBg, modifier = Modifier.size(12.dp))
                                                Text("جديد", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Divider(color = GlassBorder.copy(alpha = 0.5f))

                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(folderTemplatesList.size) { index ->
                                            val folder = folderTemplatesList[index]
                                            val isSelected = selectedFolderIndex == index

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) GoldGlassBg else Color.Transparent)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) MetallicGold else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { selectedFolderIndex = index }
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("📁", fontSize = 14.sp)
                                                    Text(
                                                        text = folder.name,
                                                        color = if (isSelected) BrightGold else TextSilver,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 11.sp,
                                                        maxLines = 1
                                                    )
                                                }

                                                // Inline delete icon for quick removal
                                                if (folderTemplatesList.size > 1) {
                                                    IconButton(
                                                        onClick = {
                                                            folderTemplatesList.removeAt(index)
                                                            if (selectedFolderIndex >= folderTemplatesList.size) {
                                                                selectedFolderIndex = folderTemplatesList.size - 1
                                                            }
                                                        },
                                                        modifier = Modifier.size(18.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "حذف المجلد", tint = DangerRed.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Right Partition: Folder Attributes Editor
                            Card(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight(),
                                colors = CardDefaults.cardColors(containerColor = CardSlateBg),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, GlassBorder)
                            ) {
                                if (selectedFolderIndex in folderTemplatesList.indices) {
                                    val currentFolder = folderTemplatesList[selectedFolderIndex]

                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("⚙️ لوحة التحرير والتصنيف:", color = MetallicGold, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                        Divider(color = GlassBorder.copy(alpha = 0.5f))

                                        // 1. Arabic Name Editor
                                        OutlinedTextField(
                                            value = currentFolder.name,
                                            onValueChange = { newVal ->
                                                folderTemplatesList[selectedFolderIndex] = currentFolder.copy(name = newVal)
                                            },
                                            label = { Text("الاسم العربي للمجلد", fontSize = 9.sp) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MetallicGold,
                                                unfocusedBorderColor = GlassBorder,
                                                focusedLabelColor = MetallicGold
                                            ),
                                            textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // 2. English Path Editor
                                        OutlinedTextField(
                                            value = currentFolder.path,
                                            onValueChange = { newVal ->
                                                folderTemplatesList[selectedFolderIndex] = currentFolder.copy(path = newVal)
                                            },
                                            label = { Text("المسار الإنجليزي للملفات", fontSize = 9.sp) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = MetallicGold,
                                                unfocusedBorderColor = GlassBorder,
                                                focusedLabelColor = MetallicGold
                                            ),
                                            textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // 3. Folder Types dropdown selector
                                        var dropdownExpanded by remember { mutableStateOf(false) }
                                        val availableTypes = listOf("text", "markdown", "json", "yaml", "html", "kotlin")

                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSilver),
                                                border = BorderStroke(1.dp, GlassBorder),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "أنواع الملفات: ${currentFolder.fileTypes.joinToString()}",
                                                        fontSize = 10.sp,
                                                        maxLines = 1
                                                    )
                                                    Text("▾", fontSize = 12.sp, color = MetallicGold)
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.background(CardSlateBg).border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                            ) {
                                                availableTypes.forEach { type ->
                                                    val isSelected = currentFolder.fileTypes.contains(type)
                                                    DropdownMenuItem(
                                                        text = {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Checkbox(
                                                                    checked = isSelected,
                                                                    onCheckedChange = null,
                                                                    colors = CheckboxDefaults.colors(checkedColor = BrightGold)
                                                                )
                                                                Text(type, color = TextSilver, fontSize = 11.sp)
                                                            }
                                                        },
                                                        onClick = {
                                                            val updatedList = if (isSelected) {
                                                                currentFolder.fileTypes - type
                                                            } else {
                                                                (currentFolder.fileTypes + type).distinct()
                                                            }
                                                            folderTemplatesList[selectedFolderIndex] = currentFolder.copy(fileTypes = updatedList)
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        // 4. Keyword Add Field & Chips
                                        var keywordInput by remember { mutableStateOf("") }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = keywordInput,
                                                onValueChange = { keywordInput = it },
                                                label = { Text("أضف كلمة دالة", fontSize = 9.sp) },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = MetallicGold,
                                                    unfocusedBorderColor = GlassBorder,
                                                    focusedLabelColor = MetallicGold
                                                ),
                                                textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )

                                            IconButton(
                                                onClick = {
                                                    if (keywordInput.isNotBlank()) {
                                                        val clean = keywordInput.trim()
                                                        val updatedKws = (currentFolder.keywords + clean).distinct()
                                                        folderTemplatesList[selectedFolderIndex] = currentFolder.copy(keywords = updatedKws)
                                                        keywordInput = ""
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(GlassWhite, RoundedCornerShape(4.dp))
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "اضافة", tint = BrightGold, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        // Scrollable horizontal Row of Chips
                                        Text("مفاتيح الفرز النشطة (انقر للإزالة):", color = TextMuted, fontSize = 9.sp)
                                        androidx.compose.foundation.lazy.LazyRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            items(currentFolder.keywords.size) { kwIdx ->
                                                val kw = currentFolder.keywords[kwIdx]
                                                SuggestionChip(
                                                    onClick = {
                                                        val remaining = currentFolder.keywords - kw
                                                        folderTemplatesList[selectedFolderIndex] = currentFolder.copy(keywords = remaining)
                                                    },
                                                    label = { Text(kw, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                                        containerColor = GoldGlassBg,
                                                        labelColor = BrightGold
                                                    ),
                                                    border = BorderStroke(0.8.dp, MetallicGold.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("يرجى تحديد مجلد للبدء في تحريره", color = TextGray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Combined Save Action row under visual mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (editingProjectName.trim().isEmpty()) {
                                    Toast.makeText(context, "الرجاء تحديد اسم للمشروع!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val finalTemplate = ProjectTemplate(
                                    projectName = editingProjectName,
                                    templateVersion = templateVersion,
                                    folders = folderTemplatesList.toList()
                                )
                                val buildRes = ProjectBuilder.build(finalTemplate, ProjectContextManager.getBaseDir(context).absolutePath)
                                if (buildRes.isSuccess) {
                                    val resPath = buildRes.getOrThrow()
                                    ProjectManager.addProject(context, resPath, finalTemplate.projectName)
                                    ProjectManager.setActiveProject(context, resPath)
                                    onImportSuccess(resPath, finalTemplate.projectName)
                                    Toast.makeText(context, "🎉 تم بناء وحفظ وتفعيل المشروع: ${finalTemplate.projectName} بنجاح!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "❌ فشل بناء المشروع: ${buildRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("save_and_build_project_btn")
                        ) {
                            Text("💾 بناء وتفعيل وحفظ القالب بصرياً", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
