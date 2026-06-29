package com.example.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

object ThemeManager {
    // Current active theme ID
    val currentTheme = mutableStateOf("golden_dark")

    // Define Theme definition holder
    data class ThemeDef(
        val id: String,
        val nameLtr: String,
        val nameRtl: String,
        val primaryColor: Color,
        val description: String,
        val icon: String,
        val isDark: Boolean
    )

    // The list of all 5 + Golden original themes
    val themes = listOf(
        ThemeDef(
            id = "golden_dark",
            nameLtr = "Golden Dark",
            nameRtl = "داكن ذهبي الفاخر",
            primaryColor = Color(0xFFD4AF37),
            description = "الهوية الكلاسيكية بتوشيح الذهب والظلال الفضية الفاخرة",
            icon = "👑",
            isDark = true
        ),
        ThemeDef(
            id = "theme_tech",
            nameLtr = "Tech Console",
            nameRtl = "النمط التقني المحترف",
            primaryColor = Color(0xFF00FF41),
            description = "مظهر داكن بالكامل مع ألوان نيون وظيفية مريحة لعين المبرمج",
            icon = "💻",
            isDark = true
        ),
        ThemeDef(
            id = "theme_academic",
            nameLtr = "Academic Scholar",
            nameRtl = "النمط الأكاديمي الوقور",
            primaryColor = Color(0xFF2C3E50),
            description = "خلفية عاجية كلاسيكية كصفحات الكتب القديمة تعزز التركيز والهدوء",
            icon = "📚",
            isDark = false
        ),
        ThemeDef(
            id = "theme_researcher",
            nameLtr = "Researcher Lab",
            nameRtl = "النمط البحثي الدقيق",
            primaryColor = Color(0xFF1565C0),
            description = "تباين بارد ونظيف جداً يركز على عرض أكوام البيانات والملفات بفعالية",
            icon = "🔬",
            isDark = false
        ),
        ThemeDef(
            id = "theme_executive",
            nameLtr = "Executive Boardroom",
            nameRtl = "النمط الإداري الفخم",
            primaryColor = Color(0xFFD4AF37),
            description = "كحلي داكن رسمي مع الذهب يضفي طابعاً من الموثوقية والسلطة والتنظيم",
            icon = "👔",
            isDark = true
        ),
        ThemeDef(
            id = "theme_everyday",
            nameLtr = "Everyday Modern",
            nameRtl = "النمط اليومي العصري",
            primaryColor = Color(0xFF6750A4),
            description = "ألوان عصرية مبهجة تشع الحيوية للمستخدم لمهام الأتمتة السهلة واليومية",
            icon = "✨",
            isDark = false
        ),
        ThemeDef(
            id = "theme_glass_light",
            nameLtr = "Frosted Glass Light",
            nameRtl = "الوضع الفاتح الزجاجي البلوري (عالي الكثافة)",
            primaryColor = Color(0xFFC09000),
            description = "تأثيرات زجاجية بلورية فائقة الكثافة والوضوح مع زجاج مضيء ناصع وخلفيات مريحة جداً وهادئة تكسو كامل شاشات التطبيق",
            icon = "💎",
            isDark = false
        ),
        ThemeDef(
            id = "theme_ivory",
            nameLtr = "Pristine Warm Ivory",
            nameRtl = "الوضع العاجي الفاخر (المريح والمنظم)",
            primaryColor = Color(0xFFB45309),
            description = "خلفية عاجية مخصصة وفائقة الراحة تكتسي بالدفء الكلاسيكي مع ميزة التحكم الذكي بمستويات دفء الحليب والعسل لراحة بصرية تامة",
            icon = "🥛",
            isDark = false
        )
    )

    // Dynamic premium feature: Ivory Warmth Level (Controls level of ivory saturation, from classic pearl to deep golden honey)
    val ivoryWarmthLevel = mutableStateOf(0.4f) // Default warmth from 0f to 1f

