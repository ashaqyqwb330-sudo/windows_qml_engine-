package com.example

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GlassBlack
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassWhite
import com.example.ui.theme.GoldGlassBg
import com.example.ui.components.CodeEditor
import com.example.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreeDocDashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val targetPath = viewModel.showTreeDocDashboardPath.collectAsState().value ?: "."
    
    // Base Directory Object lookup
    val baseDir = remember(targetPath) {
        val fObj = File(targetPath)
        if (fObj.isAbsolute && fObj.exists()) fObj else File(context.filesDir, targetPath)
    }

    // State of all files/folders read
    var allFiles by remember { mutableStateOf<List<LocalFileInfo>>(emptyList()) }
    var statsInfo by remember { mutableStateOf(StatsInfo(0, 0, "0 B", "0 ms")) }
    var expandedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") } // "ALL", "TEXTS", "CODE", "IMAGES"
    var selectedFile by remember { mutableStateOf<LocalFileInfo?>(null) }
    var activePreviewFile by remember { mutableStateOf<LocalFileInfo?>(null) }
    var activeTab by remember { mutableStateOf("TREE") } // "TREE", "GRAPH", "EXPORT"
    var isLoading by remember { mutableStateOf(true) }

    // Read the files recursively
    LaunchedEffect(baseDir) {
        isLoading = true
        val startTime = System.currentTimeMillis()
        val fileList = mutableListOf<LocalFileInfo>()
        
        fun scanDir(dir: java.io.File, relativePrefix: String) {
            val list = dir.listFiles() ?: return
            for (f in list) {
                if (f.name == "tree_report.txt" || f.name == "tree_report.json" || f.name.startsWith(".")) {
                    continue
                }
                val relativePath = if (relativePrefix.isEmpty()) f.name else "$relativePrefix/${f.name}"
                val lastMod = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(f.lastModified()))
                if (f.isDirectory) {
                    fileList.add(
                        LocalFileInfo(
                            name = f.name,
                            relativePath = relativePath,
                            absolutePath = f.absolutePath,
                            sizeBytes = 0,
                            sizeStr = "مجلد",
                            isDir = true,
                            lastModified = lastMod
                        )
                    )
                    scanDir(f, relativePath)
                } else {
                    val size = f.length()
                    fileList.add(
                        LocalFileInfo(
                            name = f.name,
                            relativePath = relativePath,
                            absolutePath = f.absolutePath,
                            sizeBytes = size,
                            sizeStr = formatTreeDocBytes(size),
                            isDir = false,
                            lastModified = lastMod
                        )
                    )
                }
            }
        }

        if (baseDir.exists() && baseDir.isDirectory) {
            scanDir(baseDir, "")
        } else if (baseDir.exists() && baseDir.isFile) {
            fileList.add(
                LocalFileInfo(
                    name = baseDir.name,
                    relativePath = baseDir.name,
                    absolutePath = baseDir.absolutePath,
                    sizeBytes = baseDir.length(),
                    sizeStr = formatTreeDocBytes(baseDir.length()),
                    isDir = false,
                    lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(baseDir.lastModified()))
                )
            )
        }
        
        // Compute stats
        val filesOnly = fileList.filter { !it.isDir }
        val count = filesOnly.size
        val totalBytes = filesOnly.sumOf { it.sizeBytes }
        val duration = System.currentTimeMillis() - startTime
        
        // Auto-expand all top/mid level directories initially
        val defaultExpanded = fileList.filter { it.isDir }.map { it.relativePath }.toSet()

        statsInfo = StatsInfo(
            count = count,
            dirsCount = fileList.filter { it.isDir }.size,
            totalSize = formatTreeDocBytes(totalBytes),
            scanTimeMs = "${duration}ms"
        )
        allFiles = fileList
        expandedFolders = defaultExpanded
        isLoading = false
    }

    // Filter items dynamically based on search & category
    val filteredFiles = remember(allFiles, searchQuery, selectedCategory, expandedFolders) {
        allFiles.filter { item ->
            // Search filter
            val matchesSearch = item.name.contains(searchQuery, ignoreCase = true) || 
                                item.relativePath.contains(searchQuery, ignoreCase = true)
            
            // Category filter for files
            val matchesCategory = when (selectedCategory) {
                "TEXTS" -> !item.isDir && (item.name.endsWith(".txt", true) || item.name.endsWith(".html", true) || item.name.endsWith(".md", true))
                "CODE" -> !item.isDir && (item.name.endsWith(".kt", true) || item.name.endsWith(".py", true) || item.name.endsWith(".java", true) || item.name.endsWith(".json", true))
                "IMAGES" -> !item.isDir && (item.name.endsWith(".png", true) || item.name.endsWith(".jpg", true) || item.name.endsWith(".jpeg", true))
                else -> true // ALL
            }

            // Folder packaging check: files should only be shown if their parent directory is expanded
            val parentPath = if (item.relativePath.contains("/")) item.relativePath.substringBeforeLast("/") else ""
            val isParentExpanded = parentPath.isEmpty() || expandedFolders.contains(parentPath)

            matchesSearch && (item.isDir || matchesCategory) && (item.isDir || isParentExpanded)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "لوحة التقرير الشجري الموحدة",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = baseDir.absolutePath,
                            style = TextStyle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeTreeDocDashboard() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Stats Row card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatBadge("📄 ملفات", "${statsInfo.count}", MaterialTheme.colorScheme.primary)
                    StatBadge("📁 مجلدات", "${statsInfo.dirsCount}", MaterialTheme.colorScheme.secondary)
                    StatBadge("💾 حجم كلي", statsInfo.totalSize, MaterialTheme.colorScheme.tertiary)
                    StatBadge("🕐 رصد", statsInfo.scanTimeMs, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Bar Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassBlack, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "TREE" to "🌴 شجرة مستكشف",
                    "GRAPH" to "📊 توزيع بياني",
                    "EXPORT" to "📥 تقارير التصدير"
                ).forEach { (tabId, tabName) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (activeTab == tabId) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .border(
                                1.dp,
                                if (activeTab == tabId) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { activeTab = tabId }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabName,
                            color = if (activeTab == tabId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                when (activeTab) {
                    "TREE" -> {
                        // Tree View Tab Content
                        Column(modifier = Modifier.weight(1f)) {
                            // Search and filtering row
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("ابحث بالاسم أو الامتداد...", fontSize = 11.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("treedoc_search_field"),
                                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    unfocusedBorderColor = GlassBorder,
                                    focusedContainerColor = GlassBlack,
                                    unfocusedContainerColor = GlassBlack
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Category chips
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val cats = listOf(
                                    "ALL" to "📁 الكل",
                                    "TEXTS" to "📝 نصوص",
                                    "CODE" to "💻 كود",
                                    "IMAGES" to "🖼️ صور"
                                )
                                items(cats) { (catId, catLabel) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selectedCategory == catId) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else GlassBlack
                                            )
                                            .border(
                                                1.dp,
                                                if (selectedCategory == catId) MaterialTheme.colorScheme.primary else GlassBorder,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedCategory = catId }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = catLabel,
                                            color = if (selectedCategory == catId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Directories list / Tree elements
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(GlassBlack, RoundedCornerShape(12.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            ) {
                                if (filteredFiles.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "مستكشف الملفات لا يتطابق مع معايير البحث والفرز.",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(filteredFiles) { file ->
                                            val depth = file.relativePath.count { it == '/' }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (selectedFile == file) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                                                    )
                                                    .clickable {
                                                        if (file.isDir) {
                                                            expandedFolders = if (expandedFolders.contains(file.relativePath)) {
                                                                expandedFolders - file.relativePath
                                                            } else {
                                                                expandedFolders + file.relativePath
                                                            }
                                                        } else {
                                                            selectedFile = file
                                                        }
                                                    }
                                                    .padding(vertical = 6.dp)
                                                    .padding(start = (depth * 14).dp, end = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Icon representing state
                                                Text(
                                                    text = if (file.isDir) {
                                                        if (expandedFolders.contains(file.relativePath)) "📂" else "📁"
                                                    } else {
                                                        getFileIcon(file.name)
                                                    },
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.padding(end = 6.dp)
                                                )

                                                Text(
                                                    text = file.name,
                                                    style = TextStyle(
                                                        color = if (file.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (file.isDir) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 11.5.sp,
                                                        fontFamily = if (file.isDir) FontFamily.Default else FontFamily.Monospace
                                                    ),
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (!file.isDir) {
                                                    Text(
                                                        text = file.sizeStr,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        modifier = Modifier.padding(start = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "GRAPH" -> {
                        // Drawing distributions
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            DistributionChartPane(allFiles)
                        }
                    }

                    "EXPORT" -> {
                        // Export pane triggers
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            ExportToolsPane(viewModel, baseDir, allFiles)
                        }
                    }
                }
            }

            // Preview Sheet or Detail Pane overlaying the bottom
            AnimatedVisibility(
                visible = selectedFile != null && activeTab == "TREE",
                modifier = Modifier.animateContentSize()
            ) {
                selectedFile?.let { file ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📄 تفاصيل الملف المحدد:",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { selectedFile = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "اغلاق", modifier = Modifier.size(16.dp))
                                }
                            }

                            Text(
                                text = file.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "المسار: ${file.relativePath}",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("الحجم: ${file.sizeStr}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                                Text("آخر تعديل: ${file.lastModified}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 10.sp)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { activePreviewFile = file },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("👁️ معاينة داخلية", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(file.absolutePath))
                                        Toast.makeText(context, "🌱 تم نسخ المسار المطلق بنجاح!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("📋 نسخ المسار", fontSize = 10.5.sp)
                                }

                                Button(
                                    onClick = {
                                        try {
                                            val text = File(file.absolutePath).readText(Charsets.UTF_8)
                                            clipboardManager.setText(AnnotatedString(text))
                                            Toast.makeText(context, "🗒️ تم قراءة ونسخ محتوى الملف كاملاً!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "❌ تعذر الفتح: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .height(36.dp)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("📂 نسخ الشفرة", fontSize = 10.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Full Inline Preview (WebView or Custom Highlight Editor Overlay)
    if (activePreviewFile != null) {
        val fPreview = activePreviewFile!!
        Dialog(
            onDismissRequest = { activePreviewFile = null }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👀 معاينة: ${fPreview.name}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { activePreviewFile = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "اغلاق", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        val extension = fPreview.name.substringAfterLast(".", "").lowercase()
                        if (extension == "html" || extension == "md") {
                            // WebView render
                            val content = remember(fPreview) {
                                try {
                                    val fileText = File(fPreview.absolutePath).readText(Charsets.UTF_8)
                                    if (extension == "md") {
                                        // A simple mock markdown to HTML template
                                        "<html><head><style>body{background-color:#1e1e1e;color:#e0e0e0;font-family:sans-serif;padding:16px;line-height:1.6;}h1,h2{color:#d4af37;}</style></head><body>" +
                                        fileText.replace("\n", "<br/>").replace("# ", "<h1>").replace("## ", "<h2>") +
                                        "</body></html>"
                                    } else {
                                        fileText
                                    }
                                } catch (e: Exception) {
                                    "<h3>خطأ بقراءة الملف: ${e.message}</h3>"
                                }
                            }
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        webViewClient = WebViewClient()
                                        settings.javaScriptEnabled = true
                                        loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
                                    }
                                },
                                update = { webView ->
                                    webView.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Syntax color highlighted reader inside custom CodeEditor
                            val fileContent = remember(fPreview) {
                                try {
                                    File(fPreview.absolutePath).readText(Charsets.UTF_8)
                                } catch (e: Exception) {
                                    "// خطأ بقراءة الملف: ${e.message}"
                                }
                            }
                            CodeEditor(
                                value = fileContent,
                                onValueChange = {},
                                language = extension,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// Stats helper
data class StatsInfo(
    val count: Int,
    val dirsCount: Int,
    val totalSize: String,
    val scanTimeMs: String
)

data class LocalFileInfo(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val sizeStr: String,
    val isDir: Boolean,
    val lastModified: String
)

@Composable
fun StatBadge(
    label: String,
    value: String,
    accentColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 9.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
    }
}

// Simple smart vector-like Canvas Pie Chart distribution
@Composable
fun DistributionChartPane(files: List<LocalFileInfo>) {
    val context = LocalContext.current
    val categories = remember(files) {
        val filesOnly = files.filter { !it.isDir }
        val counts = mutableMapOf<String, Int>()
        for (f in filesOnly) {
            val ext = f.name.substringAfterLast(".", "").lowercase()
            val cat = when (ext) {
                "py", "kt", "java", "json" -> "برمجيات (Code)"
                "html", "md", "txt" -> "نصوص ومستندات"
                "png", "jpg", "jpeg", "gif" -> "وسائط وصور"
                else -> "أخرى"
            }
            counts[cat] = (counts[cat] ?: 0) + 1
        }
        counts.map { (cat, count) ->
            ChartSlice(
                label = cat,
                count = count,
                fraction = count.toFloat() / filesOnly.size.coerceAtLeast(1)
            )
        }
    }

    if (categories.isEmpty()) {
        Text("لا يوجد ملفات لتصويرها بيانيًا.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        return
    }

    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "توزيع الملفات حسب الأنواع البرمجية والبيانية",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(20.dp))

        // Canvas Pie Chart
        Box(
            modifier = Modifier.size(170.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = 0f
                categories.forEachIndexed { idx, slice ->
                    val sweepAngle = slice.fraction * 360f
                    drawArc(
                        color = colors[idx % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Legend
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEachIndexed { idx, slice ->
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
                                .size(10.dp)
                                .background(colors[idx % colors.size], RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = slice.label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "${slice.count} ملفات (${(slice.fraction * 100).toInt()}%)",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

data class ChartSlice(
    val label: String,
    val count: Int,
    val fraction: Float
)

// Export Tools Panel allowing user to generate formatted reports applying current filters
@Composable
fun ExportToolsPane(
    viewModel: MainViewModel,
    baseDir: File,
    allFiles: List<LocalFileInfo>
) {
    val context = LocalContext.current
    var exportStatus by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "تصدير التقرير الشجري الفوري بلمسة واحدة",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "يقوم النظام بأتمتة فرز وتوليد التقارير المتوافقة مع معجم الأنظمة البرمجية الفاخرة، مع كشف الحمايات والمسارات.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Grid of 4 Export Button Formats
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExportButton(
                    title = "تقرير تفاعلي HTML",
                    icon = "🌐",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    isExporting = true
                    viewModel.generateTreeReport(baseDir.name, "html", true) { msg ->
                        isExporting = false
                        exportStatus = msg
                    }
                }

                ExportButton(
                    title = "ملف مخرجات JSON",
                    icon = "📋",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    isExporting = true
                    viewModel.generateTreeReport(baseDir.name, "json", true) { msg ->
                        isExporting = false
                        exportStatus = msg
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExportButton(
                    title = "مستند طباعة PDF",
                    icon = "📕",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    isExporting = true
                    viewModel.generateTreeReport(baseDir.name, "pdf", true) { msg ->
                        isExporting = false
                        exportStatus = msg
                    }
                }

                ExportButton(
                    title = "جدول بيانات CSV",
                    icon = "📊",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                ) {
                    isExporting = true
                    viewModel.generateTreeReport(baseDir.name, "csv", true) { msg ->
                        isExporting = false
                        exportStatus = msg
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
        }

        if (exportStatus.isNotEmpty()) {
            Text(
                text = exportStatus,
                color = if (exportStatus.contains("✅")) Color(0xFF10B981) else Color(0xFFEF4444),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun ExportButton(
    title: String,
    icon: String,
    modifier: Modifier = Modifier,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(54.dp)
            .clickable { onClick() }
            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(text = title, color = color, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Byte formatter utility
fun formatTreeDocBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// Icon getter based on extension
fun getFileIcon(name: String): String {
    val ext = name.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "py" -> "🐍"
        "kt" -> "💜"
        "java" -> "☕"
        "html" -> "🌐"
        "md" -> "📝"
        "txt" -> "📄"
        "json" -> "📋"
        "png", "jpg", "jpeg", "gif" -> "🖼"
        "pdf" -> "📕"
        else -> "📎"
    }
}
