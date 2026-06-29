package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.db.AppDatabase
import com.example.db.FileEntity
import com.example.db.LogEntity
import com.example.engine.BuilderEngine
import kotlinx.coroutines.*
import java.io.File

/**
 * لوحة مفاتيح الأتمتة الذكية (Builder IME) لـ Android 10+
 *
 * تصميم مبسط برمجياً بالكامل بنسبة 100% تفادياً لأي مشاكل تتعلق بدورات حياة Compose
 * داخل نظام الإدخال بـ Android، ولضمان ثبات تام ونشاط مستمر دون أي خطر للانهيار.
 */
class BuilderIME : InputMethodService() {

    companion object {
        private const val TAG = "BuilderIME"
    }

    private var clipboardManager: ClipboardManager? = null
    private var database: AppDatabase? = null
    private val imeScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastKnownClipText = ""
    private var isPollingActive = false
    private val handler = Handler(Looper.getMainLooper())
    private var statusTextView: TextView? = null
    private var rootLayout: LinearLayout? = null
    private var isExplorerMode = false
    private val collapsedFolders = mutableSetOf<String>()
    private var isManualPathEditActive = false
    private val selectedFiles = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var currentBrowsingFile = File("/storage/emulated/0")
    private val browsingHistoryStack = java.util.Stack<File>()

    private val pathObserver = androidx.lifecycle.Observer<String> { _ ->
        rebuildIMEUI(this)
    }

    fun handleHomePress() {
        isExplorerMode = false
        rootLayout?.let { layout ->
            clearAllEditTextFocus(layout)
            try {
                rebuildIMEUI(this)
            } catch (e: Exception) {}
        }
        requestHideSelf(0)
    }

