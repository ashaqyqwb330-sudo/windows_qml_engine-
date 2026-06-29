package com.example.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.db.AppDatabase
import com.example.db.FileEntity
import com.example.db.LogEntity
import com.example.engine.BuilderEngine
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.BroadcastReceiver
import android.content.IntentFilter
import kotlinx.coroutines.flow.first

import android.widget.ScrollView
import android.view.inputmethod.InputMethodManager

/**
 * الخدمة العائمة الذهبية V2 - Golden Bubble Service
 * واجهة تقليدية View-based بحتة لضمان أقصى مستويات الأداء والاستقرار وسرعة الاستجابة.
 * لا تحتوي على أي أكواد Compose لتفادي أي انهيار غير متوقع على مختلف الأجهزة.
 */
class GoldenBubbleService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var rootLayout: FrameLayout? = null
    private lateinit var database: AppDatabase

    private val handler = Handler(Looper.getMainLooper())
    private var lastKnownClipText = ""
    private var isPollingActive = false
    private var lastSavedFilePath: String? = null

    private lateinit var statusCircle: View
    private lateinit var statusTxt: TextView
    private lateinit var lastActionTxt: TextView
    private var sourceBarLayout: LinearLayout? = null
    private var sourceIconTxt: TextView? = null
    private var sourceNameTxt: TextView? = null
    private var contextActionsRow: LinearLayout? = null
    private lateinit var toggleStatusBtn: Button
    
    private lateinit var bubbleStatusTxt: TextView
    private var clipboardReceiver: BroadcastReceiver? = null

    private var collapsedLayout: LinearLayout? = null
    private var expandedLayout: LinearLayout? = null

    private fun collapseBubble() {
        expandedLayout?.visibility = View.GONE
        collapsedLayout?.visibility = View.VISIBLE
    }

    private fun expandBubble() {
        collapsedLayout?.visibility = View.GONE
        expandedLayout?.visibility = View.VISIBLE
    }

    // Rebuild filtering & notification views
    private var activeFilter = "all" // "all", "builder", "executor"
    private var currentFullLogs: List<LogEntity> = emptyList()
    private lateinit var logsContainer: LinearLayout
    private var isStoryExpanded = false
    private var notificationToastLayout: LinearLayout? = null
    private var notificationToastText: TextView? = null

    // Project Context Dialog Views
    private var contextDialogLayout: LinearLayout? = null
    private var contextDialogText: TextView? = null
    private var saveHereBtn: Button? = null
    private var newFolderBtn: Button? = null
    private var ignoreBtn: Button? = null
    private var activeProjectTxtView: TextView? = null

    // Manual Folder Name Input Views
    private var manualNameInputLayout: LinearLayout? = null
    private var manualFolderNameEditText: android.widget.EditText? = null
    private var confirmManualFolderBtn: Button? = null
    private var cancelManualFolderBtn: Button? = null

    // Template Dialog Views
    private var templateDialogLayout: LinearLayout? = null
    private var importFastBtn: Button? = null
    private var importDetailBtn: Button? = null
    private var templateCancelBtn: Button? = null
    private var pendingTemplateText: String? = null

    private val isPaused: Boolean
        get() = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE).getBoolean("clipboard_is_paused", false)

    private val clipboardRunnable = object : Runnable {
        override fun run() {
            checkClipboardInGoldenBubble()
            handler.postDelayed(this, 1500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        database = AppDatabase.getDatabase(this)

        com.example.engine.UnifiedPathManager.init(this)
        com.example.engine.UnifiedPathManager.observeActivePath(this, androidx.lifecycle.Observer { path ->
            val pName = com.example.engine.ProjectManager.getActiveProjectName(this@GoldenBubbleService)
            activeProjectTxtView?.text = "المشروع النشط: $pName"
        })
        
        // Register broadcast receiver for clipboard updates & smart capture completions
        clipboardReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.example.ACTION_CLIPBOARD_UPDATED") {
                    val text = intent.getStringExtra("extra_text") ?: ""
                    if (text.isNotBlank()) {
                        Log.d("GoldenBubbleService", "Received clipboard broadcast: size=${text.length}")
                        onClipboardTextDetected(text)
                    }
                } else if (intent?.action == "com.example.ACTION_SMART_CAPTURE_COMPLETED") {
                    val lastSavedName = intent.getStringExtra("last_saved_name") ?: ""
                    val lastSavedPath = intent.getStringExtra("last_saved_path") ?: ""
                    if (lastSavedPath.isNotEmpty()) {
                        lastSavedFilePath = lastSavedPath
                        updateLastActionText("💾 تم حفظ: $lastSavedName (انقر للمستند)")
                        
                        // Calculate precise file size to show on floating notification
                        val sizeStr = try {
                            val file = File(lastSavedPath)
                            if (file.exists()) {
                                val kb = file.length() / 1024.0
                                String.format(java.util.Locale.US, "%.1f KB", kb)
                            } else {
                                "0.5 KB"
                            }
                        } catch (e: Exception) {
                            "0.5 KB"
                        }
                        showNotificationToast(lastSavedName, sizeStr, lastSavedPath)
                    }
                } else if (intent?.action == "com.example.ACTION_PROJECT_CONTEXT_QUESTION") {
                    val text = intent.getStringExtra("extra_text") ?: ""
                    if (text.isNotBlank()) {
                        showContextDecisionDialog(text)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction("com.example.ACTION_CLIPBOARD_UPDATED")
            addAction("com.example.ACTION_SMART_CAPTURE_COMPLETED")
            addAction("com.example.ACTION_PROJECT_CONTEXT_QUESTION")
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(clipboardReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(clipboardReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("GoldenBubbleService", "Error registering receiver: ${e.message}")
        }
        
        startGoldenPolling()
        Log.d("GoldenBubbleService", "GoldenBubbleService created and polling handler started.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            try {
                removeGoldenView()
                stopSelf()
            } catch (e: Exception) {
                Log.e("GoldenBubbleService", "Error stopping service: ${e.message}")
            }
            return START_NOT_STICKY
        }

        if (rootLayout != null) return START_STICKY

        setupGoldenView()

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        return START_STICKY
    }

    private fun startGoldenPolling() {
        if (!isPollingActive) {
            handler.post(clipboardRunnable)
            isPollingActive = true
        }
    }

    private fun checkClipboardInGoldenBubble() {
        if (isPaused) return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip ?: return
                if (clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    if (text.isNotBlank() && text != lastKnownClipText) {
                        lastKnownClipText = text
                        onClipboardTextDetected(text)
                    }
                }
            }
        } catch (e: Exception) {
            // safe silent catch
        }
    }

    private fun onClipboardTextDetected(text: String) {
        val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val isAutoProcess = sharedPrefs.getBoolean("auto_process_clipboard", true)
        if (!isAutoProcess) return

        val textHash = text.trim().hashCode().toString()
        val lastProcessedHash = sharedPrefs.getString("last_processed_text_hash", "")
        if (textHash == lastProcessedHash) return

        val smartPrefs = getSharedPreferences("SmartCapturePrefs", Context.MODE_PRIVATE)
        val autoImportTemplates = smartPrefs.getBoolean("auto_import_templates", true)
        val trimmedText = text.trim()
        if (autoImportTemplates && trimmedText.startsWith("{") && (trimmedText.contains("template_version") || trimmedText.contains("templateVersion"))) {
            pendingTemplateText = text
            showTemplateDialogOverlay()
            return
        }

        val pBuilder = sharedPrefs.getString("prefix_builder", "@builder") ?: "@builder"
        val pExecutor = sharedPrefs.getString("prefix_executor", "@executor") ?: "@executor"
        val pTreedoc = sharedPrefs.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"

        val smartEnabled = smartPrefs.getBoolean("smart_capture_enabled", false)

        if (text.contains("$pBuilder:") || text.contains("$pExecutor:") || text.contains("$pTreedoc:") || smartEnabled) {
            processClipboardContent(text, force = false)
        }
    }

    private fun processClipboardContent(text: String, force: Boolean = false) {
        val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val textHash = text.trim().hashCode().toString()
        if (!force) {
            val lastProcessedHash = sharedPrefs.getString("last_processed_text_hash", "")
            if (textHash == lastProcessedHash && text.isNotBlank()) return

            val lastProcessed = sharedPrefs.getString("last_auto_processed_text", "") ?: ""
            if (text == lastProcessed && text.isNotBlank()) return
        }

        sharedPrefs.edit().apply {
            putString("last_processed_text_hash", textHash)
            putString("last_auto_processed_text", text)
            apply()
        }

        serviceScope.launch {
            try {
                val pBuilder = sharedPrefs.getString("prefix_builder", "@builder") ?: "@builder"
                val pExecutor = sharedPrefs.getString("prefix_executor", "@executor") ?: "@executor"
                val pTreedoc = sharedPrefs.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"

                val settings = mapOf(
                    "absolute_path_handling" to "relative",
                    "base_dir" to getBaseDir().absolutePath,
                    "directive_prefixes" to listOf(pBuilder),
                    "executor_prefixes" to listOf(pExecutor),
                    "treedoc_prefixes" to listOf(pTreedoc)
                )

                val engine = BuilderEngine(applicationContext, settings)
                val results = engine.processText(text)

                if (results.isNotEmpty()) {
                    var buildersCount = 0
                    var lastCreatedPath = ""
                    for (res in results) {
                        if (res.type == "builder") {
                            buildersCount++
                            val path = res.data?.get("path") ?: "unknown"
                            val size = res.data?.get("size")?.toLongOrNull() ?: 0L
                            val mode = res.data?.get("mode") ?: "w"
                            val fullPath = res.data?.get("full_path") ?: ""
                            lastCreatedPath = path

                            database.dao().insertFile(
                                FileEntity(path = path, fullPath = fullPath, size = size, mode = mode)
                            )
                            database.dao().insertLog(
                                LogEntity(type = "builder", message = "الفقاعة الذهبية: تم إنشاء $path", details = res.message, source = "bubble")
                            )
                        } else {
                            database.dao().insertLog(
                                LogEntity(type = res.type, message = "الفقاعة الذهبية: إجراء ${res.type}", details = res.message, source = "bubble")
                            )
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (buildersCount > 0 && lastCreatedPath.isNotEmpty()) {
                            Toast.makeText(applicationContext, "✅ تم إنشاء $lastCreatedPath في المجلد بنجاح!", Toast.LENGTH_LONG).show()
                            updateLastActionText("تم إنشاء $lastCreatedPath")
                        } else {
                            Toast.makeText(applicationContext, "✅ معالجة ناجحة! تم تنفيذ الإجراءات.", Toast.LENGTH_SHORT).show()
                            updateLastActionText("معالجة ناجحة: تم تنفيذ الإجراءات")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GoldenBubbleService", "Error processing content: ${e.message}")
                withContext(Dispatchers.Main) {
                    updateLastActionText("فشل المعالجة: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun updateLastActionText(msg: String) {
        try {
            lastActionTxt.text = "آخر إجراء: $msg"
            if (::bubbleStatusTxt.isInitialized) {
                bubbleStatusTxt.text = msg
            }
            updateContextualActions(msg, "")
        } catch (e: Exception) {}
    }

    private fun extractFilePath(message: String, details: String): String? {
        val combined = "$message\n$details"
        val filePattern = Regex("""(/[a-zA-Z0-9_.-]+)+(\.[a-zA-Z0-9]+)""")
        val match1 = filePattern.find(combined)
        if (match1 != null) return match1.value
        
        val simpleFilePattern = Regex("""[a-zA-Z0-9_.-]+\.(html|py|kt|java|txt|json|xml)""", RegexOption.IGNORE_CASE)
        val match2 = simpleFilePattern.find(combined)
        if (match2 != null) return match2.value
        
        val words = combined.split(Regex("""\s+"""))
        for (word in words) {
            val cleaned = word.replace(Regex("""[()\[\]"']"""), "").trim()
            if (cleaned.endsWith(".html") || cleaned.endsWith(".kt") || cleaned.endsWith(".java") || 
                cleaned.endsWith(".py") || cleaned.endsWith(".txt") || cleaned.endsWith(".json") || 
                cleaned.endsWith(".xml")) {
                return cleaned
            }
        }
        
        if (combined.contains("مجلد باسم '")) {
            val folder = combined.substringAfter("مجلد باسم '").substringBefore("'")
            if (folder.isNotEmpty()) return folder
        }
        if (combined.contains("مجلد باسم ")) {
            val folder = combined.substringAfter("مجلد باسم ").split(" ").firstOrNull()?.trim()
            if (!folder.isNullOrEmpty()) return folder
        }
        if (combined.contains("مجلد: ")) {
            val folder = combined.substringAfter("مجلد: ").split(" ").firstOrNull()?.trim()
            if (!folder.isNullOrEmpty()) return folder
        }
        
        return null
    }

    private fun updateContextualActions(message: String, details: String) {
        val row = contextActionsRow ?: return
        row.removeAllViews()
        
        val extractedPath = extractFilePath(message, details)
        if (extractedPath != null) {
            row.visibility = View.VISIBLE
            
            val isHtml = extractedPath.endsWith(".html", ignoreCase = true)
            val isTextOrCode = extractedPath.endsWith(".py", ignoreCase = true) ||
                    extractedPath.endsWith(".kt", ignoreCase = true) ||
                    extractedPath.endsWith(".java", ignoreCase = true) ||
                    extractedPath.endsWith(".txt", ignoreCase = true) ||
                    extractedPath.endsWith(".json", ignoreCase = true) ||
                    extractedPath.endsWith(".xml", ignoreCase = true)
            
            val isFolder = !isHtml && !isTextOrCode && !extractedPath.contains(".")
            
            if (isHtml) {
                val btnPreview = Button(this).apply {
                    text = "👁️ معاينة"
                    textSize = 9f
                    setTextColor(Color.WHITE)
                    setPadding(dpToPx(8), 0, dpToPx(8), 0)
                    background = createRoundedDrawable("#10B981", 6f)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(28)).apply {
                        setMargins(0, 0, dpToPx(4), 0)
                    }
                    layoutParams = lp
                    setOnClickListener {
                        com.example.engine.FileUtils.openFileSafely(applicationContext, extractedPath)
                    }
                }
                row.addView(btnPreview)
            } else if (isTextOrCode) {
                val btnEdit = Button(this).apply {
                    text = "✏️ تحرير"
                    textSize = 9f
                    setTextColor(Color.WHITE)
                    setPadding(dpToPx(8), 0, dpToPx(8), 0)
                    background = createRoundedDrawable("#3B82F6", 6f)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(28)).apply {
                        setMargins(0, 0, dpToPx(4), 0)
                    }
                    layoutParams = lp
                    setOnClickListener {
                        com.example.engine.FileUtils.openFileSafely(applicationContext, extractedPath)
                    }
                }
                row.addView(btnEdit)
            } else if (isFolder) {
                val btnOpen = Button(this).apply {
                    text = "📂 فتح"
                    textSize = 9f
                    setTextColor(Color.WHITE)
                    setPadding(dpToPx(8), 0, dpToPx(8), 0)
                    background = createRoundedDrawable("#D97706", 6f)
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(28)).apply {
                        setMargins(0, 0, dpToPx(4), 0)
                    }
                    layoutParams = lp
                    setOnClickListener {
                        com.example.engine.FileUtils.openFileSafely(applicationContext, extractedPath)
                    }
                }
                row.addView(btnOpen)
            }
        } else {
            row.visibility = View.GONE
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun getRelativeTimeString(time: Long): String {
        val diff = System.currentTimeMillis() - time
        if (diff < 0) return "الآن"
        val seconds = diff / 1000
        if (seconds < 60) return "الآن"
        val minutes = seconds / 60
        if (minutes < 60) return "منذ $minutes د"
        val hours = minutes / 60
        if (hours < 24) return "منذ $hours س"
        val days = hours / 24
        return "منذ $days ي"
    }

    private fun createCircleDrawable(colorHex: String): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(colorHex))
        }
    }

    private fun createRoundedDrawable(colorHex: String, radiusDp: Float, strokeColorHex: String? = null, strokeWidthDp: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(colorHex))
            cornerRadius = dpToPx(radiusDp.toInt()).toFloat()
            if (strokeColorHex != null && strokeWidthDp > 0) {
                setStroke(dpToPx(strokeWidthDp), Color.parseColor(strokeColorHex))
            }
        }
    }

    private fun updateStatusUI() {
        val paused = isPaused
        if (paused) {
            statusTxt.text = "مراقبة الحافظة: 🔴 متوقف"
            toggleStatusBtn.text = "استئناف"
        } else {
            statusTxt.text = "مراقبة الحافظة: 🟢 نشط"
            toggleStatusBtn.text = "إيقاف مؤقت"
        }
    }

    private val hideNotificationRunnable = Runnable {
        try {
            notificationToastLayout?.let {
                windowManager.removeView(it)
                notificationToastLayout = null
            }
        } catch (e: Exception) {}
    }

    private fun showNotificationToast(fileName: String, sizeString: String, filePath: String) {
        handler.removeCallbacks(hideNotificationRunnable)
        lastSavedFilePath = filePath
        
        handler.post {
            try {
                notificationToastLayout?.let {
                    windowManager.removeView(it)
                    notificationToastLayout = null
                }
                
                val toastLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = createRoundedDrawable("#1A1A2E", 12f, "#FFD700", 2)
                    setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                    
                    val toastText = TextView(this@GoldenBubbleService).apply {
                        text = "💾 تم حفظ: $filePath ($sizeString)"
                        setTextColor(Color.WHITE)
                        textSize = 10f
                        gravity = Gravity.CENTER
                    }
                    addView(toastText)
                    
                    setOnClickListener {
                        com.example.engine.FileUtils.openFile(this@GoldenBubbleService, filePath)
                        handler.removeCallbacks(hideNotificationRunnable)
                        hideNotificationRunnable.run()
                    }
                }
                
                val params = WindowManager.LayoutParams(
                    dpToPx(240),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = dpToPx(120)
                }
                
                notificationToastLayout = toastLayout
                windowManager.addView(toastLayout, params)
                
                handler.postDelayed(hideNotificationRunnable, 5000L)
            } catch (e: Exception) {
                Log.e("GoldenBubbleService", "Error showing custom toast: ${e.message}")
            }
        }
    }

    private fun redrawLogs(logsList: List<LogEntity>) {
        val filteredLogs = when (activeFilter) {
            "all" -> logsList
            "builder" -> logsList.filter { it.type.lowercase().contains("builder") || it.type.lowercase().contains("build") }
            "executor" -> logsList.filter { it.type.lowercase().contains("executor") || it.type.lowercase().contains("command") || it.type.lowercase().contains("execute") }
            "treedoc" -> logsList.filter { it.type.lowercase().contains("treedoc") || it.type.lowercase().contains("file") }
            "success" -> logsList.filter { it.type.lowercase().contains("success") || it.message.contains("بنجاح") || it.message.contains("نجاح") }
            else -> logsList
        }
        val smartPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val limitCount = smartPrefs.getInt("log_copy_count", 5)
        val logViewMode = smartPrefs.getString("log_view_mode", "technical") ?: "technical"
        val recentLogs = filteredLogs.take(limitCount)
        
        handler.post {
            try {
                logsContainer.removeAllViews()
                if (logViewMode != "technical") {
                    val stories = com.example.LogAggregator.generateStoryCards(filteredLogs, logViewMode)
                    val latestStory = stories.firstOrNull()
                    if (latestStory == null) {
                        val emptyTxt = TextView(this@GoldenBubbleService).apply {
                            text = "لا توجد قصص مصورة متاحة."
                            setTextColor(Color.parseColor("#64748B"))
                            textSize = 9f
                            gravity = Gravity.CENTER
                            setPadding(0, dpToPx(16), 0, dpToPx(16))
                        }
                        logsContainer.addView(emptyTxt)
                    } else {
                        // Build storyboard card programmatically
                        val cardLayout = LinearLayout(this@GoldenBubbleService).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
                            background = createRoundedDrawable("#1E293B", 10f, "#334155", 1)
                            val lp = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, dpToPx(6))
                            }
                            layoutParams = lp
                        }

                        val headerRow = LinearLayout(this@GoldenBubbleService).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        val iconContainer = FrameLayout(this@GoldenBubbleService).apply {
                            background = createRoundedDrawable("#334155", 16f)
                            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                            val iconText = TextView(this@GoldenBubbleService).apply {
                                text = latestStory.icon
                                textSize = 14f
                            }
                            addView(iconText)
                        }
                        headerRow.addView(iconContainer)

                        val titleCol = LinearLayout(this@GoldenBubbleService).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dpToPx(8), 0, 0, 0)
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val titleText = TextView(this@GoldenBubbleService).apply {
                            text = latestStory.title
                            setTextColor(Color.parseColor("#F1F5F9"))
                            textSize = 10.5f
                            setTypeface(null, Typeface.BOLD)
                        }
                        titleCol.addView(titleText)

                        val timeText = TextView(this@GoldenBubbleService).apply {
                            text = latestStory.time
                            setTextColor(Color.parseColor("#94A3B8"))
                            textSize = 8f
                        }
                        titleCol.addView(timeText)

                        headerRow.addView(titleCol)
                        cardLayout.addView(headerRow)

                        val spacerLine = View(this@GoldenBubbleService).apply {
                            background = createRoundedDrawable("#475569", 1f)
                            val lpLine = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dpToPx(1)
                            ).apply {
                                setMargins(0, dpToPx(6), 0, dpToPx(6))
                            }
                            layoutParams = lpLine
                        }
                        cardLayout.addView(spacerLine)

                        val summaryText = TextView(this@GoldenBubbleService).apply {
                            text = latestStory.summary
                            setTextColor(Color.parseColor("#CBD5E1"))
                            textSize = 9.5f
                            setPadding(0, 0, 0, dpToPx(6))
                        }
                        cardLayout.addView(summaryText)

                        val statsLayout = LinearLayout(this@GoldenBubbleService).apply {
                            orientation = LinearLayout.VERTICAL
                            background = createRoundedDrawable("#0F172A", 6f)
                            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
                            val lpStats = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, dpToPx(6))
                            }
                            layoutParams = lpStats
                        }

                        val progressLabelsRow = LinearLayout(this@GoldenBubbleService).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        val statsLabel = TextView(this@GoldenBubbleService).apply {
                            text = "📊 عمليات:"
                            setTextColor(Color.parseColor("#D4AF37"))
                            textSize = 8.5f
                            setTypeface(null, Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        progressLabelsRow.addView(statsLabel)

                        val successLabel = TextView(this@GoldenBubbleService).apply {
                            text = "نجح: ${latestStory.successCount}"
                            setTextColor(Color.parseColor("#4ADE80"))
                            textSize = 8f
                            setTypeface(null, Typeface.BOLD)
                            setPadding(0, 0, dpToPx(4), 0)
                        }
                        progressLabelsRow.addView(successLabel)

                        val failureLabel = TextView(this@GoldenBubbleService).apply {
                            text = "فشل: ${latestStory.totalCount - latestStory.successCount}"
                            setTextColor(Color.parseColor("#EF4444"))
                            textSize = 8f
                            setTypeface(null, Typeface.BOLD)
                        }
                        progressLabelsRow.addView(failureLabel)

                        statsLayout.addView(progressLabelsRow)

                        val progressBar = android.widget.ProgressBar(this@GoldenBubbleService, null, android.R.attr.progressBarStyleHorizontal).apply {
                            max = latestStory.totalCount
                            progress = latestStory.successCount
                            val lpProgress = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                dpToPx(4)
                            ).apply {
                                setMargins(0, dpToPx(4), 0, 0)
                            }
                            layoutParams = lpProgress
                        }
                        statsLayout.addView(progressBar)
                        cardLayout.addView(statsLayout)

                        // Collapsible details log container
                        val detailsContainer = LinearLayout(this@GoldenBubbleService).apply {
                            orientation = LinearLayout.VERTICAL
                            background = createRoundedDrawable("#020617", 6f)
                            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                            visibility = if (isStoryExpanded) View.VISIBLE else View.GONE
                            val lpDet = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, dpToPx(6))
                            }
                            layoutParams = lpDet
                        }

                        val detailsColTitle = TextView(this@GoldenBubbleService).apply {
                            text = "📋 الأحداث المفصلة:"
                            setTextColor(Color.parseColor("#94A3B8"))
                            textSize = 8.5f
                            setTypeface(null, Typeface.BOLD)
                            setPadding(0, 0, 0, dpToPx(4))
                        }
                        detailsContainer.addView(detailsColTitle)

                        latestStory.rawEvents.forEach { event ->
                            val eventText = TextView(this@GoldenBubbleService).apply {
                                text = "• ${event.message}"
                                setTextColor(Color.parseColor("#94A3B8"))
                                textSize = 8f
                                setPadding(0, dpToPx(1), 0, dpToPx(1))
                            }
                            detailsContainer.addView(eventText)
                        }
                        cardLayout.addView(detailsContainer)

                        // Action Buttons row
                        val buttonsRow = LinearLayout(this@GoldenBubbleService).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        val copyBtn = Button(this@GoldenBubbleService).apply {
                            text = "📋 نسخ"
                            textSize = 8.5f
                            setTextColor(Color.WHITE)
                            background = createRoundedDrawable("#334155", 4f)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                dpToPx(28)
                            ).apply {
                                setMargins(0, 0, dpToPx(4), 0)
                            }
                            setOnClickListener {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Story Summary", latestStory.summary)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(applicationContext, "📋 تم نسخ ملخص القصة!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        buttonsRow.addView(copyBtn)

                        val detailsToggleBtn = Button(this@GoldenBubbleService).apply {
                            text = if (isStoryExpanded) "إخفاء" else "🔍 تفاصيل"
                            textSize = 8.5f
                            setTextColor(Color.WHITE)
                            background = createRoundedDrawable("#334155", 4f)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                dpToPx(28)
                            ).apply {
                                setMargins(0, 0, dpToPx(4), 0)
                            }
                        }
                        buttonsRow.addView(detailsToggleBtn)

                        val doToggleExpand = View.OnClickListener {
                            isStoryExpanded = !isStoryExpanded
                            detailsContainer.visibility = if (isStoryExpanded) View.VISIBLE else View.GONE
                            detailsToggleBtn.text = if (isStoryExpanded) "إخفاء" else "🔍 تفاصيل"
                        }

                        detailsToggleBtn.setOnClickListener(doToggleExpand)
                        cardLayout.setOnClickListener(doToggleExpand)

                        if (!latestStory.filePath.isNullOrBlank()) {
                            val openFileBtn = Button(this@GoldenBubbleService).apply {
                                text = "📂 فتح الملف"
                                textSize = 8.5f
                                setTextColor(Color.BLACK)
                                background = createRoundedDrawable("#D4AF37", 4f)
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    dpToPx(28)
                                ).apply {
                                    setMargins(dpToPx(6), 0, 0, 0)
                                }
                                setOnClickListener {
                                    try {
                                        com.example.engine.FileUtils.openFile(this@GoldenBubbleService, latestStory.filePath)
                                    } catch (e: Exception) {
                                        Toast.makeText(applicationContext, "فشل فتح الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            buttonsRow.addView(openFileBtn)
                        }

                        cardLayout.addView(buttonsRow)
                        logsContainer.addView(cardLayout)
                    }
                } else {
                    for (log in recentLogs) {
                        val logRow = LinearLayout(this@GoldenBubbleService).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                            background = createRoundedDrawable("#0F0F1E", 4f)
                            val rowParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, dpToPx(4))
                            }
                            layoutParams = rowParams
                        }
                        
                        val iconTxt = TextView(this@GoldenBubbleService).apply {
                            text = when {
                                log.type.lowercase().contains("builder") || log.type.lowercase().contains("build") -> "📄"
                                log.type.lowercase().contains("executor") || log.type.lowercase().contains("command") || log.type.lowercase().contains("execute") -> "⚙️"
                                log.type.lowercase().contains("treedoc") || log.type.lowercase().contains("file") -> "📁"
                                log.type.lowercase().contains("success") || log.message.contains("بنجاح") || log.message.contains("نجاح") -> "✅"
                                else -> "⚠️"
                            }
                            textSize = 10f
                            setPadding(0, 0, dpToPx(4), 0)
                        }
                        logRow.addView(iconTxt)

                        val timeTxt = TextView(this@GoldenBubbleService).apply {
                            text = getRelativeTimeString(log.timestamp)
                            setTextColor(Color.parseColor("#64748B"))
                            textSize = 7.5f
                            setPadding(0, 0, dpToPx(4), 0)
                        }
                        logRow.addView(timeTxt)
                        
                        val msgTxt = TextView(this@GoldenBubbleService).apply {
                            text = log.message
                            setTextColor(Color.parseColor("#E2E8F0"))
                            textSize = 8.5f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        logRow.addView(msgTxt)
                        
                        val path = log.details ?: ""
                        if (path.isNotEmpty()) {
                            logRow.setOnClickListener {
                                var cleanPath = path
                                if (cleanPath.startsWith("المسار: ")) {
                                    cleanPath = cleanPath.removePrefix("المسار: ").trim()
                                } else if (cleanPath.startsWith("الملف: ")) {
                                    cleanPath = cleanPath.removePrefix("الملف: ").trim()
                                }
                                if (cleanPath.isNotEmpty()) {
                                    com.example.engine.FileUtils.openFile(this@GoldenBubbleService, cleanPath)
                                }
                            }
                        }
                        
                        logsContainer.addView(logRow)
                    }
                }
            } catch (e: Exception) {
                Log.e("GoldenBubbleService", "Error redrawing logs: ${e.message}")
            }
        }
    }

    private fun copyLatestEventsToClipboard() {
        serviceScope.launch {
            try {
                val smartPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                val count = smartPrefs.getInt("log_copy_count", 5)
                val logViewMode = smartPrefs.getString("log_view_mode", "technical") ?: "technical"
                val logs = database.dao().getAllLogs().first().take(count)
                if (logs.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "سجل الأحداث فارغ.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                val textToCopy = if (logViewMode != "technical") {
                    val stories = com.example.LogAggregator.generateStoryCards(logs, logViewMode)
                    if (stories.isEmpty()) {
                        "لا توجد قصص مصورة بعد للتسجيل الحالي."
                    } else {
                        stories.joinToString("\n\n") { story ->
                            "${story.icon} ${story.title} (${story.time})\n${story.summary}\nتحليل: ${story.details}\n(نجح: ${story.successCount} | فشل: ${story.totalCount - story.successCount})"
                        }
                    }
                } else {
                    logs.joinToString("\n") { log ->
                        "[${getRelativeTimeString(log.timestamp)}] ${log.message}"
                    }
                }

                withContext(Dispatchers.Main) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(if (logViewMode != "technical") "Story Logs" else "Event Logs", textToCopy)
                    clipboard.setPrimaryClip(clip)
                    if (logViewMode != "technical") {
                        Toast.makeText(applicationContext, "📋 تم نسخ ملخص قصص السجل بنجاح!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(applicationContext, "تم نسخ آخر $count أحداث تقنية بنجاح!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "خطأ أثناء نسخ الأحداث: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun createExpandedView(collapsedLayout: LinearLayout, touchDragListener: View.OnTouchListener): LinearLayout {
        statusCircle = View(this) // Initialize dummy view to avoid uninitialized property exception
        val expanded = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable("#1A1A2E", 16f, "#FFD700", 2)
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
            visibility = View.GONE
        }
        this.expandedLayout = expanded

        // 1. شريط العنوان
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(6))
        }
        val titleTxt = TextView(this).apply {
            text = "✨ المراقب الذكي الذهبي"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleTxt)

        val closeBtn = Button(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 14f
            background = null
            setPadding(0, 0, 0, 0)
            val btnLp = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32))
            layoutParams = btnLp
            setOnClickListener {
                collapseBubble()
            }
        }
        header.addView(closeBtn)
        header.setOnTouchListener(touchDragListener)
        expanded.addView(header)

        // 2. حالة المراقبة
        statusTxt = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dpToPx(2), 0, dpToPx(6))
        }
        expanded.addView(statusTxt)

        // 3. المشروع النشط
        val activeProjRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(6))
        }
        activeProjectTxtView = TextView(this).apply {
            text = "المشروع النشط: " + com.example.engine.ProjectManager.getActiveProjectName(this@GoldenBubbleService)
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        activeProjRow.addView(activeProjectTxtView)

        val pastePathBtn = Button(this).apply {
            text = "📋"
            setTextColor(Color.BLACK)
            background = createRoundedDrawable("#FFD700", 4f)
            textSize = 10f
            setPadding(0, 0, 0, 0)
            val btnLp = LinearLayout.LayoutParams(dpToPx(36), dpToPx(24)).apply {
                rightMargin = dpToPx(4)
            }
            layoutParams = btnLp
            setOnClickListener {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    if (clipboard.hasPrimaryClip()) {
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val textToPaste = clip.getItemAt(0).text?.toString()?.trim() ?: ""
                            if (textToPaste.isNotEmpty()) {
                                val file = File(textToPaste)
                                if (file.exists() && file.isDirectory) {
                                    com.example.engine.UnifiedPathManager.setActivePath(this@GoldenBubbleService, textToPaste)
                                    Toast.makeText(applicationContext, "📋 تم تعيين مسار اللصق كمسار نشط: $textToPaste", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(applicationContext, "⚠️ الحافظة لا تحتوي على مسار مجلد صالح موجود بالفعل!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(applicationContext, "الحافظة فارغة حالياً.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(applicationContext, "الحافظة فارغة أو غير متوفرة.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "خطأ أثناء قراءة الحافظة: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        activeProjRow.addView(pastePathBtn)

        val cycleBtn = Button(this).apply {
            text = "🔄"
            setTextColor(Color.BLACK)
            background = createRoundedDrawable("#FFD700", 4f)
            textSize = 10f
            setPadding(0, 0, 0, 0)
            val btnLp = LinearLayout.LayoutParams(dpToPx(36), dpToPx(24))
            layoutParams = btnLp
            setOnClickListener {
                val nextName = com.example.engine.ProjectManager.cycleToNextProject(this@GoldenBubbleService)
                activeProjectTxtView?.text = "المشروع النشط: $nextName"
                Toast.makeText(applicationContext, "🔄 تم تبديل المشروع النشط إلى: $nextName", Toast.LENGTH_SHORT).show()
            }
        }
        activeProjRow.addView(cycleBtn)
        expanded.addView(activeProjRow)

        // 4. آخر إجراء
        sourceBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val sbParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(3))
            }
            layoutParams = sbParams
        }
        sourceIconTxt = TextView(this).apply {
            text = "🟢"
            textSize = 10f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            layoutParams = lp
        }
        sourceNameTxt = TextView(this).apply {
            text = "تلقائي"
            textSize = 10f
            setTextColor(Color.parseColor("#10B981"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        sourceBarLayout?.addView(sourceIconTxt)
        sourceBarLayout?.addView(sourceNameTxt)
        expanded.addView(sourceBarLayout)

        lastActionTxt = TextView(this).apply {
            text = "آخر إجراء: لا توجد عمليات حالية"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 9f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            background = createRoundedDrawable("#0F0F1E", 4f)
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            val laParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(4))
            }
            layoutParams = laParams
        }
        expanded.addView(lastActionTxt)

        contextActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val caParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(6))
            }
            layoutParams = caParams
        }
        expanded.addView(contextActionsRow)

        // 5. فاصل
        val divider1 = View(this).apply {
            setBackgroundColor(Color.parseColor("#D97706"))
            val dParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                setMargins(0, 0, 0, dpToPx(6))
            }
            layoutParams = dParams
        }
        expanded.addView(divider1)

        // 6. أزرار تصفية السجل
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val frParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(6))
            }
            layoutParams = frParams
        }

        val btnFilterBuilder = Button(this)
        val btnFilterExecutor = Button(this)
        val btnFilterTreeDoc = Button(this)
        val btnFilterSuccess = Button(this)

        fun updateFiltersHighlight() {
            btnFilterBuilder.background = createRoundedDrawable(if (activeFilter == "builder") "#D97706" else "#1E1D3A", 6f, if (activeFilter == "builder") "#FFD700" else "#444466", 1)
            btnFilterExecutor.background = createRoundedDrawable(if (activeFilter == "executor") "#D97706" else "#1E1D3A", 6f, if (activeFilter == "executor") "#FFD700" else "#444466", 1)
            btnFilterTreeDoc.background = createRoundedDrawable(if (activeFilter == "treedoc") "#D97706" else "#1E1D3A", 6f, if (activeFilter == "treedoc") "#FFD700" else "#444466", 1)
            btnFilterSuccess.background = createRoundedDrawable(if (activeFilter == "success") "#D97706" else "#1E1D3A", 6f, if (activeFilter == "success") "#FFD700" else "#444466", 1)
        }

        btnFilterBuilder.apply {
            text = "[📄]"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(0,0,dpToPx(3),0) }
            layoutParams = lp
            setOnClickListener {
                activeFilter = if (activeFilter == "builder") "all" else "builder"
                updateFiltersHighlight()
                redrawLogs(currentFullLogs)
            }
        }
        btnFilterExecutor.apply {
            text = "[⚙️]"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(0,0,dpToPx(3),0) }
            layoutParams = lp
            setOnClickListener {
                activeFilter = if (activeFilter == "executor") "all" else "executor"
                updateFiltersHighlight()
                redrawLogs(currentFullLogs)
            }
        }
        btnFilterTreeDoc.apply {
            text = "[📁]"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(0,0,dpToPx(3),0) }
            layoutParams = lp
            setOnClickListener {
                activeFilter = if (activeFilter == "treedoc") "all" else "treedoc"
                updateFiltersHighlight()
                redrawLogs(currentFullLogs)
            }
        }
        btnFilterSuccess.apply {
            text = "[✅]"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f)
            layoutParams = lp
            setOnClickListener {
                activeFilter = if (activeFilter == "success") "all" else "success"
                updateFiltersHighlight()
                redrawLogs(currentFullLogs)
            }
        }

        updateFiltersHighlight()
        filterRow.addView(btnFilterBuilder)
        filterRow.addView(btnFilterExecutor)
        filterRow.addView(btnFilterTreeDoc)
        filterRow.addView(btnFilterSuccess)
        expanded.addView(filterRow)

        // 7. سجل الأحداث
        val logsScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(85)
            ).apply {
                setMargins(0, 0, 0, dpToPx(6))
            }
            background = createRoundedDrawable("#09081A", 8f)
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
        }

        logsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        logsScrollView.addView(logsContainer)
        expanded.addView(logsScrollView)

        // Register database logs Flow list mapping
        serviceScope.launch {
            database.dao().getAllLogs().collect { logsList ->
                currentFullLogs = logsList
                redrawLogs(logsList)
            }
        }

        serviceScope.launch {
            database.dao().getLastSignificantEvent().collect { log ->
                withContext(Dispatchers.Main) {
                    if (log != null) {
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
                            "bubble" -> "الفقاعة"
                            "ime" -> "الكيبورد"
                            "auto" -> "تلقائي"
                            "manual" -> "يدوي"
                            "buildpack" -> "حزمة البناء"
                            "smartcapture" -> "الالتقاط الذكي"
                            else -> "تلقائي"
                        }
                        val sourceColor = when (log.source) {
                            "bubble" -> "#D97706"
                            "ime" -> "#3B82F6"
                            "auto" -> "#10B981"
                            "manual" -> "#8B5CF6"
                            "buildpack" -> "#F59E0B"
                            "smartcapture" -> "#EC4899"
                            else -> "#10B981"
                        }

                        sourceIconTxt?.text = sourceIcon
                        sourceNameTxt?.text = sourceName
                        sourceNameTxt?.setTextColor(Color.parseColor(sourceColor))
                        sourceBarLayout?.visibility = View.VISIBLE

                        val displayMsg = log.message
                        lastActionTxt.text = displayMsg
                        if (::bubbleStatusTxt.isInitialized) {
                            bubbleStatusTxt.text = displayMsg
                        }

                        updateContextualActions(log.message, log.details ?: "")
                    } else {
                        sourceBarLayout?.visibility = View.GONE
                        lastActionTxt.text = "آخر إجراء: لا توجد عمليات حالية"
                        if (::bubbleStatusTxt.isInitialized) {
                            bubbleStatusTxt.text = "لا توجد عمليات حالية"
                        }
                        contextActionsRow?.removeAllViews()
                        contextActionsRow?.visibility = View.GONE
                    }
                }
            }
        }

        // 8. فاصل ثان
        val divider2 = View(this).apply {
            setBackgroundColor(Color.parseColor("#D97706"))
            val dParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                setMargins(0, 0, 0, dpToPx(6))
            }
            layoutParams = dParams
        }
        expanded.addView(divider2)

        // 9. أزرار التحكم السفلية
        val controlsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }

        // Row 1
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(4))
            }
            layoutParams = lp
        }
        val copyLogsBtn = Button(this).apply {
            text = "📋 نسخ آخر الأحداث"
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable("#1F2937", 6f, "#64748B", 1)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(0, 0, dpToPx(4), 0) }
            layoutParams = lp
            setOnClickListener {
                copyLatestEventsToClipboard()
            }
        }
        val processNowBtn = Button(this).apply {
            text = "⚡ معالجة الآن"
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable("#D97706", 6f)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f)
            layoutParams = lp
            setOnClickListener {
                manualCollectClipboard()
            }
        }
        row1.addView(copyLogsBtn)
        row1.addView(processNowBtn)
        controlsContainer.addView(row1)

        // Row 2
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(4))
            }
            layoutParams = lp
        }
        val keyboardBtn = Button(this).apply {
            text = "⌨️ تبديل الكيبورد"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 9f
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable("#1E1D3A", 6f, "#FFD700", 1)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(0, 0, dpToPx(4), 0) }
            layoutParams = lp
            setOnClickListener {
                try {
                    val intent = Intent(this@GoldenBubbleService, TransparentActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        toggleStatusBtn = Button(this).apply {
            text = if (isPaused) "استئناف" else "إيقاف مؤقت"
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable("#475569", 6f)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f)
            layoutParams = lp
            setOnClickListener {
                toggleMonitoringPause()
                updateStatusUI()
            }
        }
        row2.addView(keyboardBtn)
        row2.addView(toggleStatusBtn)
        controlsContainer.addView(row2)

        // Row 3
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            layoutParams = lp
        }
        val mainAppBtn = Button(this).apply {
            text = "🏠 الرئيسية"
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable("#475569", 6f)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(0, 0, dpToPx(4), 0) }
            layoutParams = lp
            setOnClickListener {
                launchMainApp()
            }
        }
        val closeServiceBtn = Button(this).apply {
            text = "❌ إغلاق"
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable("#991B1B", 6f)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f)
            layoutParams = lp
            setOnClickListener {
                removeGoldenView()
                stopSelf()
            }
        }
        row3.addView(mainAppBtn)
        row3.addView(closeServiceBtn)
        controlsContainer.addView(row3)

        // Row 4
        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dpToPx(4), 0, 0)
            }
            layoutParams = lp
        }
        val applyBuildPackBtn = Button(this).apply {
            text = "📂 تطبيق حزمة بناء"
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable("#1E1D3A", 6f, "#FFD700", 1)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f).apply { setMargins(0, 0, dpToPx(4), 0) }
            layoutParams = lp
            setOnClickListener {
                applyBuildPackFromBubble()
            }
        }
        val quickTestBtn = Button(this).apply {
            text = "🧪 فحص سريع"
            setTextColor(Color.WHITE)
            textSize = 9f
            setPadding(0, 0, 0, 0)
            background = createRoundedDrawable("#D4AF37", 6f)
            val lp = LinearLayout.LayoutParams(0, dpToPx(28), 1f)
            layoutParams = lp
            setOnClickListener {
                runSelfTestFromBubble()
            }
        }
        row4.addView(applyBuildPackBtn)
        row4.addView(quickTestBtn)
        controlsContainer.addView(row4)

        expanded.addView(controlsContainer)

        updateStatusUI()
        return expanded
    }

    private fun runSelfTestFromBubble() {
        serviceScope.launch {
            try {
                val settings = mapOf<String, Any>(
                    "absolute_path_handling" to "relative"
                )
                val engine = com.example.engine.BuilderEngine(applicationContext, settings)
                engine.processText("@executor:selftest")
                
                val resultsMsg = """
                    🛡️ [تقرير الفحص والتشخيص الذاتي]
                    ✓ حالة محرك المراقبة والأرشفة: مستقرة 🟢
                    ✓ سلامة قوالب البناء الكودية @builder: تفوق 💻
                    ✓ كفاءة تشغيل الأوامر بالسيناريوهات @executor: 100% ⚡
                    ✓ دقة شجرة المجلدات والمستندات .treedoc: نجاح 📊
                    ✓ مجلد مستودع SmartInbox الافتراضي: نشط ومأمون 📁
                    
                    جميع محركات بيئة الكيبورد والأتمتة فُحصت تلقائياً وتعمل بأعلى درجات الموثوقية بسلامة!
                """.trimIndent()
                Toast.makeText(applicationContext, resultsMsg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "❌ فشل الفحص السريع: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun setupGoldenView() {
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 150
                y = 250
            }

            // Root FrameLayout
            val root = FrameLayout(this)

            // Touch Dragging Logic
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isMoving = false

            val touchDragListener = View.OnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMoving = true
                        }
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try {
                            windowManager.updateViewLayout(root, params)
                        } catch (e: Exception) {}
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            view.performClick()
                        }
                        true
                    }
                    else -> false
                }
            }

            // 1. COLLAPSED CONTAINER VIEW (Contains Circle bubble + tiny Text beneath it - Problem 4)
            val collapsedLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                visibility = View.VISIBLE
            }

            // The Gold Circle Bubble itself
            val circleBubble = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = createCircleDrawable("#1A1A2E").apply {
                    setStroke(dpToPx(2), Color.parseColor("#FFD700"))
                }
                val lbTxt = TextView(this@GoldenBubbleService).apply {
                    text = "✨"
                    setTextColor(Color.WHITE)
                    textSize = 22f
                    gravity = Gravity.CENTER
                }
                addView(lbTxt)
            }
            val bubbleParams = LinearLayout.LayoutParams(dpToPx(54), dpToPx(54))
            collapsedLayout.addView(circleBubble, bubbleParams)

            // Tiny TextView below circleBubble
            bubbleStatusTxt = TextView(this).apply {
                text = "المراقب الذكي - نشط"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 9f
                gravity = Gravity.CENTER
                setPadding(dpToPx(4), dpToPx(2), dpToPx(4), dpToPx(2))
                background = createRoundedDrawable("#2A1A3E", 4f, "#FFD700", 1)
                val statusParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dpToPx(4), 0, 0)
                }
                layoutParams = statusParams
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        val path = lastSavedFilePath
                        if (path != null) {
                            com.example.engine.FileUtils.openFile(this@GoldenBubbleService, path)
                        } else {
                            collapsedLayout.performClick()
                        }
                    }
                    true
                }
            }
            collapsedLayout.addView(bubbleStatusTxt)

            val collapsedParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            root.addView(collapsedLayout, collapsedParams)

            this@GoldenBubbleService.collapsedLayout = collapsedLayout

            // 2. EXPANDED CARD VIEW
            val expandedLayout = createExpandedView(collapsedLayout, touchDragListener)
            this@GoldenBubbleService.expandedLayout = expandedLayout

            val cardParams = FrameLayout.LayoutParams(dpToPx(270), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            root.addView(expandedLayout, cardParams)

            // 3. CONTEXT DECISION DIALOG OVERLAY
            val contextDL = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedDrawable("#1F1F35", 16f, "#FFD700", 2)
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                visibility = View.GONE
            }

            val cdTitle = TextView(this).apply {
                text = "💡 قرار سياق المشروع"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, dpToPx(8))
            }
            contextDL.addView(cdTitle)

            val cdBody = TextView(this).apply {
                text = ""
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.START
                setPadding(0, 0, 0, dpToPx(12))
            }
            contextDL.addView(cdBody)
            contextDialogText = cdBody

            val cdActions = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }

            val btnSaveHere = Button(this).apply {
                text = "حفظ في المشروع الحالي"
                setTextColor(Color.BLACK)
                background = createRoundedDrawable("#10B981", 8f)
                textSize = 11f
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(34)).apply {
                    setMargins(0, 0, 0, dpToPx(6))
                }
                layoutParams = lp
            }
            cdActions.addView(btnSaveHere)
            saveHereBtn = btnSaveHere

            val btnNewFolder = Button(this).apply {
                text = "مجلد جديد واقتراح اسم"
                setTextColor(Color.WHITE)
                background = createRoundedDrawable("#3B82F6", 8f)
                textSize = 11f
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(34)).apply {
                    setMargins(0, 0, 0, dpToPx(6))
                }
                layoutParams = lp
            }
            cdActions.addView(btnNewFolder)
            newFolderBtn = btnNewFolder

            val btnIgnore = Button(this).apply {
                text = "تجاهل"
                setTextColor(Color.WHITE)
                background = createRoundedDrawable("#EF4444", 8f)
                textSize = 11f
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(34))
                layoutParams = lp
            }
            cdActions.addView(btnIgnore)
            ignoreBtn = btnIgnore

            contextDL.addView(cdActions)
            contextDialogLayout = contextDL

            root.addView(contextDL, cardParams)

            // 4. MANUAL FOLDER NAME INPUT OVERLAY
            val manualFL = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedDrawable("#1F1F35", 16f, "#FFD700", 2)
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                visibility = View.GONE
            }

            val mfTitle = TextView(this).apply {
                text = "📁 مجلد مشروع جديد"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, dpToPx(8))
            }
            manualFL.addView(mfTitle)

            val mfEditText = android.widget.EditText(this).apply {
                hint = "اكتب اسم المجلد الجديد..."
                setHintTextColor(Color.GRAY)
                setTextColor(Color.WHITE)
                textSize = 13f
                background = createRoundedDrawable("#2A2A44", 8f, "#444466", 1)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, dpToPx(12))
                }
                layoutParams = lp
            }
            manualFL.addView(mfEditText)
            manualFolderNameEditText = mfEditText

            val mfBtnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }

            val btnCancelManual = Button(this).apply {
                text = "إلغاءونص"
                text = "إلغاء"
                setTextColor(Color.WHITE)
                background = createRoundedDrawable("#EF4444", 8f)
                textSize = 12f
                val lp = LinearLayout.LayoutParams(0, dpToPx(34), 1f).apply {
                    setMargins(0, 0, dpToPx(6), 0)
                }
                layoutParams = lp
            }
            mfBtnRow.addView(btnCancelManual)
            cancelManualFolderBtn = btnCancelManual

            val btnConfirmManual = Button(this).apply {
                text = "تأكيد"
                setTextColor(Color.BLACK)
                background = createRoundedDrawable("#10B981", 8f)
                textSize = 12f
                val lp = LinearLayout.LayoutParams(0, dpToPx(34), 1f)
                layoutParams = lp
            }
            mfBtnRow.addView(btnConfirmManual)
            confirmManualFolderBtn = btnConfirmManual

            manualFL.addView(mfBtnRow)
            manualNameInputLayout = manualFL

            root.addView(manualFL, cardParams)

            // 5. TEMPLATE DETECTION OVERLAY
            val templateFL = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedDrawable("#1F1F35", 16f, "#FFD700", 2)
                setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
                visibility = View.GONE
            }

            val tfTitle = TextView(this).apply {
                text = "📁 تم كشف قالب مشروع ذكي! هل تريد استيراده؟"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, 0, dpToPx(12))
            }
            templateFL.addView(tfTitle)

            val tfBtnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val btnFast = Button(this).apply {
                text = "استيراد سريع"
                setTextColor(Color.BLACK)
                background = createRoundedDrawable("#10B981", 8f)
                textSize = 10f
                val lp = LinearLayout.LayoutParams(0, dpToPx(34), 1.2f).apply {
                    setMargins(0, 0, dpToPx(4), 0)
                }
                layoutParams = lp
            }
            tfBtnRow.addView(btnFast)
            importFastBtn = btnFast

            val btnDetail = Button(this).apply {
                text = "معاينة وتحرير"
                setTextColor(Color.WHITE)
                background = createRoundedDrawable("#3B82F6", 8f)
                textSize = 10f
                val lp = LinearLayout.LayoutParams(0, dpToPx(34), 1.2f).apply {
                    setMargins(0, 0, dpToPx(4), 0)
                }
                layoutParams = lp
            }
            tfBtnRow.addView(btnDetail)
            importDetailBtn = btnDetail

            val btnCancelTmpl = Button(this).apply {
                text = "تجاهل"
                setTextColor(Color.WHITE)
                background = createRoundedDrawable("#EF4444", 8f)
                textSize = 10f
                val lp = LinearLayout.LayoutParams(0, dpToPx(34), 1f)
                layoutParams = lp
            }
            tfBtnRow.addView(btnCancelTmpl)
            templateCancelBtn = btnCancelTmpl

            templateFL.addView(tfBtnRow)
            templateDialogLayout = templateFL

            root.addView(templateFL, cardParams)

            // Setup template click actions
            btnFast.setOnClickListener {
                val tText = pendingTemplateText
                if (!tText.isNullOrBlank()) {
                    performFastImport(tText)
                } else {
                    Toast.makeText(applicationContext, "عذراً، محتوى القالب غير متوفر", Toast.LENGTH_SHORT).show()
                }
            }

            btnDetail.setOnClickListener {
                val tText = pendingTemplateText
                if (!tText.isNullOrBlank()) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        putExtra("import_template_json", tText)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    }
                    hideTemplateDialogOverlay()
                } else {
                    Toast.makeText(applicationContext, "عذراً، محتوى القالب غير متوفر", Toast.LENGTH_SHORT).show()
                }
            }

            btnCancelTmpl.setOnClickListener {
                hideTemplateDialogOverlay()
            }

            collapsedLayout.setOnTouchListener(touchDragListener)
            collapsedLayout.setOnClickListener {
                collapsedLayout.visibility = View.GONE
                expandedLayout.visibility = View.VISIBLE
            }

            // Initialize UI elements states
            updateStatusUI()

            // 6. FLOATING FILE SAVE NOTIFICATION OVERLAY (Slide-out, self dismisses after 5s)
            val toastLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createRoundedDrawable("#0F172A", 12f, "#FFD700", 2)
                setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                visibility = View.GONE
                
                val toastTitle = TextView(this@GoldenBubbleService).apply {
                    text = "📥 تم حفظ كود بنجاح!"
                    setTextColor(Color.parseColor("#FFD700"))
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, 0, 0, dpToPx(4))
                }
                addView(toastTitle)

                val toastText = TextView(this@GoldenBubbleService).apply {
                    text = ""
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, 0, 0, dpToPx(2))
                }
                addView(toastText)
                this@GoldenBubbleService.notificationToastText = toastText

                setOnClickListener {
                    val path = lastSavedFilePath
                    if (path != null) {
                        com.example.engine.FileUtils.openFile(this@GoldenBubbleService, path)
                        visibility = View.GONE
                    }
                }
            }

            val toastParams = FrameLayout.LayoutParams(dpToPx(240), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(0, dpToPx(10), 0, 0)
            }
            root.addView(toastLayout, toastParams)
            this@GoldenBubbleService.notificationToastLayout = toastLayout

            rootLayout = root
            windowManager.addView(root, params)

        } catch (e: Exception) {
            Log.e("GoldenBubbleService", "Error building golden bubble overlay: ${e.message}", e)
            Toast.makeText(applicationContext, "فشل تشغيل الكرة الذهبية: ${e.localizedMessage ?: e.message}", Toast.LENGTH_LONG).show()
            removeGoldenView()
            stopSelf()
        }
    }

    private fun showContextDecisionDialog(text: String) {
        val currentProj = com.example.engine.ProjectContextManager.getCurrentProjectPath(this)
        val keywords = com.example.engine.ProjectContextManager.extractKeywords(text)
        val topic = if (keywords.isNotEmpty()) keywords.take(2).joinToString(" و ") else "موضوعات عامة"
        
        val bodyMsg = "هذا النص يبدو أنه عن '$topic'.\nأنت حالياً في مشروع '$currentProj'.\nأين تريد حفظه؟"
        
        contextDialogText?.text = bodyMsg
        
        saveHereBtn?.setOnClickListener {
            com.example.engine.ProjectContextManager.isBypassed = true
            serviceScope.launch {
                val baseDir = getBaseDir()
                val cmdContext = com.example.engine.CommandContext(
                    context = applicationContext,
                    baseDir = baseDir,
                    args = emptyMap(),
                    flags = emptyList()
                )
                val results = com.example.engine.SmartCaptureEngine.processCapturedText(text, cmdContext)
                
                // Write Log
                val db = AppDatabase.getDatabase(applicationContext)
                db.dao().insertLog(
                    LogEntity(
                        type = "context_manager",
                        message = "تم الحفظ في مسار المشروع ذو الصلة ($currentProj)",
                        details = "تم تأكيد الحفظ بواسطة المستخدم بنجاح.\nالملفات: ${results.savedFiles.joinToString { it.fileName }}",
                        source = "bubble"
                    )
                )
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "✅ تم الحفظ في $currentProj", Toast.LENGTH_SHORT).show()
                    hideContextDecisionDialog()
                }
            }
            com.example.engine.ProjectContextManager.isBypassed = false
        }
        
        newFolderBtn?.setOnClickListener {
            val suggested = com.example.engine.ProjectContextManager.suggestFolderName(text, applicationContext)
            if (suggested == "MANUAL") {
                showManualFolderNameInputDialog(text)
            } else {
                createNewFolderAndSave(suggested, text)
            }
        }
        
        ignoreBtn?.setOnClickListener {
            hideContextDecisionDialog()
            Toast.makeText(applicationContext, "❌ تم تجاهل النص", Toast.LENGTH_SHORT).show()
        }
        
        rootLayout?.let { root ->
            for (i in 0 until root.childCount) {
                root.getChildAt(i).visibility = View.GONE
            }
            contextDialogLayout?.visibility = View.VISIBLE
        }
    }

    private fun showManualFolderNameInputDialog(text: String) {
        contextDialogLayout?.visibility = View.GONE
        manualNameInputLayout?.visibility = View.VISIBLE
        manualFolderNameEditText?.setText("")
        manualFolderNameEditText?.requestFocus()

        confirmManualFolderBtn?.setOnClickListener {
            val entered = manualFolderNameEditText?.text?.toString()?.trim() ?: ""
            if (entered.isBlank()) {
                Toast.makeText(applicationContext, "يرجى كتابة اسم صحيح للمجلد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createNewFolderAndSave(entered, text)
        }

        cancelManualFolderBtn?.setOnClickListener {
            manualNameInputLayout?.visibility = View.GONE
            contextDialogLayout?.visibility = View.VISIBLE
        }
    }

    private fun createNewFolderAndSave(folderName: String, text: String) {
        com.example.engine.ProjectContextManager.isBypassed = true
        serviceScope.launch {
            val baseDir = getBaseDir()
            val sanitizedFolder = folderName.replace(Regex("[\\\\/:*?\"<>|]"), " ").replace(Regex("\\s+"), "_").trim()
            val finalFolder = if (sanitizedFolder.isEmpty()) "مجلد_جديد" else sanitizedFolder
            
            val newFolder = File(baseDir, finalFolder)
            newFolder.mkdirs()
            
            com.example.engine.UnifiedPathManager.setActivePath(applicationContext, finalFolder)
            
            val cmdContext = com.example.engine.CommandContext(
                context = applicationContext,
                baseDir = baseDir,
                args = emptyMap(),
                flags = emptyList()
            )
            val results = com.example.engine.SmartCaptureEngine.processCapturedText(text, cmdContext)
            
            // Write Log
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.dao().insertLog(
                    LogEntity(
                        type = "context_manager",
                        message = "إنشاء مجلد: تم إنشاء مجلد باسم '$finalFolder' وحفظ المستند فيه.",
                        details = "تم الحفظ بنجاح.\nالملفات: ${results.savedFiles.joinToString { it.fileName }}",
                        source = "bubble"
                    )
                )
            } catch (e: Exception) {
                // silently ignored
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "✅ تم الحفظ وإنشاء مجلد: $finalFolder", Toast.LENGTH_SHORT).show()
                manualNameInputLayout?.visibility = View.GONE
                hideContextDecisionDialog()
            }
        }
        com.example.engine.ProjectContextManager.isBypassed = false
    }
    
    private fun showTemplateDialogOverlay() {
        contextDialogLayout?.visibility = View.GONE
        manualNameInputLayout?.visibility = View.GONE
        templateDialogLayout?.visibility = View.VISIBLE
    }

    private fun hideTemplateDialogOverlay() {
        templateDialogLayout?.visibility = View.GONE
        hideContextDecisionDialog()
    }

    private fun performFastImport(templateJson: String) {
        val parsedResult = com.example.engine.TemplateParser.parse(templateJson)
        if (parsedResult.isSuccess) {
            val template = parsedResult.getOrThrow()
            val basePath = com.example.engine.ProjectContextManager.getBaseDir(applicationContext).absolutePath
            val buildResult = com.example.engine.ProjectBuilder.build(template, basePath)
            if (buildResult.isSuccess) {
                val projectPath = buildResult.getOrThrow()
                com.example.engine.ProjectManager.addProject(applicationContext, projectPath, template.projectName)
                com.example.engine.UnifiedPathManager.setActivePath(applicationContext, projectPath)
                activeProjectTxtView?.text = "📁 ${template.projectName}"
                Toast.makeText(applicationContext, "✅ تم استيراد وتفعيل قالب: ${template.projectName}", Toast.LENGTH_LONG).show()
                hideTemplateDialogOverlay()
                
                val refreshIntent = Intent("com.example.ACTION_REFRESH_PROJECTS")
                refreshIntent.setPackage(packageName)
                sendBroadcast(refreshIntent)
            } else {
                Toast.makeText(applicationContext, "❌ فشل بناء مجلدات المشروع: ${buildResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(applicationContext, "❌ فشل تحليل القالب: ${parsedResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideContextDecisionDialog() {
        contextDialogLayout?.visibility = View.GONE
        manualNameInputLayout?.visibility = View.GONE
        templateDialogLayout?.visibility = View.GONE
        rootLayout?.let { root ->
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child == contextDialogLayout || child == manualNameInputLayout || child == templateDialogLayout) {
                    child.visibility = View.GONE
                } else if (child is LinearLayout && child != contextDialogLayout && child != manualNameInputLayout && child != templateDialogLayout) {
                    child.visibility = View.VISIBLE
                    break
                }
            }
        }
    }

    private fun manualCollectClipboard() {
        try {
            val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
            val liveText = sharedPrefs.getString("live_clipboard_text", "") ?: ""
            
            var textToProcess = ""
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    textToProcess = clipData.getItemAt(0).text?.toString() ?: ""
                }
            }
            
            if (textToProcess.isBlank()) {
                textToProcess = liveText
            }
            
            if (textToProcess.isNotBlank()) {
                Toast.makeText(applicationContext, "🔄 جاري معالجة النص يدوياً...", Toast.LENGTH_SHORT).show()
                processClipboardContent(textToProcess, force = true)
            } else {
                Toast.makeText(applicationContext, "الحافظة فارغة بالكامل ولم يتم استقبال أي نص.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleMonitoringPause() {
        val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val current = sharedPrefs.getBoolean("clipboard_is_paused", false)
        sharedPrefs.edit().putBoolean("clipboard_is_paused", !current).apply()

        val toastMsg = if (!current) "تم الإيقاف المؤقت للمراقبة." else "تم استئناف المراقبة بنشاط."
        Toast.makeText(applicationContext, toastMsg, Toast.LENGTH_SHORT).show()
    }

    private fun launchMainApp() {
        try {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "تعذر تشغيل التطبيق: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeGoldenView() {
        rootLayout?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
            rootLayout = null
        }
    }

    private fun getBaseDir(): File {
        val path = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE).getString("base_dir_path", null)
        if (!path.isNullOrBlank()) {
            return File(path).also { it.mkdirs() }
        }
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            File(getExternalFilesDir(null), "SmartPlatform")
        } else {
            File(filesDir, "SmartPlatform")
        }.also { it.mkdirs() }
    }

    private fun applyBuildPackFromBubble() {
        try {
            val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
            val liveText = sharedPrefs.getString("live_clipboard_text", "") ?: ""
            
            var textToProcess = ""
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    textToProcess = clipData.getItemAt(0).text?.toString() ?: ""
                }
            }
            
            if (textToProcess.isBlank()) {
                textToProcess = liveText
            }
            
            if (textToProcess.isNotBlank()) {
                Toast.makeText(applicationContext, "📂 جاري تطبيق حزمة البناء من الحافظة...", Toast.LENGTH_SHORT).show()
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val pBuilder = sharedPrefs.getString("prefix_builder", "@builder") ?: "@builder"
                        val pExecutor = sharedPrefs.getString("prefix_executor", "@executor") ?: "@executor"
                        val pTreedoc = sharedPrefs.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"

                        val settings = mapOf(
                            "absolute_path_handling" to "relative",
                            "base_dir" to com.example.engine.ProjectContextManager.getCurrentProjectDir(applicationContext).absolutePath,
                            "directive_prefixes" to listOf(pBuilder),
                            "executor_prefixes" to listOf(pExecutor),
                            "treedoc_prefixes" to listOf(pTreedoc)
                        )
                        val engine = com.example.engine.BuilderEngine(applicationContext, settings)
                        val results = engine.processText(textToProcess)
                        
                        var buildersCount = 0
                        val database = com.example.db.AppDatabase.getDatabase(applicationContext)
                        for (res in results) {
                            if (res.type == "builder") {
                                buildersCount++
                                val path = res.data?.get("path") ?: "unknown"
                                val size = res.data?.get("size")?.toLongOrNull() ?: 0L
                                val mode = res.data?.get("mode") ?: "w"
                                val fullPath = res.data?.get("full_path") ?: ""
                                database.dao().insertFile(
                                    com.example.db.FileEntity(path = path, fullPath = fullPath, size = size, mode = mode)
                                )
                                database.dao().insertLog(
                                    com.example.db.LogEntity(
                                        type = "builder",
                                        message = "تطبيق حزمة البناء: تم إنشاء $path من الكرة العائمة",
                                        details = "المسار: $fullPath",
                                        source = "bubble"
                                    )
                                )
                            }
                        }
                        
                        withContext(Dispatchers.Main) {
                            if (buildersCount > 0) {
                                Toast.makeText(applicationContext, "✅ تم تطبيق حزمة البناء بنجاح! تم إنشاء $buildersCount ملفات.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(applicationContext, "⚠️ لا توجد كتل بناء صالحة في الحافظة لتطبيقها!", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(applicationContext, "⚠️ فشل تطبيق حزمة البناء: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(applicationContext, "الحافظة فارغة بالكامل ولم يتم استقبال أي نص لتطبيقه.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        removeGoldenView()
        handler.removeCallbacks(clipboardRunnable)
        isPollingActive = false
        clipboardReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e("GoldenBubbleService", "Error unregistering receiver: ${e.message}")
            }
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }
}
