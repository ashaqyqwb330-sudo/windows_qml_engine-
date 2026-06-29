package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.LogEntity
import com.example.ui.theme.*
import com.example.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StorytellerScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val eventLogsState = viewModel.eventLogs.collectAsState(initial = emptyList())
    val rawLogs = eventLogsState.value

    val smartPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
    var logViewMode by remember {
        mutableStateOf(smartPrefs.getString("log_view_mode", "technical") ?: "technical")
    }

    // Sync state dynamically when visible
    LaunchedEffect(Unit) {
        logViewMode = smartPrefs.getString("log_view_mode", "technical") ?: "technical"
    }

    // Categories computations for Pie Chart
    val totalCount = rawLogs.size
    val filesCount = rawLogs.count { it.type == "builder" || it.type == "treedoc" }
    val cmdsCount = rawLogs.count { it.type == "executor" }
    val aiCount = rawLogs.count { it.type == "gemini" }
    val sysCount = rawLogs.count { it.type == "system" || it.type == "clipboard_service" || it.type == "bubble" || it.type == "clipboard_monitor" }
    val otherCount = totalCount - (filesCount + cmdsCount + aiCount + sysCount)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("storyteller_screen_container"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // HEADER SUMMARY CARD
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                CardSlateBg,
                                SlateBg
                            )
                        )
                    )
                    .border(BorderStroke(1.2.dp, Brush.linearGradient(listOf(MetallicGold, BrightGold))), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✨ راوي القصة المصورة",
                            color = BrightGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .background(GoldGlassBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "حصاد العمليات",
                                color = MetallicGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "يقوم راوي القصص بتحليل سجل الأحداث التقني الجاف واستخلاص بطاقات ذكية حية تعبر عن نشاطك في المشروع الحالي بأسلوب تفاعلي مريح.",
                        color = TextGray,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Live Summary sentence based on counts
                    if (totalCount > 0) {
                        val durationText = "في الفترة الأخيرة"
                        Text(
                            text = "🔄 $durationText: قام النظام برصد $totalCount عمليات مسجلة، تم تصنيفها وحمايتها بالكامل، وتعمل بيئتك الأمنية بأقصى كفاءة.",
                            color = EmeraldGlow,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = "👋 مرحباً بك! لا توجد أحداث مسجلة حالياً لبناء القصة المصورة منها. ابدأ بإنشاء ملفات أو تشغيل أوامر لترى السحر!",
                            color = TextSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // MODE CONTROLS / RADIO SELECTORS (شخصية العرض)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "🎭 شخصية الراوي (صياغة العرض)",
                        color = MetallicGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = "اختر مستوى الصياغة والمصطلحات المفضلة لديك لعرض قصة الأحداث:",
                        color = TextGray,
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val modes = listOf(
                        Triple("technical", "⚙️ تقني ومفصل", "عرض سجل الأكواد بالصيغة الخام المباشرة."),
                        Triple("developer", "💻 قصة مصورة (مطور)", "صياغات برمجية دقيقة تتمحور حول الملفات والعمليات."),
                        Triple("academic", "🎓 قصة مصورة (أكاديمي)", "أسلوب هيكلي فلسفي رصين يبرز تكامل البنية المعرفية."),
                        Triple("user", "👥 قصة مصورة (مستخدم)", "جمل وعبارات بسيطة مفهومة للجميع تسرد ما تم إنجازه.")
                    )

                    modes.forEach { (modeKey, title, desc) ->
                        val isSelected = logViewMode == modeKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) GoldGlassBg else Color.Transparent)
                                .clickable {
                                    logViewMode = modeKey
                                    smartPrefs.edit().putString("log_view_mode", modeKey).apply()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    logViewMode = modeKey
                                    smartPrefs.edit().putString("log_view_mode", modeKey).apply()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MetallicGold,
                                    unselectedColor = TextGray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = if (isSelected) BrightGold else TextSilver,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = desc, color = TextGray, fontSize = 9.5.sp)
                            }
                        }
                    }
                }
            }
        }

        // STATS SUMMARY & COMPOSABLE PIE CHART
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📊 توزيع هيكل الأنشطة والأحداث",
                        color = MetallicGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Numerical Stats Cards (Grid-like Row)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCounterMini(
                            modifier = Modifier.weight(1f),
                            title = "تعديلات الملفات",
                            count = filesCount,
                            color = BrightGold,
                            icon = Icons.Default.Build
                        )
                        StatCounterMini(
                            modifier = Modifier.weight(1f),
                            title = "الأوامر المنفذة",
                            count = cmdsCount,
                            color = EmeraldGlow,
                            icon = Icons.Default.PlayArrow
                        )
                        StatCounterMini(
                            modifier = Modifier.weight(1f),
                            title = "استعلامات الذكاء",
                            count = aiCount,
                            color = Color(0xFF818CF8),
                            icon = Icons.Default.Star
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Canvas Dynamic Animated Pie Chart representation
                    if (totalCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardSlateBg.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .border(BorderStroke(0.6.dp, GlassBorder), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Circular Chart on Left
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val slices = listOf(
                                        Pair(filesCount.toFloat(), BrightGold),
                                        Pair(cmdsCount.toFloat(), EmeraldGlow),
                                        Pair(aiCount.toFloat(), Color(0xFF818CF8)),
                                        Pair(sysCount.toFloat(), Color(0xFFFF8A65)),
                                        Pair(otherCount.coerceAtLeast(0).toFloat(), TextGray)
                                    ).filter { it.first > 0f }

                                    var startAngle = -90f
                                    val total = slices.sumOf { it.first.toDouble() }.toFloat()

                                    if (total > 0f) {
                                        slices.forEach { (count, color) ->
                                            val sweepAngle = (count / total) * 360f
                                            drawArc(
                                                color = color,
                                                startAngle = startAngle,
                                                sweepAngle = sweepAngle,
                                                useCenter = false,
                                                style = Stroke(width = 24f, cap = StrokeCap.Round),
                                                size = Size(size.width, size.height)
                                            )
                                            startAngle += sweepAngle
                                        }
                                    } else {
                                        // Draw default hollow circle
                                        drawArc(
                                            color = TextMuted,
                                            startAngle = 0f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = Stroke(width = 16f),
                                            size = Size(size.width, size.height)
                                        )
                                    }
                                }

                                // Center text inside Donut Chart
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = "$totalCount", color = TextSilver, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Text(text = "عملية", color = TextMuted, fontSize = 9.sp)
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Colorized Legend details on Right
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LegendItem(label = "الملفات والبناء", count = filesCount, percentage = getPercent(filesCount, totalCount), color = BrightGold)
                                LegendItem(label = "أوامر التشغيل", count = cmdsCount, percentage = getPercent(cmdsCount, totalCount), color = EmeraldGlow)
                                LegendItem(label = "استدعاء جمناي", count = aiCount, percentage = getPercent(aiCount, totalCount), color = Color(0xFF818CF8))
                                LegendItem(label = "أحداث النظام", count = sysCount + otherCount, percentage = getPercent(sysCount + otherCount, totalCount), color = Color(0xFFFF8A65))
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .background(CardSlateBg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "توزيع خريطة العمليات خالي لعدم وجود أحداث.", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // EXPORT CONTROL ACTIONS PANEL
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "📤 مشاركة وتصدير قصص العمليات",
                        color = MetallicGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "حول سجلاتك لمنتج مرئي أو انسخ فقط السكربتات والأوامر التي تم تنفيذها لإعادة تشغيلها:",
                        color = TextGray,
                        fontSize = 10.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Export HTML Storyteller Report
                        Button(
                            onClick = {
                                if (rawLogs.isEmpty()) {
                                    Toast.makeText(context, "⚠️ لا توجد سجلات حالياً لتصدير التقرير المصور!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val storytellingHtml = generateStorytellerHtml(rawLogs, logViewMode)
                                    AppReportHelper.saveAndOpenHtmlReport(context, storytellingHtml)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MetallicGold,
                                contentColor = SlateBg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("export_storytelling_html_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("🌐 تصدير القصة التفاعلية", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 2. Export Executor Scripts (Raw Commands)
                        Button(
                            onClick = {
                                val execLogs = rawLogs.filter { it.type == "executor" }
                                if (execLogs.isEmpty()) {
                                    Toast.makeText(context, "⚠️ لم يتم العثور على أوامر نظام منفذة لنسخها!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val scriptText = buildString {
                                        appendLine("#!/bin/bash")
                                        appendLine("# تم تصديرها من راوي السجل الذهبي للمشروع")
                                        appendLine("# تاريخ النسخ: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                                        appendLine("=========================================")
                                        execLogs.forEach { log ->
                                            appendLine(log.details ?: log.message)
                                        }
                                    }
                                    clipboardManager.setText(AnnotatedString(scriptText))
                                    Toast.makeText(context, "📋 تم نسخ ${execLogs.size} من الأوامر والسكربتات للحافظة ككود!", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CardSlateBg,
                                contentColor = MetallicGold
                            ),
                            border = BorderStroke(1.dp, MetallicGold.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("copy_exec_scripts_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("💻 نسخ سكربت التشغيل", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // STORIES SEGMENT HEADER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔔 تسلسل بطاقات القصة النشطة",
                    color = TextSilver,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .background(GlassWhite, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    val countToShow = if (rawLogs.size > 50) 50 else rawLogs.size
                    Text(
                        text = "عرض آخر $countToShow حدثاً مجمعاً",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // STORY CARDS LIST
        if (rawLogs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(GlassWhite, RoundedCornerShape(16.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                        Text("السجل خالي، لم تتم أي عمليات لكتابة قصة عنها.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            val stories = LogAggregator.generateStoryCards(rawLogs, logViewMode)
            items(stories, key = { it.id }) { story ->
                StoryCardView(
                    story = story,
                    onCopyClick = { summaryText ->
                        clipboardManager.setText(AnnotatedString(summaryText))
                        Toast.makeText(context, "📋 تم نسخ ملخص القصة تفصيلياً", Toast.LENGTH_SHORT).show()
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
    }
}

@Composable
fun StatCounterMini(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = modifier
            .background(CardSlateBg, RoundedCornerShape(14.dp))
            .border(0.8.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            }
            Text(text = title, color = TextGray, fontSize = 9.sp)
            Text(
                text = "$count",
                color = TextSilver,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun LegendItem(
    label: String,
    count: Int,
    percentage: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(text = label, color = TextSilver, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            text = "$count ($percentage%)",
            color = TextGray,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StoryCardItem(story: StoryCard) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassWhite, RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-fidelity Colorized circle frame for story icon
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(GoldGlassBg, CircleShape)
                    .border(1.dp, MetallicGold.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = story.icon, fontSize = 18.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = story.title,
                        color = TextSilver,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Multi-event badge indicator
                    if (story.rawLogsCount > 1) {
                        Box(
                            modifier = Modifier
                                .background(GoldGlassBg, RoundedCornerShape(6.dp))
                                .border(0.5.dp, MetallicGold.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "×${story.rawLogsCount}",
                                color = MetallicGold,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = story.details,
                    color = TextGray,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Relative Time badge footer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = EmeraldGlow,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = story.relativeTime,
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun getPercent(count: Int, total: Int): Int {
    if (total == 0) return 0
    return ((count.toFloat() / total) * 100).toInt()
}

/**
 * Custom generation of a stunning interactive storytelling report with clean CSS, responsiveness
 * and SVG based details to perfectly match aesthetic expectations.
 */
fun generateStorytellerHtml(logs: List<LogEntity>, mode: String): String {
    val stories = LogAggregator.generateStoryCards(logs, mode)
    val totalCount = logs.size
    val filesCount = logs.count { it.type == "builder" || it.type == "treedoc" }
    val cmdsCount = logs.count { it.type == "executor" }
    val aiCount = logs.count { it.type == "gemini" }
    
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val formattedDate = sdf.format(Date())

    val storyBlocks = StringBuilder()
    for (story in stories) {
        val qtyBadge = if (story.rawLogsCount > 1) {
            "<span class='badge'>×${story.rawLogsCount}</span>"
        } else ""

        storyBlocks.append("""
            <div class="story-card">
                <div class="story-icon">${story.icon}</div>
                <div class="story-body">
                    <div class="story-header">
                        <span class="story-title">${story.title}</span>
                        $qtyBadge
                    </div>
                    <div class="story-details">${story.details}</div>
                    <div class="story-time">🎯 ${story.relativeTime}</div>
                </div>
            </div>
        """.trimIndent())
    }

    return """
        <!DOCTYPE html>
        <html lang="ar" dir="rtl">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>لوحة تفاصيل القصة المصورة - الراوي الذهبي</title>
            <style>
                :root {
                    --bg-dark: #0f1113;
                    --card-bg: #1a1c1e;
                    --primary-gold: #d4af37;
                    --bright-gold: #ffd700;
                    --text-silver: #e2e2e6;
                    --text-gray: #9ca3af;
                    --border-glass: rgba(255, 255, 255, 0.1);
                    --success-glow: #4ade80;
                }
                body {
                    background-color: var(--bg-dark);
                    color: var(--text-silver);
                    font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    margin: 0;
                    padding: 24px;
                    line-height: 1.6;
                }
                .container {
                    max-width: 900px;
                    margin: 0 auto;
                }
                .header {
                    background: linear-gradient(135deg, #1a1c1e 0%, #101112 100%);
                    border: 1px solid var(--primary-gold);
                    border-radius: 20px;
                    padding: 30px;
                    margin-bottom: 24px;
                    text-align: center;
                    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
                }
                .header h1 {
                    color: var(--bright-gold);
                    margin: 0 0 10px 0;
                    font-size: 28px;
                    text-shadow: 0 0 15px rgba(212, 175, 55, 0.4);
                }
                .header p {
                    color: var(--text-gray);
                    margin: 0;
                    font-size: 14px;
                }
                .stats-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 16px;
                    margin-bottom: 24px;
                }
                .stat-card {
                    background-color: var(--card-bg);
                    border: 1px solid var(--border-glass);
                    border-radius: 16px;
                    padding: 20px;
                    text-align: center;
                    transition: transform 0.2s;
                }
                .stat-card:hover {
                    transform: translateY(-2px);
                    border-color: var(--primary-gold);
                }
                .stat-num {
                    font-size: 32px;
                    font-weight: 800;
                    color: var(--bright-gold);
                    margin-bottom: 4px;
                }
                .stat-label {
                    color: var(--text-gray);
                    font-size: 13px;
                }
                .stories-title {
                    font-size: 18px;
                    font-weight: bold;
                    margin: 30px 0 16px 0;
                    color: var(--bright-gold);
                    border-right: 4px solid var(--primary-gold);
                    padding-right: 12px;
                }
                .story-card {
                    display: flex;
                    gap: 16px;
                    background-color: var(--card-bg);
                    border: 1px solid var(--border-glass);
                    border-radius: 16px;
                    padding: 20px;
                    margin-bottom: 12px;
                    align-items: center;
                    transition: border-color 0.2s;
                }
                .story-card:hover {
                    border-color: rgba(212, 175, 55, 0.4);
                }
                .story-icon {
                    font-size: 24px;
                    width: 48px;
                    height: 48px;
                    border-radius: 50%;
                    background-color: rgba(212, 175, 55, 0.15);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    border: 1px solid rgba(212, 175, 55, 0.3);
                }
                .story-body {
                    flex: 1;
                }
                .story-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                }
                .story-title {
                    font-weight: bold;
                    font-size: 15px;
                    color: #fff;
                }
                .badge {
                    background-color: rgba(212, 175, 55, 0.2);
                    color: var(--bright-gold);
                    padding: 2px 8px;
                    border-radius: 8px;
                    font-size: 11px;
                    font-weight: bold;
                    border: 1px solid rgba(212, 175, 55, 0.4);
                }
                .story-details {
                    color: var(--text-gray);
                    font-size: 13px;
                    margin-top: 6px;
                }
                .story-time {
                    color: var(--primary-gold);
                    font-size: 11px;
                    font-weight: bold;
                    margin-top: 8px;
                }
                .footer {
                    text-align: center;
                    margin-top: 40px;
                    color: var(--text-gray);
                    font-size: 12px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>✨ تقرير القصة المصورة - الراوي الذهبي</h1>
                    <p>المستخلص الآلي وحصاد العمليات التفاعلي للمشروع الحالي</p>
                    <p style="margin-top:8px; font-size:12px; color: var(--primary-gold);">تاريخ الاستخراج: $formattedDate</p>
                </div>

                <div class="stats-grid">
                    <div class="stat-card">
                        <div class="stat-num">$totalCount</div>
                        <div class="stat-label">إجمالي الأحداث</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-num">$filesCount</div>
                        <div class="stat-label">تعديلات الملفات وبنائها</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-num">$cmdsCount</div>
                        <div class="stat-label">الأوامر المنفذة</div>
                    </div>
                    <div class="stat-card">
                        <div class="stat-num">$aiCount</div>
                        <div class="stat-label">طلبات جمناي الذكية</div>
                    </div>
                </div>

                <div class="stories-title">🔔 سجل بطاقات رواية القصص</div>
                
                <div class="stories-container">
                    $storyBlocks
                </div>

                <div class="footer">
                    تم التوليد تلقائياً بواسطة مشروع المراقب المطور الذكي. جميع الحقوق محفوظة © 2026.
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
}
