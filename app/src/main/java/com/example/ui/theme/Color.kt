package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Premium Frosted Glass Slate & Gold Theme Palette
val SlateBg: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xFFE9EFF5) // Gilded frozen ice-blue base background for Light Mode
        "theme_ivory" -> ThemeManager.getDynamicIvoryBackground() // Dynamically computed luxury warm ivory background
        else -> Color(0xFF0F1113) // Slate design dark
    }

val CardSlateBg: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xFFF1F5F9) // Clean frosted base tile
        "theme_ivory" -> ThemeManager.getDynamicIvorySurface() // Dynamically computed luxury warm ivory surface
        else -> Color(0xFF1A1C1E)
    }

val MetallicGold = Color(0xFFD4AF37)

val BrightGold: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xFFB45309) // Stable readable Amber/Gold for light mode
        "theme_ivory" -> Color(0xFFB45309) // Rich elegant terracotta amber for ivory mode
        else -> Color(0xFFFFD700)
    }

val DarkGold = Color(0xFF996515)
val EmeraldGlow = Color(0xFF4ADE80)
val DangerRed = Color(0xFFEF4444)

val TextSilver: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xFF1E293B) // Rich Slate text for light mode
        "theme_ivory" -> Color(0xFF292524) // Deep stone grey for ultimate luxury contrast on ivory
        else -> Color(0xFFE2E2E6)
    }

val TextGray: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xFF475569) // Mid slate text
        "theme_ivory" -> Color(0xFF57534E) // Warm granite gray for subheaders
        else -> Color(0xFF9CA3AF)
    }

val TextMuted: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xFF64748B) // Fully faded details text
        "theme_ivory" -> Color(0xFF78716C) // Soft warm clay for muted text labels
        else -> Color(0xFF6B7280)
    }

// Glassmorphism components translucencies (Fully Dynamic based on theme)
val GlassWhite: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xD9FFFFFF) // High-density light frosted glass backdrop (85% opacity white)
        "theme_ivory" -> Color(0xC0FFFFFF) // Soft white glaze (75% opacity) overlay on warm background
        else -> Color(0x0CFFFFFF)
    }

val GlassWhiteMedium: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xEDF2F7FF) // Brighter double-layered frosted glass plate
        "theme_ivory" -> Color(0xE6FFFDF5) // Beautiful premium cream/honey warm glass
        else -> Color(0x18FFFFFF)
    }

val GlassWhiteHeavy: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0xF5F8FBFF) // Solid thick glazed white panel
        "theme_ivory" -> Color(0xF2FFFDF0) // Opaque rich milk glass panel
        else -> Color(0x2BFFFFFF)
    }

val GlassBorder: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0x5594A3B8) // Perfect light-slate border stroke
        "theme_ivory" -> Color(0x3BB45309) // Delicate golden-amber bronze border strokes
        else -> Color(0x20FFFFFF)
    }

val GoldGlassBg: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0x2BD4AF37) // Warm golden backlight glow
        "theme_ivory" -> Color(0x2BB45309) // Luxurious soft dynamic golden-honey glow
        else -> Color(0x1AD4AF37)
    }

val GlassBlack: Color
    get() = when (ThemeManager.currentTheme.value) {
        "theme_glass_light" -> Color(0x0A000000) // Super soft drop shadow
        "theme_ivory" -> Color(0x081C1917) // Calm organic light drop shadow
        else -> Color(0x40000000)
    }
