package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    
    val pagerState = rememberPagerState(pageCount = { 4 })
    
    // Available themes definition in Task 2 & 3 - sourced dynamically from centralized ThemeManager
    val themeList = ThemeManager.themes
    
    var selectedThemeId by remember { 
        mutableStateOf(sharedPrefs.getString("chosen_theme", "golden_dark") ?: "golden_dark") 
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBg)
    ) {
        // Shared Slow Floating Waves in standard theme colors
        val currentThemeColor = themeList.find { it.id == selectedThemeId }?.primaryColor ?: MetallicGold
        WaveBackground(waveColor = currentThemeColor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "دليل الاستخدام الفاخر",
                    color = BrightGold,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(GoldGlassBg, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )

                // Skip onboarding button
                if (pagerState.currentPage < 3) {
                    Text(
                        text = "تخطي الدليل ➔",
                        color = TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                scope.launch { pagerState.scrollToPage(3) }
                            }
                            .padding(8.dp)
                    )
                }
            }

            // Horizontal pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Custom Gorgeous Native Vector Drawing instead of fragile Lottie local assets
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(GlassWhite, CircleShape)
                            .border(1.2.dp, MetallicGold.copy(alpha = 0.35f), CircleShape)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OnboardingIllustration(page = page, themeColor = currentThemeColor)
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // Title
                    Text(
                        text = getPageTitle(page),
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Description
                    Text(
                        text = getPageDescription(page),
                        color = TextSilver.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    // Step 4 Premium Custom Theme Selection Grid
                    if (page == 3) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "👇 اختر سمة المساعد المفضلة لديك:",
                            color = BrightGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // 4 Interactive Theme Cards Grid
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            themeList.chunked(2).forEach { rowThemes ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowThemes.forEach { theme ->
                                        val isSelected = selectedThemeId == theme.id
                                        val cardBg = if (isSelected) currentThemeColor.copy(alpha = 0.18f) else GlassWhite
                                        val cardBorder = if (isSelected) currentThemeColor else GlassBorder

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(cardBg, RoundedCornerShape(12.dp))
                                                .border(
                                                    if (isSelected) 1.5.dp else 0.8.dp,
                                                    cardBorder,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    selectedThemeId = theme.id
                                                    ThemeManager.setTheme(theme.id, context)
                                                    Toast.makeText(context, "تم تطبيق سمة ${theme.nameRtl} بنجاح!", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(theme.icon, fontSize = 20.sp)
                                            Column {
                                                Text(
                                                    text = theme.nameRtl,
                                                    color = if (isSelected) BrightGold else TextSilver,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = theme.description.take(20) + "...",
                                                    color = TextMuted,
                                                    fontSize = 8.5.sp,
                                                    maxLines = 1
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

            // Bottom Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicator bullets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { idx ->
                        val isCurrent = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 16.dp else 8.dp, 8.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) currentThemeColor else GlassWhiteHeavy)
                        )
                    }
                }

                // Call to Action Buttons
                if (pagerState.currentPage < 3) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentThemeColor,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("onboarding_next_btn")
                    ) {
                        Text(
                            text = "التالي ➔",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            sharedPrefs.edit().putBoolean("has_seen_onboarding", true).apply()
                            onFinish()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrightGold,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .testTag("onboarding_finish_btn")
                            .glowEffect(glowColor = BrightGold)
                    ) {
                        Text(
                            text = "انطلق للتحفة الفنية! ✨",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingIllustration(page: Int, themeColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "illustration")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spinRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = center
        val w = size.width
        val h = size.height

        when (page) {
            0 -> {
                // Step 1: chaos turning into order (dots to structured center)
                drawCircle(
                    color = themeColor.copy(alpha = 0.15f),
                    radius = 45.dp.toPx()
                )
                
                // Draw satellite items rotating in a structured grid
                for (i in 0..7) {
                    val angle = Math.toRadians((spinRotation + i * 45).toDouble())
                    val distance = 55.dp.toPx() + pulseScale * 1.5f
                    val sX = center.x + cos(angle).toFloat() * distance
                    val sY = center.y + sin(angle).toFloat() * distance
                    
                    drawCircle(
                        color = if (i % 2 == 0) BrightGold else themeColor,
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(sX, sY)
                    )
                }

                // Glowing central diamond
                val path = Path().apply {
                    moveTo(center.x, center.y - 22.dp.toPx() - pulseScale)
                    lineTo(center.x + 22.dp.toPx() + pulseScale, center.y)
                    lineTo(center.x, center.y + 22.dp.toPx() + pulseScale)
                    lineTo(center.x - 22.dp.toPx() - pulseScale, center.y)
                    close()
                }
                drawPath(path, Brush.radialGradient(listOf(BrightGold, themeColor)))
            }
            1 -> {
                // Step 2: Code Editor simulation for Developers
                drawRoundRect(
                    color = Color(0xFF0F172A),
                    size = androidx.compose.ui.geometry.Size(90.dp.toPx(), 75.dp.toPx()),
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - 45.dp.toPx(), center.y - 37.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f)
                )

                // Outer border glow
                drawRoundRect(
                    color = themeColor.copy(alpha = 0.4f),
                    size = androidx.compose.ui.geometry.Size(90.dp.toPx(), 75.dp.toPx()),
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - 45.dp.toPx(), center.y - 37.5.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
                )

                // Draw code lines inside
                val terminalLeft = center.x - 35.dp.toPx()
                val terminalTop = center.y - 25.dp.toPx()
                
                drawLine(
                    color = themeColor,
                    start = androidx.compose.ui.geometry.Offset(terminalLeft, terminalTop),
                    end = androidx.compose.ui.geometry.Offset(terminalLeft + 35.dp.toPx(), terminalTop),
                    strokeWidth = 3.dp.toPx()
                )

                drawLine(
                    color = BrightGold,
                    start = androidx.compose.ui.geometry.Offset(terminalLeft, terminalTop + 10.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(terminalLeft + 55.dp.toPx(), terminalTop + 10.dp.toPx()),
                    strokeWidth = 3.dp.toPx()
                )

                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(terminalLeft, terminalTop + 20.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(terminalLeft + 45.dp.toPx(), terminalTop + 20.dp.toPx()),
                    strokeWidth = 3.dp.toPx()
                )

                // Floating code brackets
                drawCircle(
                    color = themeColor,
                    radius = 18.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(center.x + 40.dp.toPx(), center.y + 30.dp.toPx())
                )
            }
            2 -> {
                // Step 3: Hierarchy Tree for Researchers / Academics
                // Drawing an elegant hierarchical Node network structure
                val pTop = androidx.compose.ui.geometry.Offset(center.x, center.y - 40.dp.toPx())
                val pLeft = androidx.compose.ui.geometry.Offset(center.x - 45.dp.toPx(), center.y + 25.dp.toPx())
                val pMid = androidx.compose.ui.geometry.Offset(center.x, center.y + 25.dp.toPx())
                val pRight = androidx.compose.ui.geometry.Offset(center.x + 45.dp.toPx(), center.y + 25.dp.toPx())

                // Draw connective lines
                drawLine(color = themeColor, start = pTop, end = pLeft, strokeWidth = 2.dp.toPx())
                drawLine(color = themeColor, start = pTop, end = pMid, strokeWidth = 2.dp.toPx())
                drawLine(color = themeColor, start = pTop, end = pRight, strokeWidth = 2.dp.toPx())

                // Draw circles at nodes
                drawCircle(color = BrightGold, radius = 12.dp.toPx(), center = pTop)
                drawCircle(color = themeColor, radius = 9.dp.toPx(), center = pLeft)
                drawCircle(color = themeColor, radius = 9.dp.toPx(), center = pMid)
                drawCircle(color = themeColor, radius = 9.dp.toPx(), center = pRight)

                // Pulsating orbit factor
                drawCircle(
                    color = BrightGold,
                    radius = 18.dp.toPx() + pulseScale,
                    center = pTop,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx(), pathEffect = null)
                )
            }
            3 -> {
                // Step 4: Golden bubble widget floating central screen
                val bubbleCenter = androidx.compose.ui.geometry.Offset(center.x, center.y - 10.dp.toPx() + pulseScale)
                drawCircle(
                    color = themeColor.copy(alpha = 0.25f),
                    radius = 48.dp.toPx(),
                    center = bubbleCenter
                )

                drawCircle(
                    color = BrightGold.copy(alpha = 0.5f),
                    radius = 35.dp.toPx(),
                    center = bubbleCenter,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                drawCircle(
                    color = themeColor,
                    radius = 24.dp.toPx(),
                    center = bubbleCenter
                )
            }
        }
    }
}

private fun getPageTitle(page: Int): String {
    return when (page) {
        0 -> "أهلاً بك في عالمك الجديد"
        1 -> "💻 البيئة المخصصة للمطورين"
        2 -> "🎓 البوابة البرمجية للأكاديميين"
        3 -> "✨ طوع المساعد لتناسب رغباتك"
        else -> ""
    }
}

private fun getPageDescription(page: Int): String {
    return when (page) {
        0 -> "حياتك اليومية مليئة بالأفكار والتنقلات البرمجية والأبنية التقنية المترامية. حان وقت تنظيمها وصياغتها بروح العصر الحالية!"
        1 -> "انسخ أي كود أو صياغة برمجية، وسنقوم ببناء وتجميع المشروع وتنفيذ الأوامر تلقائياً وراء الستار وإصدار التقارير."
        2 -> "نظم مئات المراجع وأعمال الكود بطرق معملية. استخرج العناوين التعليمية والفقرات، وابنِ شجرتك التفاعلية الخاصة عبر @treedoc."
        3 -> "تحكم وخصص كل بقعة أمامك. اختر السمة الرائعة، وفعّل الفقاعة الذهبية الذكية لتبهر أصدقاءك وسير عملك."
        else -> ""
    }
}
