package com.example.service

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.example.engine.FileUtils
import java.io.File
import java.util.Locale

object CustomFileExplorerDialog {

    fun show(context: Context, initialPath: String = "/storage/emulated/0", onFolderSelected: (String) -> Unit) {
        try {
            var currentDir = File(initialPath)
            if (!currentDir.exists() || !currentDir.isDirectory) {
                currentDir = File("/storage/emulated/0")
                if (!currentDir.exists() || !currentDir.isDirectory) {
                    currentDir = File("/")
                }
            }

            val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            builder.setTitle("📂 مستكشف الملفات والمجلدات")

            val dpToPx = { dp: Int ->
                (dp * context.resources.displayMetrics.density).toInt()
            }

            val mainLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundColor(Color.parseColor("#1F2937"))
            }

            val pathTxt = TextView(context).apply {
                text = currentDir.absolutePath
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 0, dpToPx(12))
            }
            mainLayout.addView(pathTxt)

            val listView = ListView(context).apply {
                divider = GradientDrawable().apply {
                    setColor(Color.parseColor("#374151"))
                    setSize(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1))
                }
                dividerHeight = dpToPx(1)
                cacheColorHint = 0
            }
            
            val filesList = mutableListOf<File>()
            
            class FileAdapter : BaseAdapter() {
                override fun getCount(): Int {
                    return if (currentDir.parentFile != null) filesList.size + 1 else filesList.size
                }

                override fun getItem(position: Int): Any? {
                    if (currentDir.parentFile != null) {
                        if (position == 0) return null
                        return filesList[position - 1]
                    }
                    return filesList[position]
                }

                override fun getItemId(position: Int): Long {
                    return position.toLong()
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val rowLayout = convertView as? LinearLayout ?: LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#111827"))
                            cornerRadius = dpToPx(6).toFloat()
                        }
                        val lp = AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        layoutParams = lp
                    }

                    rowLayout.removeAllViews()

                    val nameTxt = TextView(context).apply {
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val isParent = currentDir.parentFile != null && position == 0
                    if (isParent) {
                        nameTxt.apply {
                            text = "↩️ .."
                            setTextColor(Color.parseColor("#9CA3AF"))
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                    } else {
                        val file = getItem(position) as File
                        val isDir = file.isDirectory
                        nameTxt.apply {
                            text = if (isDir) "📁 ${file.name}" else "📄 ${file.name}"
                            setTextColor(if (isDir) Color.parseColor("#FBBF24") else Color.parseColor("#F3F4F6"))
                            typeface = if (isDir) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        }
                    }

                    rowLayout.addView(nameTxt)
                    return rowLayout
                }
            }

            val adapter = FileAdapter()
            listView.adapter = adapter

            fun refreshFiles() {
                try {
                    pathTxt.text = currentDir.absolutePath
                    val rawFiles = currentDir.listFiles() ?: emptyArray()
                    val sorted = rawFiles.sortedWith(compareBy(
                        { !it.isDirectory },
                        { it.name.lowercase(Locale.ROOT) }
                    ))
                    filesList.clear()
                    filesList.addAll(sorted)
                    adapter.notifyDataSetChanged()
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ في قراءة المجلد: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            refreshFiles()

            val listParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(280)
            ).apply {
                weight = 1f
                bottomMargin = dpToPx(12)
            }
            mainLayout.addView(listView, listParams)

            val confirmBtn = Button(context).apply {
                text = "🎯 اختيار هذا المجلد كمجلد نشط"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2563EB"))
                    cornerRadius = dpToPx(8).toFloat()
                }
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                layoutParams = lp
            }
            mainLayout.addView(confirmBtn)

            builder.setView(mainLayout)
            val dialog = builder.create()

            listView.setOnItemClickListener { _, _, position, _ ->
                val isParent = currentDir.parentFile != null && position == 0
                if (isParent) {
                    val parent = currentDir.parentFile
                    if (parent != null) {
                        currentDir = parent
                        refreshFiles()
                    }
                } else {
                    val file = adapter.getItem(position) as File
                    if (file.isDirectory) {
                        currentDir = file
                        refreshFiles()
                    } else {
                        FileUtils.openFile(context, file.absolutePath)
                    }
                }
            }

            confirmBtn.setOnClickListener {
                onFolderSelected(currentDir.absolutePath)
                dialog.dismiss()
            }

            dialog.window?.let { window ->
                window.setType(android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD)
            }
            dialog.show()

        } catch (e: Exception) {
            Toast.makeText(context, "فشل فوري لعرض المستكشف الداخلي: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showForProcessing(context: Context, initialPath: String = "/storage/emulated/0", onSelected: (File) -> Unit) {
        try {
            var currentDir = File(initialPath)
            if (!currentDir.exists() || !currentDir.isDirectory) {
                currentDir = File("/storage/emulated/0")
                if (!currentDir.exists() || !currentDir.isDirectory) {
                    currentDir = File("/")
                }
            }

            val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            builder.setTitle("📂 معالجة ملف أو مجلد")

            val dpToPx = { dp: Int ->
                (dp * context.resources.displayMetrics.density).toInt()
            }

            val mainLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
                setBackgroundColor(Color.parseColor("#1F2937"))
            }

            val pathTxt = TextView(context).apply {
                text = currentDir.absolutePath
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, 0, dpToPx(12))
            }
            mainLayout.addView(pathTxt)

            val listView = ListView(context).apply {
                divider = GradientDrawable().apply {
                    setColor(Color.parseColor("#374151"))
                    setSize(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1))
                }
                dividerHeight = dpToPx(1)
                cacheColorHint = 0
            }
            
            val filesList = mutableListOf<File>()
            
            val adapter = object : BaseAdapter() {
                override fun getCount(): Int {
                    return if (currentDir.parentFile != null) filesList.size + 1 else filesList.size
                }

                override fun getItem(position: Int): Any? {
                    if (currentDir.parentFile != null) {
                        if (position == 0) return null
                        return filesList[position - 1]
                    }
                    return filesList[position]
                }

                override fun getItemId(position: Int): Long {
                    return position.toLong()
                }

                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val rowLayout = convertView as? LinearLayout ?: LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#111827"))
                            cornerRadius = dpToPx(6).toFloat()
                        }
                        val lp = AbsListView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        layoutParams = lp
                    }

                    rowLayout.removeAllViews()

                    val nameTxt = TextView(context).apply {
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val isParent = currentDir.parentFile != null && position == 0
                    if (isParent) {
                        nameTxt.apply {
                            text = "↩️ .."
                            setTextColor(Color.parseColor("#9CA3AF"))
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        }
                    } else {
                        val file = getItem(position) as File
                        val isDir = file.isDirectory
                        nameTxt.apply {
                            text = if (isDir) "📁 ${file.name}" else "📄 ${file.name}"
                            setTextColor(if (isDir) Color.parseColor("#FBBF24") else Color.parseColor("#F3F4F6"))
                            typeface = if (isDir) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                        }
                    }

                    rowLayout.addView(nameTxt)
                    return rowLayout
                }
            }

            listView.adapter = adapter

            fun refreshFiles() {
                try {
                    pathTxt.text = currentDir.absolutePath
                    val rawFiles = currentDir.listFiles() ?: emptyArray()
                    val sorted = rawFiles.sortedWith(compareBy(
                        { !it.isDirectory },
                        { it.name.lowercase(Locale.ROOT) }
                    ))
                    filesList.clear()
                    filesList.addAll(sorted)
                    adapter.notifyDataSetChanged()
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ في قراءة المجلد: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            refreshFiles()

            val listParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(280)
            ).apply {
                weight = 1f
                bottomMargin = dpToPx(12)
            }
            mainLayout.addView(listView, listParams)

            val confirmBtn = Button(context).apply {
                text = "🎯 معالجة هذا المجلد"
                setTextColor(Color.WHITE)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#D97706"))
                    cornerRadius = dpToPx(8).toFloat()
                }
                setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                layoutParams = lp
            }
            mainLayout.addView(confirmBtn)

            builder.setView(mainLayout)
            val dialog = builder.create()

            listView.setOnItemClickListener { _, _, position, _ ->
                val isParent = currentDir.parentFile != null && position == 0
                if (isParent) {
                    val parent = currentDir.parentFile
                    if (parent != null) {
                        currentDir = parent
                        refreshFiles()
                    }
                } else {
                    val file = adapter.getItem(position) as File
                    if (file.isDirectory) {
                        currentDir = file
                        refreshFiles()
                    } else {
                        onSelected(file)
                        dialog.dismiss()
                    }
                }
            }

            confirmBtn.setOnClickListener {
                onSelected(currentDir)
                dialog.dismiss()
            }

            dialog.show()

        } catch (e: Exception) {
            Toast.makeText(context, "فشل عرض المستكشف: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
