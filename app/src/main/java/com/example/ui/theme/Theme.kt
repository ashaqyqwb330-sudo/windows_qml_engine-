package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val activeId = ThemeManager.currentTheme.value
    val activeColorScheme = ThemeManager.getColorScheme(activeId)

    MaterialTheme(
        colorScheme = activeColorScheme,
        typography = Typography,
        content = content
    )
}