    fun getDynamicIvoryBackground(): Color {
        val progress = ivoryWarmthLevel.value
        // Interpolate between clean porcelain/white ivory (250, 250, 245) and warm antique honey ivory (255, 246, 218)
        val r = (250 + 5 * progress).toInt()
        val g = (250 - 4 * progress).toInt()
        val b = (245 - 27 * progress).toInt()
        return Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    fun getDynamicIvorySurface(): Color {
        val progress = ivoryWarmthLevel.value
        // Card background slightly warmer and opaque
        val r = (244 + 11 * progress).toInt()
        val g = (244 - 6 * progress).toInt()
        val b = (236 - 32 * progress).toInt()
        return Color(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    fun updateIvoryWarmth(level: Float, context: Context) {
        ivoryWarmthLevel.value = level.coerceIn(0f, 1f)
        val sharedPrefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putFloat("ivory_warmth", level).apply()
    }

    // 6. High-Density Frosted Glass Light Color Scheme
    val GlassLightDensityColorScheme = lightColorScheme(
        primary = Color(0xFFC09000),       // Deep Rich Champagne Golden bronze
        secondary = Color(0xFF4F46E5),     // Indigo Accent
        tertiary = Color(0xFF0D9488),      // Modern Teal Accent
        background = Color(0xFFEBF1F6),    // Frost/ice silver-blue background
        surface = Color(0xFFF8FAFC),       // Clean light slate/white card
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1E293B),  // Dark slate text
        onSurface = Color(0xFF1E293B),
        error = Color(0xFFEF4444)
    )

    // Cosmic Gold Scheme (Original Luxurious Brand)
    val CosmicGoldColorScheme = darkColorScheme(
        primary = Color(0xFFD4AF37),
        secondary = Color(0xFF4ADE80),
        tertiary = Color(0xFFFFD700),
        background = Color(0xFF0F1113),
        surface = Color(0xFF1A1C1E),
        onPrimary = Color(0xFF0F1113),
        onSecondary = Color(0xFF0F1113),
        onBackground = Color(0xFFE2E2E6),
        onSurface = Color(0xFFE2E2E6),
        error = Color(0xFFEF4444)
    )

    // 1. Tech Console (Developer) Color Scheme
    val TechConsoleColorScheme = darkColorScheme(
        primary = Color(0xFF00FF41),       // Matrix Green
        secondary = Color(0xFF00FFCC),     // Cyan glow
        tertiary = Color(0xFF00E676),
        background = Color(0xFF0A0C0E),    // Clean Black
        surface = Color(0xFF14181E),       // Custom Terminal Card
        onPrimary = Color(0xFF0A0C0E),
        onSecondary = Color(0xFF0A0C0E),
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFF00FF41),     // Green font for standard surfaces
        error = Color(0xFFEF4444)
    )

    // 2. Academic Scholar Color Scheme
    val AcademicQuietColorScheme = lightColorScheme(
        primary = Color(0xFF2C3E50),       // Academic Blue
        secondary = Color(0xFFB45309),     // Scholar Gold
        tertiary = Color(0xFF1E293B),
        background = Color(0xFFFDF6E3),    // Scholar Warm Ivory Page
        surface = Color(0xFFF5F0E0),       // Darker Soft Ivory Paper Card
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF3E2723),  // Deep brown scholar text
        onSurface = Color(0xFF212121),
        error = Color(0xFFD97706)
    )

    // 3. Researcher Lab Color Scheme
    val ResearcherLabColorScheme = lightColorScheme(
        primary = Color(0xFF1565C0),       // Royal Blue
        secondary = Color(0xFF00796B),     // Clinical Teal
        tertiary = Color(0xFFE65100),      // Warning Orange
        background = Color(0xFFFAFAFA),    // Medical Pure White
        surface = Color(0xFFF1F5F9),       // Clinical cool grey Card
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1A1A1A),
        onSurface = Color(0xFF1A1A1A),
        error = Color(0xFFEF4444)
    )

    // 4. Executive Boardroom Color Scheme
    val ExecutiveBoardroomColorScheme = darkColorScheme(
        primary = Color(0xFFD4AF37),       // Premium Gold Accent
        secondary = Color(0xFFB8960C),
        tertiary = Color(0xFFB0BEC5),
        background = Color(0xFF0A1628),    // Serious Deep Corporate Navy
        surface = Color(0xFF13253F),       // Corporate Slate Navy Card
        onPrimary = Color(0xFF0A1628),
        onSecondary = Color(0xFF0A1628),
        onBackground = Color(0xFFECEFF1),
        onSurface = Color(0xFFFFFFFF),
        error = Color(0xFFEF4444)
    )

    // 5. Everyday Modern Color Scheme (Material You Vibes)
    val EverydayModernColorScheme = lightColorScheme(
        primary = Color(0xFF6750A4),       // Modern Soft Purple
        secondary = Color(0xFF4CAF50),
        tertiary = Color(0xFFFF9800),
        background = Color(0xFFFFFBEF),    // Soft Warm Off-White
        surface = Color(0xFFF3E8FF),       // Lavender Slate Card
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1C1B1F),
        onSurface = Color(0xFF1C1B1F),
        error = Color(0xFFBA1A1A)
    )

    // Load active theme state from shared preferences
    fun init(context: Context) {
        val sharedPrefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        currentTheme.value = sharedPrefs.getString("chosen_theme", "golden_dark") ?: "golden_dark"
        ivoryWarmthLevel.value = sharedPrefs.getFloat("ivory_warmth", 0.4f)
    }

    // Set theme and notify listeners instantly
    fun setTheme(themeId: String, context: Context) {
        currentTheme.value = themeId
        val sharedPrefs = context.getSharedPreferences("SmartPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("chosen_theme", themeId).apply()
    }

    fun getIvoryColorScheme(): ColorScheme {
        return lightColorScheme(
            primary = Color(0xFFB45309),       // Soft Warm Amber/Gold
            secondary = Color(0xFF0F766E),     // Calming Forest Teal
            tertiary = Color(0xFF7C2D12),      // Luxurious Terracotta
            background = getDynamicIvoryBackground(),
            surface = getDynamicIvorySurface(),
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF292524),  // Dark stone/charcoal for premium style
            onSurface = Color(0xFF292524),
            error = Color(0xFFDC2626)
        )
    }

    // Lookup Color Scheme
    fun getColorScheme(themeId: String): ColorScheme {
        return when (themeId) {
            "theme_tech" -> TechConsoleColorScheme
            "theme_academic" -> AcademicQuietColorScheme
            "theme_researcher" -> ResearcherLabColorScheme
            "theme_executive" -> ExecutiveBoardroomColorScheme
            "theme_everyday" -> EverydayModernColorScheme
            "theme_glass_light" -> GlassLightDensityColorScheme
            "theme_ivory" -> getIvoryColorScheme()
            else -> CosmicGoldColorScheme
        }
    }
}
