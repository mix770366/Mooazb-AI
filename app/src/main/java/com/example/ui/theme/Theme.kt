package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.ReasoningLevel

private const val THEME_TRANSITION_MS = 420

@Immutable
data class CyberPalette(
    val accent: Color,
    val accentContainer: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val glow: Color
)

private val DefaultCyberPalette = CyberPalette(
    accent = ElectricCyan,
    accentContainer = DeepCyanContainer,
    background = ObsidianBg,
    surface = CyberSlate,
    surfaceElevated = CyberSlateElevated,
    border = CyberBorder,
    glow = ElectricCyan.copy(alpha = 0.16f)
)

val LocalCyberPalette = staticCompositionLocalOf { DefaultCyberPalette }

fun cyberPaletteForTier(level: ReasoningLevel): CyberPalette = when (level) {
    ReasoningLevel.LOW -> CyberPalette(
        accent = SecurityEmerald,
        accentContainer = DeepEmeraldContainer,
        background = Color(0xFF060B0E),
        surface = Color(0xFF0C1718),
        surfaceElevated = Color(0xFF122325),
        border = Color(0xFF1E3E3B),
        glow = SecurityEmerald.copy(alpha = 0.14f)
    )
    ReasoningLevel.MEDIUM -> CyberPalette(
        accent = VsCodeBlue,
        accentContainer = Color(0xFF0C3554),
        background = Color(0xFF070B13),
        surface = Color(0xFF0E1624),
        surfaceElevated = Color(0xFF152236),
        border = Color(0xFF213A5E),
        glow = VsCodeBlue.copy(alpha = 0.16f)
    )
    ReasoningLevel.HIGH -> DefaultCyberPalette
    ReasoningLevel.XHIGH -> CyberPalette(
        accent = ComputeAmber,
        accentContainer = DeepAmberContainer,
        background = Color(0xFF0D0A06),
        surface = Color(0xFF18130D),
        surfaceElevated = Color(0xFF231B12),
        border = Color(0xFF4A3319),
        glow = ComputeAmber.copy(alpha = 0.20f)
    )
}

val CoreMindShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun CoreMindTheme(
    reasoningLevel: ReasoningLevel = ReasoningLevel.HIGH,
    content: @Composable () -> Unit
) {
    val target = cyberPaletteForTier(reasoningLevel)
    val spec = tween<Color>(durationMillis = THEME_TRANSITION_MS)

    val accent by animateColorAsState(targetValue = target.accent, animationSpec = spec, label = "cyber_accent")
    val accentContainer by animateColorAsState(targetValue = target.accentContainer, animationSpec = spec, label = "cyber_accent_container")
    val background by animateColorAsState(targetValue = target.background, animationSpec = spec, label = "cyber_bg")
    val surface by animateColorAsState(targetValue = target.surface, animationSpec = spec, label = "cyber_surface")
    val surfaceElevated by animateColorAsState(targetValue = target.surfaceElevated, animationSpec = spec, label = "cyber_surface_elevated")
    val border by animateColorAsState(targetValue = target.border, animationSpec = spec, label = "cyber_border")
    val glow by animateColorAsState(targetValue = target.glow, animationSpec = spec, label = "cyber_glow")

    val animatedPalette = CyberPalette(
        accent = accent,
        accentContainer = accentContainer,
        background = background,
        surface = surface,
        surfaceElevated = surfaceElevated,
        border = border,
        glow = glow
    )

    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = background,
        primaryContainer = accentContainer,
        onPrimaryContainer = accent,
        secondary = SecurityEmerald,
        onSecondary = DeepEmeraldContainer,
        secondaryContainer = DeepEmeraldContainer,
        onSecondaryContainer = SecurityEmerald,
        tertiary = ComputeAmber,
        onTertiary = DeepAmberContainer,
        tertiaryContainer = DeepAmberContainer,
        onTertiaryContainer = ComputeAmber,
        background = background,
        onBackground = IceWhite,
        surface = surface,
        onSurface = IceWhite,
        surfaceVariant = surfaceElevated,
        onSurfaceVariant = MutedSlate,
        outline = border,
        outlineVariant = border.copy(alpha = 0.6f),
        error = CrimsonAlert,
        onError = DeepCrimsonContainer,
        errorContainer = DeepCrimsonContainer,
        onErrorContainer = CrimsonAlert
    )

    CompositionLocalProvider(LocalCyberPalette provides animatedPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CoreMindTypography,
            shapes = CoreMindShapes,
            content = content
        )
    }
}
