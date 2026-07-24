package com.im_atp.volthalt.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark theme — pitch-black OLED background with a periwinkle blue accent.
private val PitchBlackColorScheme = darkColorScheme(
    primary            = Color(0xFFA5C0FF),
    onPrimary          = Color(0xFF0F172A),
    primaryContainer   = Color(0xFF2B334A),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary          = Color(0xFF4CAF50),
    onSecondary        = Color(0xFFFFFFFF),
    tertiary           = Color(0xFFFB923C),
    onTertiary         = Color(0xFF000000),
    background         = Color(0xFF000000),
    surface            = Color(0xFF1E2028),
    surfaceVariant     = Color(0xFF272932),
    onBackground       = Color(0xFFFFFFFF),
    onSurface          = Color(0xFFFFFFFF),
    onSurfaceVariant   = Color(0xFFC4C6D0),
    outline            = Color(0xFF33353F),
    outlineVariant     = Color(0xFF22242D)
)

// Light theme — clean off-white background with a rich royal blue accent.
private val ProfessionalLightColorScheme = lightColorScheme(
    primary            = Color(0xFF2563EB),
    onPrimary          = Color(0xFFFFFFFF),
    primaryContainer   = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary          = Color(0xFF16A34A),
    onSecondary        = Color(0xFFFFFFFF),
    tertiary           = Color(0xFFEA580C),
    onTertiary         = Color(0xFFFFFFFF),
    background         = Color(0xFFFAFAFA),
    surface            = Color(0xFFFFFFFF),
    surfaceVariant     = Color(0xFFF1F5F9),
    onBackground       = Color(0xFF0F172A),
    onSurface          = Color(0xFF0F172A),
    onSurfaceVariant   = Color(0xFF64748B),
    outline            = Color(0xFFE2E8F0),
    outlineVariant     = Color(0xFFCBD5E1)
)

@Composable
fun VoltHaltTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> PitchBlackColorScheme
        else      -> ProfessionalLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content
    )
}
