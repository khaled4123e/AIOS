// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AIOS brand colors
private val AIOSBlue = Color(0xFF2563EB)
private val AIOSBlueDark = Color(0xFF60A5FA)
private val AIOSSurface = Color(0xFFF8FAFC)
private val AIOSSurfaceDark = Color(0xFF0F172A)

private val LightColors = lightColorScheme(
    primary = AIOSBlue,
    onPrimary = Color.White,
    surface = AIOSSurface,
    background = AIOSSurface,
)

private val DarkColors = darkColorScheme(
    primary = AIOSBlueDark,
    onPrimary = Color.Black,
    surface = AIOSSurfaceDark,
    background = AIOSSurfaceDark,
)

/// Like your custom Color/Font setup in SwiftUI Assets.
@Composable
fun AIOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
