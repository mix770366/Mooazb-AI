package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.core.LocalCoreStrings
import com.example.domain.ReasoningLevel
import com.example.domain.SecurityStatus
import com.example.ui.theme.ComputeAmber
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSlate
import com.example.ui.theme.CyberSlateElevated
import com.example.ui.theme.DeepAmberContainer
import com.example.ui.theme.DeepCrimsonContainer
import com.example.ui.theme.DeepEmeraldContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCyberPalette
import com.example.ui.theme.SecurityEmerald
import com.example.ui.theme.VsCodeBlue
import java.text.NumberFormat
import java.util.Locale

fun reasoningAccentColor(level: ReasoningLevel): Color = when (level) {
    ReasoningLevel.LOW -> SecurityEmerald
    ReasoningLevel.MEDIUM -> VsCodeBlue
    ReasoningLevel.HIGH -> ElectricCyan
    ReasoningLevel.XHIGH -> ComputeAmber
}

@Composable
fun ReasoningTierPill(
    level: ReasoningLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberPalette.current
    val targetAccent = reasoningAccentColor(level)
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(durationMillis = 360),
        label = "pill_accent"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("reasoning_tier_pill"),
        shape = RoundedCornerShape(50),
        color = cyber.surfaceElevated,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = when (level) {
                    ReasoningLevel.LOW -> Icons.Outlined.Bolt
                    ReasoningLevel.MEDIUM -> Icons.Outlined.Speed
                    ReasoningLevel.HIGH -> Icons.Outlined.Psychology
                    ReasoningLevel.XHIGH -> Icons.Outlined.Memory
                },
                contentDescription = level.codeTag,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = level.codeTag,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ReasoningTierSegmentedControl(
    selectedLevel: ReasoningLevel,
    onSelect: (ReasoningLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberPalette.current
    val tiers = ReasoningLevel.entries
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reasoning_segmented_control")
    ) {
        tiers.forEachIndexed { index, level ->
            val selected = level == selectedLevel
            val tierAccent = reasoningAccentColor(level)
            val containerColor by animateColorAsState(
                targetValue = if (selected) tierAccent.copy(alpha = 0.22f) else cyber.surface,
                animationSpec = tween(durationMillis = 350),
                label = "segment_bg_${level.name}"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) tierAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(durationMillis = 350),
                label = "segment_fg_${level.name}"
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) tierAccent.copy(alpha = 0.78f) else cyber.border,
                animationSpec = tween(durationMillis = 350),
                label = "segment_border_${level.name}"
            )
            val label = when (level) {
                ReasoningLevel.LOW -> "Low"
                ReasoningLevel.MEDIUM -> "Medium"
                ReasoningLevel.HIGH -> "High"
                ReasoningLevel.XHIGH -> "xhigh"
            }
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(level) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tiers.size),
                icon = {},
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reasoning_segment_${level.name.lowercase()}"),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = containerColor,
                    activeContentColor = contentColor,
                    activeBorderColor = borderColor,
                    inactiveContainerColor = containerColor,
                    inactiveContentColor = contentColor,
                    inactiveBorderColor = borderColor
                )
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasoningLevelSheet(
    selectedLevel: ReasoningLevel,
    onSelect: (ReasoningLevel) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalCoreStrings.current
    val cyber = LocalCyberPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cyber.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = strings.reasoningLevelLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            ReasoningLevel.entries.forEach { level ->
                val isSelected = level == selectedLevel
                val accent = reasoningAccentColor(level)
                val (title, desc) = when (level) {
                    ReasoningLevel.LOW -> strings.reasoningLowTitle to strings.reasoningLowDesc
                    ReasoningLevel.MEDIUM -> strings.reasoningMedTitle to strings.reasoningMedDesc
                    ReasoningLevel.HIGH -> strings.reasoningHighTitle to strings.reasoningHighDesc
                    ReasoningLevel.XHIGH -> strings.reasoningXHighTitle to strings.reasoningXHighDesc
                }
                Surface(
                    onClick = {
                        onSelect(level)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reasoning_option_${level.name.lowercase()}"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) accent.copy(alpha = 0.14f) else cyber.surfaceElevated,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) accent else cyber.border
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = level.modelName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = level.targetLatencyLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TokenCapacityGauge(
    usedTokens: Long,
    modifier: Modifier = Modifier
) {
    val strings = LocalCoreStrings.current
    val cyber = LocalCyberPalette.current
    val progress = (usedTokens.toFloat() / 1_000_000f).coerceIn(0.01f, 1f)
    val formatted = NumberFormat.getNumberInstance(Locale.US).format(usedTokens)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cyber.surface,
        border = BorderStroke(1.dp, cyber.border)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.contextWindowTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = DeepEmeraldContainer
                    ) {
                        Text(
                            text = strings.implicitCacheBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = SecurityEmerald,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "$formatted / 1,000,000",
                        style = MaterialTheme.typography.labelSmall,
                        color = cyber.accent
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                color = if (progress > 0.85f) ComputeAmber else cyber.accent,
                trackColor = cyber.surfaceElevated
            )
        }
    }
}

@Composable
fun CodeBlockCard(
    title: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalCoreStrings.current
    val cyber = LocalCyberPalette.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cyber.background,
        border = BorderStroke(1.dp, cyber.border)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cyber.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = cyber.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        copyToClipboard(context, title, code, strings.copiedToast)
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = strings.copyCode,
                        tint = cyber.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = strings.copyCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = cyber.accent
                    )
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = code,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun UnifiedDiffViewer(
    unifiedDiff: String,
    modifier: Modifier = Modifier
) {
    val cyber = LocalCyberPalette.current
    val lines = unifiedDiff.lines()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = cyber.background,
            border = BorderStroke(1.dp, cyber.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                lines.forEach { line ->
                    val bgColor = when {
                        line.startsWith("+") && !line.startsWith("+++") -> SecurityEmerald.copy(alpha = 0.14f)
                        line.startsWith("-") && !line.startsWith("---") -> CrimsonAlert.copy(alpha = 0.14f)
                        line.startsWith("@@") -> cyber.accent.copy(alpha = 0.10f)
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        line.startsWith("+") && !line.startsWith("+++") -> SecurityEmerald
                        line.startsWith("-") && !line.startsWith("---") -> CrimsonAlert
                        line.startsWith("@@") || line.startsWith("+++") || line.startsWith("---") -> cyber.accent
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                    }
                    Text(
                        text = line.ifEmpty { " " },
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SecurityBadge(status: SecurityStatus, riskScore: Long? = null) {
    val (bg, fg, icon) = when (status) {
        SecurityStatus.SAFE -> Triple(DeepEmeraldContainer, SecurityEmerald, Icons.Outlined.CheckCircle)
        SecurityStatus.SANITIZED -> Triple(DeepAmberContainer, ComputeAmber, Icons.Outlined.Security)
        SecurityStatus.BLOCKED -> Triple(DeepCrimsonContainer, CrimsonAlert, Icons.Outlined.ErrorOutline)
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = status.name, tint = fg, modifier = Modifier.size(14.dp))
            Text(
                text = if (riskScore != null) "${status.name} · $riskScore%" else status.name,
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

@Composable
fun EngineErrorBanner(
    message: String,
    isXHighBillingIssue: Boolean = false,
    onFallbackToHigh: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalCoreStrings.current
    AnimatedVisibility(visible = message.isNotBlank()) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = DeepCrimsonContainer,
            border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = strings.errorBannerPrefix,
                        tint = CrimsonAlert,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${strings.errorBannerPrefix} $message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isXHighBillingIssue) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = strings.xhighBillingHint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = ComputeAmber
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isXHighBillingIssue && onFallbackToHigh != null) {
                        OutlinedButton(
                            onClick = onFallbackToHigh,
                            border = BorderStroke(1.dp, ElectricCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            Text(strings.fallbackToHigh)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(strings.close, color = CrimsonAlert)
                    }
                }
            }
        }
    }
}

fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
