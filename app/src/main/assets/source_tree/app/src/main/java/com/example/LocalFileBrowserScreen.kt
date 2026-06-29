package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.db.FileEntity
import com.example.db.LogEntity
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ويدجت متصفح ومعالج الملفات المحلية للمطور
 * يسمح للمستخدم بتصفح الملفات من ذاكرة الجهاز، اختيارها، ومعاينتها،
 * ثم إرسالها ومعالجتها تلقائياً بواسطة محرك البناء الذكي (Builder Engine).
 */
@Composable
fun LocalFileBrowserWidget(
    viewModel: MainViewModel,
    onLoadToEditor: (String) -> Unit
) {
    val context = LocalContext.current
    var showBrowserDialog by remember { mutableStateOf(false) }
    var lastSelectedFileStr by remember { mutableStateOf("") }
    
    val sharedPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
    
    LaunchedEffect(Unit) {
        lastSelectedFileStr = sharedPrefs.getString("browser_last_selected_file_path", "") ?: ""
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local_file_browser_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📂", fontSize = 16.sp)
                    Text(
                        text = "محرك متصفح ومعالج الملفات (Local File Browser)",
                        color = BrightGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldGlassBg,
                    modifier = Modifier.border(0.5.dp, MetallicGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "جديد",
                        color = BrightGold,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "افتح أي ملف كود برمجي أو توجيهات محلي من ذاكرة الجهاز لمعاينته وتشغيله بواسطة محرك البناء الذكي Builder Engine مباشرة.",
                color = TextGray,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            // Last selected info
            if (lastSelectedFileStr.isNotEmpty()) {
                val lastFile = File(lastSelectedFileStr)
                if (lastFile.exists()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassBlack, RoundedCornerShape(10.dp))
                            .border(0.5.dp, GlassBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "الملف النشط الأخير:",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                            Text(
                                text = lastFile.name,
                                color = TextSilver,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Quick Load button
                            Button(
                                onClick = {
                                    try {
                                        val content = lastFile.readText()
                                        onLoadToEditor(content)
                                        Toast.makeText(context, "📥 تم تحميل محتوى ${lastFile.name} في المحرر!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "فشل تحميل الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldGlassBg, contentColor = MetallicGold),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("تحميل للمحرر", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Primary Launch button
            Button(
                onClick = {
                    showBrowserDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("launch_file_browser_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("فتح متصفح الملفات والتشغيل الذكي", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    // Modal UI File Browser Dialog
    if (showBrowserDialog) {
        LocalFileBrowserDialog(
            viewModel = viewModel,
            onDismiss = { showBrowserDialog = false },
            onLoadToEditor = { text ->
                onLoadToEditor(text)
                showBrowserDialog = false
            },
            onFileProcessedSuccessfully = { filePath ->
                lastSelectedFileStr = filePath
                sharedPrefs.edit().putString("browser_last_selected_file_path", filePath).apply()
            }
        )
    }
}

/**
 * متصفح ملفات تفاعلي بالكامل على هيئة حوار راقي ومميز
 */
@Composable
fun LocalFileBrowserDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onLoadToEditor: (String) -> Unit,
    onFileProcessedSuccessfully: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val browserCurrentPath = viewModel.currentBrowserPath.collectAsState().value
    val browserFiles = viewModel.browserFilesList.collectAsState().value

    var searchQuery by remember { mutableStateOf("") }
    var selectedFileForPreview by remember { mutableStateOf<File?>(null) }
    var selectedFileContent by remember { mutableStateOf("جاري تحميل المحتوى...") }
    var fileLoadProgress by remember { mutableStateOf(false) }
    var isExecutingFile by remember { mutableStateOf(false) }

    // Dialog state for new file/folder creation
    var showCreateDialog by remember { mutableStateOf(false) }
    var createName by remember { mutableStateOf("") }
    var isCreatingFolder by remember { mutableStateOf(true) } // true folder, false file

    // Sync base directory when opened
    LaunchedEffect(Unit) {
        val lastPathStr = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
            .getString("browser_last_visited_dir", "") ?: ""
        val lastVisitedDir = File(lastPathStr)
        if (lastVisitedDir.exists() && lastVisitedDir.isDirectory) {
            viewModel.navigateToDir(lastVisitedDir)
        } else {
            viewModel.navigateToBaseDir()
        }
    }

    // Save path upon directory changes
    LaunchedEffect(browserCurrentPath) {
        browserCurrentPath?.let {
            context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                .edit().putString("browser_last_visited_dir", it.absolutePath).apply()
        }
    }

    // Refresh file preview when a file is selected
    LaunchedEffect(selectedFileForPreview) {
        val file = selectedFileForPreview
        if (file != null) {
            fileLoadProgress = true
            selectedFileContent = "جاري القراءة بآمان..."
            withContext(Dispatchers.IO) {
                try {
                    if (file.isDirectory) {
                        selectedFileContent = ""
                    } else {
                        // Safe text estimation logic
                        val length = file.length()
                        if (length > 300 * 1024) {
                            selectedFileContent = "⚠️ الملف كبير الحجم المعايناتي (${length / 1024} KB).\nيمكنك تشغيله ومعالجته مباشرة دون تحميل كتل الواجهة."
                        } else {
                            val content = file.readText(Charsets.UTF_8)
                            selectedFileContent = if (content.length > 2500) {
                                content.take(2500) + "\n\n... (تم اقتصاص المعاينة الفورية لكبر الحجم)"
                            } else {
                                content
                            }
                        }
                    }
                } catch (e: Exception) {
                    selectedFileContent = "❌ فشل قراءة محتويات الملف كـ نصوص عادية:\n${e.localizedMessage}"
                }
            }
            fileLoadProgress = false
        }
    }

    // Filter files list based on Search query
    val filteredFilesList = remember(browserFiles, searchQuery) {
        if (searchQuery.isBlank()) {
            browserFiles
        } else {
            browserFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
                .border(1.dp, MetallicGold.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                .shadow(24.dp, RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = SlateBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top header controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📁 متصفح الملفات الذكي والمنفذ",
                            color = BrightGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "معاينة الملفات المحلية ومعالجتها بـ Builder Engine",
                            color = TextGray,
                            fontSize = 10.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Create action
                        IconButton(
                            onClick = {
                                createName = ""
                                isCreatingFolder = true
                                showCreateDialog = true
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(GlassWhite, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "إنشاء مجلد", tint = BrightGold, modifier = Modifier.size(18.dp))
                        }

                        // Close Dialog button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .background(GlassWhite, CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = DangerRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Current path breadcrumb path selector
                browserCurrentPath?.let { path ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GlassBlack, RoundedCornerShape(12.dp))
                            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = path.absolutePath,
                            color = TextSilver,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Standard quick shortcuts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val shortcuts = listOf(
                        Pair("الرئيسية (Home)", "/storage/emulated/0"),
                        Pair("المشاريع (Smart Platform)", viewModel.baseDirSetting.collectAsState().value),
                        Pair("المستندات (Documents)", "/storage/emulated/0/Documents"),
                        Pair("التنزيلات (Downloads)", "/storage/emulated/0/Download")
                    )

                    shortcuts.forEach { (label, rawPathStr) ->
                        val active = browserCurrentPath?.absolutePath == rawPathStr
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) GoldGlassBg else GlassWhite,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    0.5.dp,
                                    if (active) MetallicGold else GlassBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val f = File(rawPathStr)
                                    viewModel.navigateToDir(f)
                                    selectedFileForPreview = null
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (active) BrightGold else TextSilver,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // File Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("بحث عن ملف في المجلد الحالي...", color = TextMuted, fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = TextGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("file_search_input"),
                    textStyle = TextStyle(color = TextSilver, fontSize = 11.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MetallicGold,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = GlassBlack,
                        unfocusedContainerColor = GlassBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Layout main vertical content: Splitting Browser and Preview Drawer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // LEFT COLUMN: Files List Browser
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .background(GlassBlack, RoundedCornerShape(16.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                    ) {
                        if (filteredFilesList.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📭", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "لا توجد نتائج للبحث الحالي." else "هذا المجلد فارغ.",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Navigator back button
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.navigateUp()
                                                selectedFileForPreview = null
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MetallicGold, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = ".. (مستوى أعلى)",
                                            color = MetallicGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.3f))
                                }

                                items(filteredFilesList) { file ->
                                    val isFileSelected = selectedFileForPreview?.absolutePath == file.absolutePath
                                    val fileExt = file.extension.lowercase()
                                    
                                    // Custom colors & icons matching modern architecture
                                    val (icon, color) = remember(file) {
                                        if (file.isDirectory) {
                                            Pair(Icons.Default.Menu, BrightGold)
                                        } else {
                                            when (fileExt) {
                                                "txt" -> Pair(Icons.Default.Info, TextSilver)
                                                "json", "yaml", "yml" -> Pair(Icons.Default.Settings, Color(0xFFF97316))
                                                "py", "kt", "kts", "java", "sh", "js", "html", "css" -> Pair(Icons.Default.Build, Color(0xFF38BDF8))
                                                else -> Pair(Icons.Default.Info, TextGray)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isFileSelected) GoldGlassBg else Color.Transparent)
                                            .clickable {
                                                if (file.isDirectory) {
                                                    viewModel.navigateToDir(file)
                                                    selectedFileForPreview = null
                                                } else {
                                                    selectedFileForPreview = file
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                tint = color,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = file.name,
                                                    color = if (isFileSelected) BrightGold else TextSilver,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!file.isDirectory) {
                                                    Text(
                                                        text = formatBytes(file.length()),
                                                        color = TextMuted,
                                                        fontSize = 8.sp
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // Quick Delete to maintain healthy workspace
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteFileFromBrowser(file)
                                                    if (isFileSelected) selectedFileForPreview = null
                                                },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "حذف ملف",
                                                    tint = DangerRed.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = GlassBorder.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }

                    // RIGHT COLUMN: Selected File Preview Drawer / Actions
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .background(GlassBlack, RoundedCornerShape(16.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                            .padding(10.dp)
                    ) {
                        val file = selectedFileForPreview
                        if (file == null) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📄", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "اختر ملفاً لمعاينته وتشغيله",
                                    color = TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "اضغط على أي ملف لاستعراض تفاصيله وخيارات تشغيله هنا فورياً.",
                                    color = TextMuted,
                                    fontSize = 8.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        } else {
                            // File fully selected preview state
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // File metadata label card
                                KeyValueRow("الاسم:", file.name)
                                KeyValueRow("الحجم:", formatBytes(file.length()))
                                KeyValueRow("تعديل الأخير:", formatLastModified(file.lastModified()))

                                HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 2.dp))

                                Text(
                                    text = "مراجعة كود التوجيهات (Preview):",
                                    color = MetallicGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Terminal-like preview card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(Color(0xFF07090C), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, GlassBorder, RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    if (fileLoadProgress) {
                                        CircularProgressIndicator(
                                            color = MetallicGold,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .align(Alignment.Center)
                                        )
                                    } else {
                                        Text(
                                            text = selectedFileContent,
                                            color = Color(0xFFE2E8F0),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            lineHeight = 13.sp,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState()),
                                            style = TextStyle(textDirection = TextDirection.Ltr)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // Quick Process Action
                                Button(
                                    onClick = {
                                        isExecutingFile = true
                                        // Execute processing using Builder Engine manually
                                        scope.launch {
                                            try {
                                                val contentToProcess = withContext(Dispatchers.IO) { file.readText() }
                                                viewModel.runManualProcess(contentToProcess) { resultMsg ->
                                                    isExecutingFile = false
                                                    Toast.makeText(context, resultMsg, Toast.LENGTH_LONG).show()
                                                    onFileProcessedSuccessfully(file.absolutePath)
                                                }
                                            } catch (e: Exception) {
                                                isExecutingFile = false
                                                Toast.makeText(context, "فشل بدء المعالجة: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .testTag("process_selected_file_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isExecutingFile) {
                                        CircularProgressIndicator(color = SlateBg, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🎯 تشغيل بواسطة المحرك", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Secondary action: Load into parent UI
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val fullText = withContext(Dispatchers.IO) { file.readText() }
                                                onLoadToEditor(fullText)
                                                Toast.makeText(context, "✅ تم تحميل النص كاملاً في الواجهة بنجاح!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "فشل استخراج النص: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSilver),
                                    border = BorderStroke(1.dp, GlassBorder),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("📥 نقل النص إلى المحرر المكتوب", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal creation folder/file dialogue dialog
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassBorder, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = CardSlateBg
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isCreatingFolder) "📁 إنشاء مجلد فرعي جديد" else "📄 إنشاء ملف نصي جديد",
                        color = BrightGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isCreatingFolder) GoldGlassBg else GlassWhite, RoundedCornerShape(10.dp))
                                .border(0.5.dp, if (isCreatingFolder) MetallicGold else GlassBorder, RoundedCornerShape(10.dp))
                                .clickable { isCreatingFolder = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("مجلد (Directory)", color = if (isCreatingFolder) BrightGold else TextSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (!isCreatingFolder) GoldGlassBg else GlassWhite, RoundedCornerShape(10.dp))
                                .border(0.5.dp, if (!isCreatingFolder) MetallicGold else GlassBorder, RoundedCornerShape(10.dp))
                                .clickable { isCreatingFolder = false }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ملف (File)", color = if (!isCreatingFolder) BrightGold else TextSilver, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        placeholder = { Text("أدخل الاسم هنا...", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("create_item_input"),
                        textStyle = TextStyle(color = TextSilver, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MetallicGold,
                            unfocusedBorderColor = GlassBorder,
                            focusedContainerColor = GlassBlack,
                            unfocusedContainerColor = GlassBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (createName.isBlank()) {
                                    Toast.makeText(context, "الرجاء كتابة الاسم بالكامل أولاً", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val currentDir = browserCurrentPath ?: return@Button
                                if (isCreatingFolder) {
                                    viewModel.createDirectoryInBrowser(createName)
                                } else {
                                    val f = File(currentDir, createName)
                                    try {
                                        f.createNewFile()
                                        Toast.makeText(context, "تم إنشاء ملف فارغ بنجاح!", Toast.LENGTH_SHORT).show()
                                        viewModel.navigateToDir(currentDir) // refresh
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "فشل إنشاء الملف: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                showCreateDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MetallicGold, contentColor = SlateBg),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("تأكيد وحفظ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showCreateDialog = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSilver),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GlassBorder),
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

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = key,
            color = TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = TextSilver,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return if (bytes < 1024) {
        "$bytes B"
    } else if (bytes < 1024 * 1024) {
        String.format("%.1f KB", bytes / 1024f)
    } else {
        String.format("%.1f MB", bytes / (1024f * 1024f))
    }
}

private fun formatLastModified(mtime: Long): String {
    return try {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        sdf.format(Date(mtime))
    } catch (e: Exception) {
        "غير معروف"
    }
}
