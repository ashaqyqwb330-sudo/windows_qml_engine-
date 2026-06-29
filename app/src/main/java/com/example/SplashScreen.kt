package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrightGold
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.SlateBg
import com.example.ui.theme.TextSilver
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    onSplashComplete: () -> Unit
) {
    // Phase control for splash screen sequence (3 seconds total)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        onSplashComplete()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    
    // Orbit rotation angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse animation for central seal
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Intro Animations (Entrance)
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030305),
                        Color(0xFF0A0A14),
                        Color(0xFF14142B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 1. Golden Starfield / Cosmic Sparkles Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = center
            val numParticles = 40
            
            // Draw floating glowing background points based on Orbit Rotation
            for (i in 0 until numParticles) {
                val seed = i * (360f / numParticles)
                val currentAngle = Math.toRadians((rotationAngle + seed).toDouble())
                // Radius expands/contracts slowly
                val waveFactor = sin(Math.toRadians((rotationAngle * 2 + seed).toDouble())).toFloat()
                val radius = 100.dp.toPx() + waveFactor * 40.dp.toPx() + (i % 5) * 15.dp.toPx()
                
                val pX = center.x + cos(currentAngle).toFloat() * radius
                val pY = center.y + sin(currentAngle).toFloat() * radius
                
                val particleSize = (i % 3 + 1.5f).dp.toPx()
                val alpha = (0.2f + 0.6f * sin(Math.toRadians((rotationAngle * 3 + seed).toDouble())).toFloat()).coerceIn(0.1f, 0.9f)
                
                drawCircle(
                    color = BrightGold,
                    radius = particleSize,
                    center = androidx.compose.ui.geometry.Offset(pX, pY),
                    alpha = alpha
                )
            }
        }

        // 2. Central Logo Panel with animations
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(900)) + scaleIn(
                initialScale = 0.82f,
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Outer circle golden frame that pulses
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                        .border(
                            width = 2.dp,
                            brush = Brush.radialGradient(
                                colors = listOf(BrightGold, MetallicGold, Color.Transparent),
                                radius = 220f
                            ),
                            shape = CircleShape
                        )
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFD700).copy(alpha = 0.18f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Emblem Text/Symbol (Crown / Gold Logo mark)
                    Text(
                        text = "🏆",
                        fontSize = 46.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // App Title: "المساعد الذكي الذهبي"
                Text(
                    text = "المساعد الذكي الذهبي",
                    color = BrightGold,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Premium Subtitle
                Text(
                    text = "مساعد التنظيم والتشغيل الفائق الذكاء",
                    color = TextSilver.copy(alpha = 0.7f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 3. Developer Credit at bottom with a delay fade-in for premium presentation
        var isCreditVisible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(800)
            isCreditVisible = true
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            AnimatedVisibility(
                visible = isCreditVisible,
                enter = fadeIn(animationSpec = tween(1200)) + slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = tween(1200)
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "من تطوير",
                        color = TextSilver.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "إدريس يوسف المداني",
                        color = MetallicGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
