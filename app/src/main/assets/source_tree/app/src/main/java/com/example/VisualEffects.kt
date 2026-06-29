package com.example

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.SlateBg
import com.example.ui.theme.ThemeManager
import kotlin.math.sin
import kotlin.math.cos

/**
 * Animated Wave Background that draws fluid, organic, and slow-moving waves 
 * in the background using sine wave equations.
 */
@Composable
fun WaveBackground(
    modifier: Modifier = Modifier,
    waveColor: Color = MetallicGold
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")
    
    // Animate phases for different wave layers to make them look distinct and rich
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 2f * Math.PI.toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_2"
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_3"
    )

    // Classic continuous ticker to drive rotational and translational movement of electrons and atoms
    val rawTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "raw_time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        clipRect {
            val width = size.width
            val height = size.height

            // Base deep background color
            drawRect(color = SlateBg)

            // Layer 1: Bottom Slow Deep Wave
            val path1 = Path().apply {
                moveTo(0f, height)
                for (x in 0..width.toInt() step 10) {
                    val angle = (x.toFloat() / width) * 2f * Math.PI.toFloat() + phase1
                    val y = height - 120.dp.toPx() + sin(angle) * 30.dp.toPx()
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                close()
            }
            drawPath(
                path = path1,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        waveColor.copy(alpha = 0.0f),
                        waveColor.copy(alpha = 0.05f)
                    ),
                    startY = height - 180.dp.toPx(),
                    endY = height
                )
            )

            // Layer 2: Middle Overlapping Wave
            val path2 = Path().apply {
                moveTo(0f, height)
                for (x in 0..width.toInt() step 10) {
                    val angle = (x.toFloat() / width) * 3f * Math.PI.toFloat() + phase2
                    val y = height - 100.dp.toPx() + sin(angle) * 24.dp.toPx()
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, height)
                close()
            }
            drawPath(
                path = path2,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        waveColor.copy(alpha = 0.0f),
                        waveColor.copy(alpha = 0.08f)
                    ),
                    startY = height - 140.dp.toPx(),
                    endY = height
                )
            )

            // Layer 3: Subtle top wave in corner
            val path3 = Path().apply {
                moveTo(0f, 0f)
                for (x in 0..width.toInt() step 10) {
                    val angle = (x.toFloat() / width) * 1.5f * Math.PI.toFloat() + phase3
                    val y = 60.dp.toPx() + sin(angle) * 15.dp.toPx()
                    lineTo(x.toFloat(), y)
                }
                lineTo(width, 0f)
                close()
            }
            drawPath(
                path = path3,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        waveColor.copy(alpha = 0.03f),
                        waveColor.copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = 100.dp.toPx()
                )
            )

            // Dynamic Atomic Particle Network Overlay (Electrons, Nucleus & Orbits)
            val activeThemeId = try { ThemeManager.currentTheme.value } catch (e: Exception) { "golden_dark" }
            
            // Adjust transparency of particles: more prominent in neon tech theme, elegant in others
            val isTechNeon = activeThemeId == "theme_tech"
            val baseOpacity = if (isTechNeon) 0.65f else 0.4f
            
            // 6 Atom positions as proportional fractions of screen dimensions
            val fractionX = floatArrayOf(0.12f, 0.85f, 0.22f, 0.78f, 0.48f, 0.86f)
            val fractionY = floatArrayOf(0.18f, 0.22f, 0.58f, 0.62f, 0.38f, 0.82f)
            val orbitCount = intArrayOf(1, 2, 1, 2, 1, 1) // Atoms 1 and 3 have dual crossed orbits
            val baseOrbitAngles = arrayOf(
                floatArrayOf(-30f),
                floatArrayOf(45f, -45f),
                floatArrayOf(15f),
                floatArrayOf(-15f, 75f),
                floatArrayOf(60f),
                floatArrayOf(-40f)
            )
            val orbitRadiusX = floatArrayOf(25f, 32f, 22f, 28f, 24f, 26f)
            val orbitRadiusY = floatArrayOf(9f, 13f, 8f, 11f, 9f, 10f)
            
            val speedX = floatArrayOf(0.04f, -0.05f, 0.03f, -0.04f, 0.02f, -0.03f)
            val speedY = floatArrayOf(0.05f, 0.04f, -0.04f, 0.03f, -0.03f, 0.04f)
            val offsetPhase = floatArrayOf(0f, 1.2f, 2.4f, 3.6f, 4.8f, 6.0f)
            
            val atomPositions = Array(6) { Offset(0f, 0f) }
            
            // Compute real coordinates for each atomic nucleus
            for (i in 0 until 6) {
                val bX = width * fractionX[i]
                val bY = height * fractionY[i]
                
                val fX = sin(rawTime * speedX[i] + offsetPhase[i]) * 15.dp.toPx()
                val fY = cos(rawTime * speedY[i] + offsetPhase[i]) * 15.dp.toPx()
                
                atomPositions[i] = Offset(bX + fX, bY + fY)
            }
            
            // Step 1: Draw Interconnecting atomic quantum bonds between near elements
            val maxBondDist = 180.dp.toPx()
            for (i in 0 until 6) {
                for (j in (i + 1) until 6) {
                    val p1 = atomPositions[i]
                    val p2 = atomPositions[j]
                    val dx = p1.x - p2.x
                    val dy = p1.y - p2.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    
                    if (dist < maxBondDist) {
                        // Blend alpha based on distance
                        val alpha = (1f - dist / maxBondDist) * 0.14f * baseOpacity
                        drawLine(
                            color = waveColor,
                            start = p1,
                            end = p2,
                            strokeWidth = 1.dp.toPx(),
                            alpha = alpha
                        )
                    }
                }
            }
            
            // Step 2: Draw individual Nuclei spheres, Orbits ellipses and Revolving electrons
            for (i in 0 until 6) {
                val p = atomPositions[i]
                val radX = orbitRadiusX[i].dp.toPx()
                val radY = orbitRadiusY[i].dp.toPx()
                
                // Draw central Nucleus double layers
                drawCircle(
                    color = waveColor.copy(alpha = 0.22f * baseOpacity),
                    radius = 4.dp.toPx(),
                    center = p
                )
                drawCircle(
                    color = waveColor.copy(alpha = 0.08f * baseOpacity),
                    radius = 11.dp.toPx(),
                    center = p
                )
                
                // Draw orbits paths and rotating electrons
                val count = orbitCount[i]
                for (o in 0 until count) {
                    val ang = baseOrbitAngles[i][o]
                    val rotSpeedMultiplier = if (o == 1) -1.8f else 1.5f
                    val phaseShift = o * 180f
                    
                    // Rotate rendering scope around atomic nucleus
                    this.rotate(degrees = ang, pivot = p) {
                        // Draw orbit oval trace
                        drawOval(
                            color = waveColor.copy(alpha = 0.09f * baseOpacity),
                            topLeft = Offset(p.x - radX, p.y - radY),
                            size = Size(radX * 2, radY * 2),
                            style = Stroke(width = 1.dp.toPx())
                        )
                        
                        // Compute electron angle matching time sequence
                        val electronAngleRad = ((rawTime * rotSpeedMultiplier + phaseShift) * (Math.PI / 180.0)).toFloat()
                        val eX = radX * cos(electronAngleRad)
                        val eY = radY * sin(electronAngleRad)
                        
                        // Electron bright glowing dot
                        drawCircle(
                            color = waveColor.copy(alpha = 0.85f * baseOpacity),
                            radius = 2.5.dp.toPx(),
                            center = Offset(p.x + eX, p.y + eY)
                        )
                        // Electron high density neon aura
                        drawCircle(
                            color = waveColor.copy(alpha = 0.25f * baseOpacity),
                            radius = 6.dp.toPx(),
                            center = Offset(p.x + eX, p.y + eY)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modifier to apply an eye-catching breathing/pulsing golden glow outline.
 * Ideal for premium components and highlight states.
 */
fun Modifier.glowEffect(
    enabled: Boolean = true,
    glowColor: Color = MetallicGold,
    maxAlpha: Float = 0.35f
): Modifier = composed {
    if (!enabled) return@composed this

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_glow"
    )

    this.drawWithContent {
        drawContent()
        // Draw elegant glowing outer halo
        drawCircle(
            color = glowColor.copy(alpha = glowAlpha),
            radius = (size.maxDimension / 2f) * scaleFactor,
            center = Offset(size.width / 2f, size.height / 2f),
            alpha = glowAlpha
        )
    }
}