    private fun clearAllEditTextFocus(view: View) {
        if (view is android.widget.EditText) {
            view.clearFocus()
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                clearAllEditTextFocus(view.getChildAt(i))
            }
        }
    }

    private val isPaused: Boolean
        get() {
            return try {
                getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                    .getBoolean("clipboard_is_paused", false)
            } catch (e: Exception) {
                false
            }
        }

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkClipboardInIME()
            handler.postDelayed(this, 1500L)
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "clipboard_is_paused") {
            updatePollingState()
            updateUIStatus()
        }
    }

    private val refreshReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_REFRESH_IME_TREE") {
                Log.d(TAG, "Received ACTION_REFRESH_IME_TREE, rebuilding UI")
                context?.let { rebuildIMEUI(it) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            database = AppDatabase.getDatabase(this)
            getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener)
            
            val filter = android.content.IntentFilter("ACTION_REFRESH_IME_TREE")
            try {
                registerReceiver(refreshReceiver, filter)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering receiver: ${e.message}")
            }

            com.example.engine.UnifiedPathManager.init(this)
            com.example.engine.UnifiedPathManager.activePath.observeForever(pathObserver)
            
            Log.d(TAG, "BuilderIME onCreate initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }

    private fun updatePollingState() {
        try {
            if (isPaused) {
                if (isPollingActive) {
                    handler.removeCallbacks(checkRunnable)
                    isPollingActive = false
                    Log.d(TAG, "IME Polling paused.")
                }
            } else {
                if (!isPollingActive) {
                    clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboardManager?.let { cm ->
                        if (cm.hasPrimaryClip()) {
                            val clip = cm.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                lastKnownClipText = clip.getItemAt(0).text?.toString() ?: ""
                            }
                        }
                    }
                    handler.post(checkRunnable)
                    isPollingActive = true
                    Log.d(TAG, "IME Polling resumed.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating polling state: ${e.message}")
        }
    }

    private fun getStatusString(): String {
        return if (isPaused) {
            "🔴 المراقبة التلقائية بالخلفية: متوقفة مؤقتاً\n(استخدم زر معالجة الحافظة للمسح اليدوي الآن)"
        } else {
            "🟢 المراقبة التلقائية بالخلفية: تعمل بنشاط\n(ستتم معالجة التوجيهات تلقائياً بمجرد نسخها)"
        }
    }

    private fun updateUIStatus() {
        handler.post {
            try {
                statusTextView?.text = getStatusString()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating UI status: ${e.message}")
            }
        }
    }

    override fun onCreateInputView(): View {
        val context = this
        rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0F19")) // Elegant SuperDark Navy BG
            val pad = dpToPx(12, context)
            setPadding(pad, pad, pad, pad)
            val prefH = getPreferredKeyboardHeight(context)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                prefH
            )
        }

        rebuildIMEUI(context)

        // Make sure clipboard manager is ready
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            updatePollingState()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ClipboardManager in onCreateInputView: ${e.message}")
        }

        return rootLayout!!
    }

    private fun rebuildIMEUI(context: Context) {
        val layout = rootLayout ?: return
        layout.removeAllViews()

        if (isExplorerMode) {
            renderDeviceExplorer(context, layout)
            return
        }

        // 0. Top resize handle (for dynamic height resizing)
        val resizeHandle = View(context).apply {
            setBackgroundColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(6, context)
            ).apply {
                bottomMargin = dpToPx(2, context)
            }
        }
        var initialY = 0f
        var initialHeight = 0
        resizeHandle.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialHeight = rootLayout?.layoutParams?.height ?: getPreferredKeyboardHeight(context)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    var newHeight = (initialHeight - deltaY).toInt()
                    val minHeight = dpToPx(180, context)
                    val maxHeight = dpToPx(550, context)
                    if (newHeight < minHeight) newHeight = minHeight
                    if (newHeight > maxHeight) newHeight = maxHeight
                    rootLayout?.layoutParams?.let { lp ->
                        lp.height = newHeight
                        rootLayout?.layoutParams = lp
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    rootLayout?.layoutParams?.height?.let { finalHeight ->
                        setPreferredKeyboardHeight(context, finalHeight)
                    }
                    true
                }
                else -> false
            }
        }
        layout.addView(resizeHandle)

        // Section 1: Top Bar
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            val padH = dpToPx(12, context)
            val padV = dpToPx(8, context)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        if (isManualPathEditActive) {
            val editText = android.widget.EditText(context).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                hint = "/storage/emulated/0/Folder"
                setHintTextColor(Color.GRAY)
                setText(com.example.engine.ProjectContextManager.getCurrentProjectPath(context))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#111827"))
                    setStroke(dpToPx(1, context), Color.parseColor("#D97706"))
                    cornerRadius = dpToPx(4, context).toFloat()
                }
                setPadding(dpToPx(8, context), dpToPx(6, context), dpToPx(8, context), dpToPx(6, context))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = dpToPx(4, context)
                }
                isSingleLine = true
                isFocusableInTouchMode = true
                isFocusable = true
            }
            topBar.addView(editText)

            val pasteBtn = Button(context).apply {
                text = "📋 لصق"
                setTextColor(Color.WHITE)
                textSize = 9f
                background = createButtonDrawable("#4B5563")
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(28, context)).apply {
                    rightMargin = dpToPx(4, context)
                }
                layoutParams = lp
                setOnClickListener {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        if (clipboard.hasPrimaryClip()) {
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                val textToPaste = clip.getItemAt(0).text?.toString() ?: ""
                                if (textToPaste.isNotEmpty()) {
                                    editText.setText(textToPaste)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "فشل اللصق: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            topBar.addView(pasteBtn)

            val confirmBtn = Button(context).apply {
                text = "✅"
                setTextColor(Color.WHITE)
                textSize = 10f
                background = createButtonDrawable("#10B981")
                val lp = LinearLayout.LayoutParams(dpToPx(28, context), dpToPx(28, context)).apply {
                    rightMargin = dpToPx(4, context)
                }
                layoutParams = lp
                setOnClickListener {
                    val path = editText.text.toString().trim()
                    if (path.isNotEmpty()) {
                        val file = File(path)
                        if (file.exists() && file.isDirectory) {
                            com.example.engine.UnifiedPathManager.setActivePath(context, path)
                            Toast.makeText(context, "تم تعيين المسار النشط: $path", Toast.LENGTH_SHORT).show()
                            isManualPathEditActive = false
                            rebuildIMEUI(context)
                        } else {
                            Toast.makeText(context, "خطأ: المجلد غير موجود!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "الرجاء كتابة مسار صالح", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            topBar.addView(confirmBtn)

            val cancelEditBtn = Button(context).apply {
                text = "❌"
                setTextColor(Color.WHITE)
                textSize = 10f
                background = createButtonDrawable("#EF4444")
                val lp = LinearLayout.LayoutParams(dpToPx(28, context), dpToPx(28, context))
                layoutParams = lp
                setOnClickListener {
                    isManualPathEditActive = false
                    rebuildIMEUI(context)
                }
            }
            topBar.addView(cancelEditBtn)

            handler.postDelayed({
                editText.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }, 100L)
        } else {
            val activeProjTxt = TextView(context).apply {
                val currentPath = com.example.engine.ProjectContextManager.getCurrentProjectPath(context)
                text = "📁 $currentPath"
                setTextColor(Color.parseColor("#FFD700"))
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                var isExpanded = false
                setOnClickListener {
                    if (isExpanded) {
                        setSingleLine(true)
                        ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                        isExpanded = false
                    } else {
                        setSingleLine(false)
                        ellipsize = null
                        isExpanded = true
                    }
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Project Path", currentPath))
                        Toast.makeText(context, "تم نسخ مسار المشروع وتعديل العرض", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "تم تعديل عرض المسار", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            topBar.addView(activeProjTxt)

            val expandAllBtn = Button(context).apply {
                text = "[+]"
                setTextColor(Color.WHITE)
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                background = createButtonDrawable("#3B82F6")
                setPadding(0, 0, 0, 0)
                val lp = LinearLayout.LayoutParams(dpToPx(24, context), dpToPx(24, context)).apply {
                    rightMargin = dpToPx(2, context)
                }
                layoutParams = lp
                setOnClickListener {
                    collapsedFolders.clear()
                    rebuildIMEUI(context)
                }
            }
            topBar.addView(expandAllBtn)

            val collapseAllBtn = Button(context).apply {
                text = "[-]"
                setTextColor(Color.WHITE)
                textSize = 8f
                typeface = Typeface.DEFAULT_BOLD
                background = createButtonDrawable("#4B5563")
                setPadding(0, 0, 0, 0)
                val lp = LinearLayout.LayoutParams(dpToPx(24, context), dpToPx(24, context)).apply {
                    rightMargin = dpToPx(4, context)
                }
                layoutParams = lp
                setOnClickListener {
                    collapsedFolders.clear()
                    val activeDir = com.example.engine.ProjectContextManager.getCurrentProjectDir(context)
                    if (activeDir.exists() && activeDir.isDirectory) {
                        activeDir.walkTopDown().forEach { f ->
                            if (f.isDirectory && f != activeDir) {
                                val rel = f.relativeTo(activeDir).path
                                if (rel.split(File.separator).none { it in com.example.engine.BuilderEngine.IGNORE_DIRS }) {
                                    collapsedFolders.add(rel)
                                }
                            }
                        }
                    }
                    rebuildIMEUI(context)
                }
            }
            topBar.addView(collapseAllBtn)

            val exploreBtn = Button(context).apply {
                text = "📂 استعراض"
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                background = createButtonDrawable("#10B981")
                setPadding(dpToPx(4, context), 0, dpToPx(4, context), 0)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(24, context)).apply {
                    rightMargin = dpToPx(2, context)
                }
                layoutParams = lp
                setOnClickListener {
                    initCurrentBrowsingFile(context)
                    browsingHistoryStack.clear()
                    isExplorerMode = true
                    rebuildIMEUI(context)
                }
            }
            topBar.addView(exploreBtn)

            val manualBtn = Button(context).apply {
                text = "✏️ يدوي"
                setTextColor(Color.WHITE)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                background = createButtonDrawable("#4B5563")
                setPadding(dpToPx(4, context), 0, dpToPx(4, context), 0)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(24, context)).apply {
                    rightMargin = dpToPx(2, context)
                }
                layoutParams = lp
                setOnClickListener {
                    isManualPathEditActive = true
                    rebuildIMEUI(context)
                }
            }
            topBar.addView(manualBtn)

            val fileCount = getActiveProjectFilesCount(context)
            val filesCountTxt = TextView(context).apply {
                text = "📄 $fileCount ملفًا"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(4, context), 0, dpToPx(4, context), 0)
            }
            topBar.addView(filesCountTxt)

            val refreshBtn = Button(context).apply {
                text = "🔄"
                setTextColor(Color.WHITE)
                background = null
                textSize = 11f
                setPadding(0, 0, 0, 0)
                val rParams = LinearLayout.LayoutParams(dpToPx(24, context), dpToPx(24, context))
                layoutParams = rParams
                setOnClickListener {
                    Toast.makeText(context, "تم تحديث مستكشف شجرة الملفات بنجاح!", Toast.LENGTH_SHORT).show()
                    rebuildIMEUI(context)
                }
            }
            topBar.addView(refreshBtn)
        }

        layout.addView(topBar)

        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#FFD700"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1, context)
            )
        }
        layout.addView(divider)

        val activeDir = com.example.engine.ProjectContextManager.getCurrentProjectDir(context)
        val treeList = mutableListOf<File>()
        getFilesTree(activeDir, activeDir, treeList)

        // Section 1.5: Contextual Multi-Selection Bar
        val mSelectionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1F2937"))
            val pad = dpToPx(6, context)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val allFilesInTree = treeList.filter { !it.isDirectory }
        val allRelPathsInTree = allFilesInTree.map { it.relativeTo(activeDir).path }
        val isAllSelectedInTree = allRelPathsInTree.isNotEmpty() && allRelPathsInTree.all { selectedFiles.contains(it) }

        val selectAllCheckbox = android.widget.CheckBox(context).apply {
            isChecked = isAllSelectedInTree
            text = "تحديد الكل"
            setTextColor(Color.WHITE)
            textSize = 10f
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedFiles.addAll(allRelPathsInTree)
                } else {
                    selectedFiles.removeAll(allRelPathsInTree)
                }
                rebuildIMEUI(context)
            }
        }
        mSelectionBar.addView(selectAllCheckbox)

        if (selectedFiles.isNotEmpty()) {
            val selectionCountTxt = TextView(context).apply {
                text = "📎 تم تحديد ${selectedFiles.size} ملف"
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dpToPx(8, context)
                }
            }
            mSelectionBar.addView(selectionCountTxt)

            val copySelBtn = Button(context).apply {
                text = "📋 نسخ التوجيهات"
                setTextColor(Color.WHITE)
                textSize = 9f
                background = createButtonDrawable("#10B981")
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(30, context)).apply {
                    rightMargin = dpToPx(4, context)
                }
                layoutParams = lp
                setOnClickListener {
                    val filesToCopy = selectedFiles.map { File(activeDir, it) }.filter { it.exists() && it.isFile }
                    if (filesToCopy.isNotEmpty()) {
                        copyMultipleFilesDirectives(context, filesToCopy)
                        selectedFiles.clear()
                        rebuildIMEUI(context)
                    } else {
                        Toast.makeText(context, "الرجاء تحديد ملفات صالحة", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            mSelectionBar.addView(copySelBtn)

            val clearSelBtn = Button(context).apply {
                text = "إلغاء [x]"
                setTextColor(Color.WHITE)
                textSize = 9f
                background = createButtonDrawable("#EF4444")
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(30, context))
                layoutParams = lp
                setOnClickListener {
                    selectedFiles.clear()
                    rebuildIMEUI(context)
                }
            }
            mSelectionBar.addView(clearSelBtn)
        } else {
            val infoTxt = TextView(context).apply {
                text = "تحديد سريع للملفات"
                setTextColor(Color.GRAY)
                textSize = 10f
                gravity = Gravity.RIGHT
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            mSelectionBar.addView(infoTxt)
        }
        layout.addView(mSelectionBar)

        // Section 2: Tree Explorer in ScrollView
        val scrollView = android.widget.ScrollView(context).apply {
            val sParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dpToPx(4, context)
                bottomMargin = dpToPx(4, context)
            }
            layoutParams = sParams
            isVerticalScrollBarEnabled = true
        }

        val scrollContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val scParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams = scParams
        }

        if (treeList.isEmpty()) {
            val emptyTxt = TextView(context).apply {
                text = "المجلد فارغ تماماً أو غير موجود."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(24, context), 0, dpToPx(24, context))
            }
            scrollContainer.addView(emptyTxt)
        } else {
            for (file in treeList) {
                val relPath = file.relativeTo(activeDir).path
                val isDir = file.isDirectory
                val level = relPath.count { it == File.separatorChar }

                val itemLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val padStart = dpToPx(8, context) + (level * dpToPx(12, context))
                    setPadding(padStart, dpToPx(5, context), dpToPx(8, context), dpToPx(5, context))
                    
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#111827"))
                        cornerRadius = dpToPx(4, context).toFloat()
                    }
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(3, context)
                    }
                    layoutParams = lp
                }

                if (!isDir) {
                    val checkbox = android.widget.CheckBox(context).apply {
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            rightMargin = dpToPx(4, context)
                        }
                        layoutParams = lp
                        isChecked = selectedFiles.contains(relPath)
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                selectedFiles.add(relPath)
                            } else {
                                selectedFiles.remove(relPath)
                            }
                            handler.post { rebuildIMEUI(context) }
                        }
                    }
                    itemLayout.addView(checkbox)
                }

                val nameTxt = TextView(context).apply {
                    val icon = if (isDir) {
                        val isCollapsed = collapsedFolders.contains(relPath)
                        if (isCollapsed) "📁 ➕ " else "📁 ➖ "
                    } else "📄 "
                    text = "$icon${file.name}"
                    setTextColor(if (isDir) Color.parseColor("#FFD700") else Color.parseColor("#F1F5F9"))
                    textSize = 11f
                    typeface = if (isDir) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                itemLayout.addView(nameTxt)

                if (!isDir) {
                    val sizeKb = file.length() / 1024.0
                    val sizeStr = String.format(java.util.Locale.US, "%.1f KB", sizeKb)
                    val sizeTxt = TextView(context).apply {
                        text = sizeStr
                        setTextColor(Color.parseColor("#94A3B8"))
                        textSize = 9f
                        setPadding(0, 0, dpToPx(6, context), 0)
                    }
                    itemLayout.addView(sizeTxt)

                    val openBtn = TextView(context).apply {
                        text = "👁️ فتح"
                        setTextColor(Color.parseColor("#10B981"))
                        textSize = 10f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dpToPx(6, context), dpToPx(4, context), dpToPx(6, context), dpToPx(4, context))
                        setOnClickListener {
                            com.example.engine.FileUtils.openFile(context, file.absolutePath)
                        }
                    }
                    itemLayout.addView(openBtn)

                    itemLayout.setOnClickListener {
                        com.example.engine.FileUtils.openFile(context, file.absolutePath)
                    }

                    itemLayout.setOnLongClickListener {
                        copySingleFileDirective(context, file)
                        true
                    }
                } else {
                    itemLayout.setOnClickListener {
                        if (collapsedFolders.contains(relPath)) {
                            collapsedFolders.remove(relPath)
                        } else {
                            collapsedFolders.add(relPath)
                        }
                        rebuildIMEUI(context)
                    }
                }
                scrollContainer.addView(itemLayout)
            }
        }

        scrollView.addView(scrollContainer)
        layout.addView(scrollView)

        // Section 3: Bottom Bar
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            val pad = dpToPx(6, context)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val btnParams = LinearLayout.LayoutParams(0, dpToPx(38, context), 1f).apply {
            leftMargin = dpToPx(3, context)
            rightMargin = dpToPx(3, context)
        }

        val processBtn = Button(context).apply {
            text = "⚡ معالجة الآن"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable("#3B82F6")
            layoutParams = btnParams
            setOnClickListener {
                forceScanClipboardManual()
            }
        }
        bottomBar.addView(processBtn)

        val copyBtnParams = LinearLayout.LayoutParams(dpToPx(38, context), dpToPx(38, context)).apply {
            leftMargin = dpToPx(3, context)
            rightMargin = dpToPx(3, context)
        }
        val copyFileDirectiveBtn = Button(context).apply {
            text = "📋"
            setTextColor(Color.WHITE)
            textSize = 12f
            background = createButtonDrawable("#4B5563")
            layoutParams = copyBtnParams
            setOnClickListener {
                try {
                    val popup = android.widget.PopupMenu(context, this)
                    val activeDir = com.example.engine.ProjectContextManager.getCurrentProjectDir(context)
                    if (activeDir.exists() && activeDir.isDirectory) {
                        val fileList = mutableListOf<File>()
                        activeDir.walkTopDown().forEach { f ->
                            if (f.isFile) {
                                val rel = f.relativeTo(activeDir).path
                                val parts = rel.split(File.separator)
                                if (parts.none { it in com.example.engine.BuilderEngine.IGNORE_DIRS }) {
                                    fileList.add(f)
                                }
                            }
                        }
                        
                        val displayFiles = fileList.take(20)
                        if (displayFiles.isEmpty()) {
                            popup.menu.add("لا توجد ملفات بالمشروع")
                        } else {
                            displayFiles.forEachIndexed { idx, f ->
                                val relPath = f.relativeTo(activeDir).path
                                popup.menu.add(0, idx, idx, relPath)
                            }
                        }
                        popup.setOnMenuItemClickListener { menuItem ->
                            if (displayFiles.isNotEmpty()) {
                                val selectedFile = displayFiles[menuItem.itemId]
                                copySingleFileDirective(context, selectedFile)
                            }
                            true
                        }
                    } else {
                        popup.menu.add("المشروع غير موجود")
                    }
                    popup.show()
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bottomBar.addView(copyFileDirectiveBtn)

        val buildPackBtn = Button(context).apply {
            text = "📦 حزمة بناء"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable("#10B981")
            layoutParams = btnParams
            setOnClickListener {
                com.example.engine.BuildPackExporter.generateAndCopy(context)
            }
        }
        bottomBar.addView(buildPackBtn)

        val prevImeBtn = Button(context).apply {
            text = "⌨️ الكيبورد السابق"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable("#1E293B")
            layoutParams = btnParams
            setOnClickListener {
                switchBackToPreviousIME()
            }
        }
        bottomBar.addView(prevImeBtn)

        layout.addView(bottomBar)
    }

    private fun getActiveProjectFilesCount(context: Context): Int {
        val dir = com.example.engine.ProjectContextManager.getCurrentProjectDir(context)
        if (!dir.exists() || !dir.isDirectory) return 0
        var count = 0
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(dir).path
                    val parts = relativePath.split(java.io.File.separator)
                    if (parts.none { it in com.example.engine.BuilderEngine.IGNORE_DIRS }) {
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error counting project files: ${e.message}")
        }
        return count
    }

    private fun getFilesTree(dir: File, baseDir: File, result: MutableList<File>) {
        if (!dir.exists() || !dir.isDirectory) return
        val children = dir.listFiles() ?: return
        
        val filteredChildren = children.filter { child ->
            child.name !in com.example.engine.BuilderEngine.IGNORE_DIRS
        }
        
        val sortedChildren = filteredChildren.sortedWith(compareBy(
            { !it.isDirectory },
            { it.name.lowercase(java.util.Locale.ROOT) }
        ))

        for (child in sortedChildren) {
            result.add(child)
            if (child.isDirectory) {
                val relativePath = child.relativeTo(baseDir).path
                if (!collapsedFolders.contains(relativePath)) {
                    getFilesTree(child, baseDir, result)
                }
            }
        }
    }

    private fun openFileWithProvider(file: File) {
        if (!file.exists()) {
            Toast.makeText(applicationContext, "الملف غير موجود.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val authority = "$packageName.fileprovider"
            val uri = FileProvider.getUriForFile(this, authority, file)
            val extension = file.extension.lowercase(java.util.Locale.ROOT)
            val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(applicationContext, "جاري فتح الملف: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "فشل تشغيل المعالج الافتراضي: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getPhysicalFilesCount(): Int {
        val base = getBaseDir()
        if (!base.exists()) return 0
        var count = 0
        try {
            base.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(base).path
                    val parts = relativePath.split(File.separator)
                    if (parts.none { it in BuilderEngine.IGNORE_DIRS }) {
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error counting physical files: ${e.message}")
        }
        return count
    }

    private fun dpToPx(dp: Int, context: Context): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun createButtonDrawable(colorHex: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = dpToPx(8, this@BuilderIME).toFloat()
        }
    }

    private fun switchBackToPreviousIME() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        try {
            val token = window?.window?.attributes?.token ?: window?.window?.decorView?.windowToken
            if (token != null) {
                imm.switchToLastInputMethod(token)
            } else {
                showIMESelectionMenu(imm)
            }
        } catch (e: Exception) {
            showIMESelectionMenu(imm)
        }
    }

    private fun showIMESelectionMenu(imm: InputMethodManager) {
        try {
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "يرجى اختيار التبديل يدوياً من شريط تنقل الهاتف.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkClipboardInIME() {
        try {
            val manager = clipboardManager ?: (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            if (manager == null || !manager.hasPrimaryClip()) return
            val clipData = manager.primaryClip ?: return
            if (clipData.itemCount == 0) return
            val text = clipData.getItemAt(0).text?.toString() ?: ""

            if (text.isNotBlank() && text != lastKnownClipText) {
                lastKnownClipText = text
                Log.d(TAG, "IME background check detected clipboard change.")
                onNewClipboardTextDetected(text)
            }
        } catch (e: Exception) {
            // Safe ignore
        }
    }

    private fun forceScanClipboardManual() {
        try {
            val manager = clipboardManager ?: (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            if (manager != null && manager.hasPrimaryClip()) {
                val clipData = manager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                    if (text.isNotBlank()) {
                        Toast.makeText(applicationContext, "🔄 جاري المعالجة الفورية المباشرة...", Toast.LENGTH_SHORT).show()
                        processCopiedText(text)
                    } else {
                        Toast.makeText(applicationContext, "الحافظة فارغة حالياً.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, "الحافظة فارغة حالياً.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(applicationContext, "لم نتمكن من الوصول للحافظة حالياً.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(applicationContext, "فشل فحص الحافظة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onNewClipboardTextDetected(text: String) {
        try {
            val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
            if (isPaused) return
            val isAutoProcess = sharedPrefs.getBoolean("auto_process_clipboard", true)
            if (!isAutoProcess) return

            if (text.isBlank()) return

            val textHash = text.trim().hashCode().toString()
            val lastProcessedHash = sharedPrefs.getString("last_processed_text_hash", "")
            if (textHash == lastProcessedHash) return

            val pBuilder = sharedPrefs.getString("prefix_builder", "@builder") ?: "@builder"
            val pExecutor = sharedPrefs.getString("prefix_executor", "@executor") ?: "@executor"
            val pTreedoc = sharedPrefs.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"

            val lastProcessed = sharedPrefs.getString("last_auto_processed_text", "") ?: ""
            if (text == lastProcessed) return

            if (text.contains("$pBuilder:") || text.contains("$pExecutor:") || text.contains("$pTreedoc:")) {
                processCopiedText(text)
            } else {
                if (text.trim().length >= 8) {
                    sharedPrefs.edit().putString("last_processed_text_hash", textHash).apply()
                    sharedPrefs.edit().putString("last_auto_processed_text", text).apply()
                    imeScope.launch(Dispatchers.Main) {
                        try {
                            Toast.makeText(applicationContext, "⚠️ نص منسوخ لا يحتوي على توجيهات حفظ؛ لم يتم الحفظ تلقائياً.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {}
                    }
                    logSystemEvent("تجاوز النسخ (لا توجد توجيهات)", "تم تجاوز الحفظ لعدم العثور على بادئات نشطة في النص المنسوخ.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNewClipboardTextDetected: ${e.message}")
        }
    }

    private fun processCopiedText(text: String) {
        val sharedPrefs = getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        val textHash = text.trim().hashCode().toString()
        val lastProcessedHash = sharedPrefs.getString("last_processed_text_hash", "")
        if (textHash == lastProcessedHash && text.isNotBlank()) {
            return
        }

        val lastProcessed = sharedPrefs.getString("last_auto_processed_text", "") ?: ""
        if (text == lastProcessed && text.isNotBlank()) {
            return
        }
        sharedPrefs.edit().putString("last_processed_text_hash", textHash).apply()
        sharedPrefs.edit().putString("last_auto_processed_text", text).apply()

        imeScope.launch {
            logSystemEvent("توجيه مكتشف (IME)", "تم التقاط محتويات الحافظة عبر الكيبورد وتمريرها للمحرك...")

            try {
                val pBuilder = sharedPrefs.getString("prefix_builder", "@builder") ?: "@builder"
                val pExecutor = sharedPrefs.getString("prefix_executor", "@executor") ?: "@executor"
                val pTreedoc = sharedPrefs.getString("prefix_treedoc", "@treedoc") ?: "@treedoc"

                val settings = mapOf<String, Any>(
                    "absolute_path_handling" to "relative",
                    "base_dir" to getBaseDir().absolutePath,
                    "directive_prefixes" to listOf(pBuilder),
                    "executor_prefixes" to listOf(pExecutor),
                    "treedoc_prefixes" to listOf(pTreedoc)
                )
                val engine = BuilderEngine(this@BuilderIME, settings)

                val results = engine.processText(text)
                if (results.isEmpty()) {
                    logSystemEvent("معالجة IME فارغة", "لم يعثر المحرك على توجيهات صالحة.")
                    return@launch
                }

                var buildersCount = 0
                var executorsCount = 0
                var treedocCount = 0

                val db = database ?: AppDatabase.getDatabase(this@BuilderIME)
                for (res in results) {
                    when (res.type) {
                        "builder" -> {
                            buildersCount++
                            val path = res.data?.get("path") ?: "unknown"
                            val size = res.data?.get("size")?.toLongOrNull() ?: 0L
                            val mode = res.data?.get("mode") ?: "w"
                            val fullPath = res.data?.get("full_path") ?: ""
                            
                            db.dao().insertFile(
                                FileEntity(
                                    path = path,
                                    fullPath = fullPath,
                                    size = size,
                                    mode = mode
                                )
                            )
                            db.dao().insertLog(
                                LogEntity(
                                    type = "builder",
                                    message = "IME: تم إنشاء الملف: $path",
                                    details = res.message,
                                    source = "ime"
                                )
                            )
                        }
                        "executor" -> {
                            executorsCount++
                            db.dao().insertLog(
                                LogEntity(
                                    type = "executor",
                                    message = "IME: تنفيذ أمر المنفذ",
                                    details = res.message,
                                    source = "ime"
                                )
                            )
                        }
                        "treedoc" -> {
                            treedocCount++
                            db.dao().insertLog(
                                LogEntity(
                                    type = "treedoc",
                                    message = "IME: توليد تقرير TreeDoc الشجري",
                                    details = res.message,
                                    source = "ime"
                                )
                            )
                        }
                    }
                }

                val summary = "IME: تم معالجة $buildersCount ملفات، $executorsCount أوامر، $treedocCount تقارير شجرية."
                logSystemEvent("نجاح معالجة IME", summary)

                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "✅ تم الحفظ والمعالجة التلقائية بنجاح من لوحة المفاتيح!", Toast.LENGTH_SHORT).show()
                }

                val clearClip = sharedPrefs.getBoolean("clear_clip_after_save", false)
                if (clearClip) {
                    withContext(Dispatchers.Main) {
                        try {
                            val manager = clipboardManager ?: (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            if (manager != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    manager.clearPrimaryClip()
                                } else {
                                    manager.setPrimaryClip(ClipData.newPlainText("", ""))
                                }
                                Toast.makeText(applicationContext, "🧹 تم مسح وتفريغ الحافظة تلقائياً بنجاح.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error clearing clipboard: ${e.message}")
                        }
                    }
                }

            } catch (e: Exception) {
                logSystemEvent("خطأ في معالجة IME", "فشل معالجة النص: ${e.message}")
            }
        }
    }

    private fun logSystemEvent(title: String, message: String) {
        try {
            imeScope.launch(Dispatchers.IO) {
                val db = database ?: AppDatabase.getDatabase(this@BuilderIME)
                db.dao().insertLog(
                    LogEntity(
                        type = "clipboard_ime",
                        message = title,
                        details = message,
                        source = "ime"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging system event: ${e.message}")
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

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updatePollingState()
        updateUIStatus()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (isExplorerMode) {
            isExplorerMode = false
            rootLayout?.let { clearAllEditTextFocus(it) }
            rebuildIMEUI(this)
        }
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            com.example.engine.UnifiedPathManager.activePath.removeObserver(pathObserver)
        } catch (e: Exception) {}
        try {
            getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (e: Exception) {}
        try {
            unregisterReceiver(refreshReceiver)
        } catch (ex: Exception) {}
        handler.removeCallbacks(checkRunnable)
        isPollingActive = false
        imeScope.cancel()
        Log.d(TAG, "BuilderIME destroyed.")
    }

    private fun copySingleFileDirective(context: Context, file: File) {
        val activeDir = com.example.engine.ProjectContextManager.getCurrentProjectDir(context)
        val relativePath = file.relativeTo(activeDir).path
        val ext = file.extension.lowercase(java.util.Locale.ROOT)
        
        val comment = when (ext) {
            "kt", "java" -> "// ملف: $relativePath\n// اللغة: Kotlin/Java"
            "py" -> "# ملف: $relativePath\n# اللغة: Python"
            "html" -> "<!-- ملف: $relativePath -->\n<!-- اللغة: HTML -->"
            else -> ""
        }
        
        val fileContent = try { file.readText() } catch (e: Exception) { "// فشل قراءة الملف" }
        
        val sb = StringBuilder()
        sb.append("@builder:file ").append(relativePath).append("\n")
        if (comment.isNotEmpty()) {
            sb.append(comment).append("\n")
        }
        sb.append(fileContent).append("\n")
        sb.append("@builder:end")
        
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("File Directive", sb.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "📋 تم نسخ توجيه الملف ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "فشل نسخ توجيه الملف: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyMultipleFilesDirectives(context: Context, files: List<File>) {
        val activeDir = com.example.engine.ProjectContextManager.getCurrentProjectDir(context)
        val sb = java.lang.StringBuilder()
        
        for (file in files) {
            val relativePath = file.relativeTo(activeDir).path
            val ext = file.extension.lowercase(java.util.Locale.ROOT)
            val comment = when (ext) {
                "kt", "java" -> "// ملف: $relativePath\n// اللغة: Kotlin/Java"
                "py" -> "# ملف: $relativePath\n# اللغة: Python"
                "html" -> "<!-- ملف: $relativePath -->\n<!-- اللغة: HTML -->"
                else -> ""
            }
            val fileContent = try { file.readText() } catch (e: Exception) { "// فشل قراءة الملف" }
            
            sb.append("@builder:file ").append(relativePath).append("\n")
            if (comment.isNotEmpty()) {
                sb.append(comment).append("\n")
            }
            sb.append(fileContent).append("\n")
            sb.append("@builder:end\n\n")
        }
        
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Multiple File Directives", sb.toString().trim())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "📋 تم نسخ توجيهات لـ ${files.size} ملفات بنجاح!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "فشل نسخ التوجيهات: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPreferredKeyboardHeight(context: Context): Int {
        val prefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        return prefs.getInt("keyboard_height_px", dpToPx(280, context))
    }

    private fun setPreferredKeyboardHeight(context: Context, heightPx: Int) {
        val prefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("keyboard_height_px", heightPx).apply()
    }

    private fun initCurrentBrowsingFile(context: Context) {
        val currentPath = com.example.engine.ProjectContextManager.getCurrentProjectPath(context)
        var f = File(currentPath)
        if (!f.exists() || !f.isDirectory) {
            f = File("/storage/emulated/0")
            if (!f.exists() || !f.isDirectory) {
                f = File("/")
            }
        }
        currentBrowsingFile = f
    }

    private fun renderDeviceExplorer(context: Context, layout: LinearLayout) {
        layout.removeAllViews()

        // 1. Top resize handle (for dynamic height resizing)
        val resizeHandle = View(context).apply {
            setBackgroundColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(6, context)
            ).apply {
                bottomMargin = dpToPx(2, context)
            }
        }
        // Touch listener for resize handle
        var initialY = 0f
        var initialHeight = 0
        resizeHandle.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialHeight = rootLayout?.layoutParams?.height ?: getPreferredKeyboardHeight(context)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    var newHeight = (initialHeight - deltaY).toInt()
                    val minHeight = dpToPx(180, context)
                    val maxHeight = dpToPx(550, context)
                    if (newHeight < minHeight) newHeight = minHeight
                    if (newHeight > maxHeight) newHeight = maxHeight
                    rootLayout?.layoutParams?.let { lp ->
                        lp.height = newHeight
                        rootLayout?.layoutParams = lp
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    rootLayout?.layoutParams?.height?.let { finalHeight ->
                        setPreferredKeyboardHeight(context, finalHeight)
                    }
                    true
                }
                else -> false
            }
        }
        layout.addView(resizeHandle)

        // 2. Explorer Top Title bar
        val explorerTopBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            val padH = dpToPx(12, context)
            val padV = dpToPx(8, context)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleTxt = TextView(context).apply {
            text = "📂 تصفح ملفات الجهاز"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        explorerTopBar.addView(titleTxt)

        // Back button to return to project tree mode
        val closeBtn = Button(context).apply {
            text = "↩️ رجوع"
            setTextColor(Color.parseColor("#FF5555"))
            background = createButtonDrawable("#2A1B1B")
            textSize = 10f
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(28, context))
            layoutParams = lp
            setOnClickListener {
                isExplorerMode = false
                rebuildIMEUI(context)
            }
        }
        explorerTopBar.addView(closeBtn)
        layout.addView(explorerTopBar)

        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#D97706"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(1, context)
            )
        }
        layout.addView(divider)

        // Breadcrumb/Path View
        val pathTextView = TextView(context).apply {
            text = currentBrowsingFile.absolutePath
            setTextColor(Color.parseColor("#D97706"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.START
            val pad = dpToPx(8, context)
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#11131E"))
        }
        layout.addView(pathTextView)

        // History Back & Up buttons row
        val navButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#141824"))
            val paddingV = dpToPx(4, context)
            val paddingH = dpToPx(8, context)
            setPadding(paddingH, paddingV, paddingH, paddingV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // History back button
        val histBackBtn = Button(context).apply {
            text = "⬅️ للقديم (${browsingHistoryStack.size})"
            isEnabled = browsingHistoryStack.isNotEmpty()
            setTextColor(if (isEnabled) Color.WHITE else Color.GRAY)
            background = createButtonDrawable(if (isEnabled) "#374151" else "#1F2937")
            textSize = 9f
            val lp = LinearLayout.LayoutParams(0, dpToPx(28, context), 1f).apply {
                rightMargin = dpToPx(4, context)
            }
            layoutParams = lp
            setOnClickListener {
                if (browsingHistoryStack.isNotEmpty()) {
                    currentBrowsingFile = browsingHistoryStack.pop()
                    rebuildIMEUI(context)
                }
            }
        }
        navButtonsRow.addView(histBackBtn)

        // Up to parent directory button
        val upParentBtn = Button(context).apply {
            val parent = currentBrowsingFile.parentFile
            isEnabled = parent != null && parent.exists()
            text = "⬆️ للأعلى"
            setTextColor(if (isEnabled) Color.WHITE else Color.GRAY)
            background = createButtonDrawable(if (isEnabled) "#374151" else "#1F2937")
            textSize = 9f
            val lp = LinearLayout.LayoutParams(0, dpToPx(28, context), 1f)
            layoutParams = lp
            setOnClickListener {
                val p = currentBrowsingFile.parentFile
                if (p != null && p.exists()) {
                    browsingHistoryStack.push(currentBrowsingFile)
                    currentBrowsingFile = p
                    rebuildIMEUI(context)
                }
            }
        }
        navButtonsRow.addView(upParentBtn)
        layout.addView(navButtonsRow)

        // 3. ScrollView showing directories & files list
        val scrollView = android.widget.ScrollView(context).apply {
            val sParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dpToPx(2, context)
                bottomMargin = dpToPx(2, context)
            }
            layoutParams = sParams
        }

        val scrollContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val scParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams = scParams
        }

        // Get children files & folders inside currentBrowsingFile
        val filesList = try {
            val list = currentBrowsingFile.listFiles() ?: emptyArray()
            list.sortedWith(compareBy(
                { !it.isDirectory },
                { fileItem -> fileItem.name.lowercase(java.util.Locale.ROOT) }
            ))
        } catch (e: Exception) {
            emptyList<File>()
        }

        if (filesList.isEmpty()) {
            val emptyTxt = TextView(context).apply {
                text = "المجلد فارغ تماماً أو غير مسموح بالدخول إليه."
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(0, dpToPx(24, context), 0, dpToPx(24, context))
            }
            scrollContainer.addView(emptyTxt)
        } else {
            for (file in filesList) {
                val isDir = file.isDirectory
                val itemLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val pad = dpToPx(8, context)
                    setPadding(pad, pad, pad, pad)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#111827"))
                        cornerRadius = dpToPx(4, context).toFloat()
                    }
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(3, context)
                    }
                    layoutParams = lp
                }

                val nameTxt = TextView(context).apply {
                    text = if (isDir) "📁 ${file.name}" else "📄 ${file.name}"
                    setTextColor(if (isDir) Color.parseColor("#FFD700") else Color.parseColor("#F1F5F9"))
                    textSize = 10f
                    typeface = if (isDir) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                itemLayout.addView(nameTxt)

                if (isDir) {
                    itemLayout.setOnClickListener {
                        browsingHistoryStack.push(currentBrowsingFile)
                        currentBrowsingFile = file
                        rebuildIMEUI(context)
                    }
                } else {
                    val sizeKb = file.length() / 1024.0
                    val sizeStr = String.format(java.util.Locale.US, "%.1f KB", sizeKb)
                    val sizeTxt = TextView(context).apply {
                        text = sizeStr
                        setTextColor(Color.parseColor("#94A3B8"))
                        textSize = 8f
                        setPadding(0, 0, dpToPx(6, context), 0)
                    }
                    itemLayout.addView(sizeTxt)

                    val openBtn = TextView(context).apply {
                        text = "👁️ فتح"
                        setTextColor(Color.parseColor("#10B981"))
                        textSize = 9f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dpToPx(4, context), dpToPx(2, context), dpToPx(4, context), dpToPx(2, context))
                        setOnClickListener {
                            com.example.engine.FileUtils.openFile(context, file.absolutePath)
                        }
                    }
                    itemLayout.addView(openBtn)

                    itemLayout.setOnClickListener {
                        com.example.engine.FileUtils.openFile(context, file.absolutePath)
                    }
                }
                scrollContainer.addView(itemLayout)
            }
        }

        scrollView.addView(scrollContainer)
        layout.addView(scrollView)

        // 4. Explorer Bottom Actions Bar
        val explorerBottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            val pad = dpToPx(4, context)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val selectFolderBtn = Button(context).apply {
            text = "🎯 تعيين هذا المجلد كمجلد نشط"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = createButtonDrawable("#D97706")
            val lp = LinearLayout.LayoutParams(0, dpToPx(34, context), 1f).apply {
                rightMargin = dpToPx(4, context)
            }
            layoutParams = lp
            setOnClickListener {
                com.example.engine.UnifiedPathManager.setActivePath(context, currentBrowsingFile.absolutePath)
                Toast.makeText(context, "تم تعيين المجلد كمجلد نشط: ${currentBrowsingFile.absolutePath}", Toast.LENGTH_SHORT).show()
                isExplorerMode = false
                rebuildIMEUI(context)
            }
        }
        explorerBottomBar.addView(selectFolderBtn)

        val cancelBtn = Button(context).apply {
            text = "إلغاء [x]"
            setTextColor(Color.WHITE)
            textSize = 9f
            background = createButtonDrawable("#4B5563")
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dpToPx(34, context))
            layoutParams = lp
            setOnClickListener {
                isExplorerMode = false
                rebuildIMEUI(context)
            }
        }
        explorerBottomBar.addView(cancelBtn)

        layout.addView(explorerBottomBar)
    }
}
