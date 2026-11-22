package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalAppLanguage
import com.example.core.LocalCoreStrings
import com.example.domain.AgentStepRecord
import com.example.domain.AgentWorkflowRun
import com.example.domain.ReasoningLevel
import com.example.domain.SecurityStatus
import com.example.domain.UiState
import com.example.domain.WorkflowPresetType
import com.example.ui.components.EngineErrorBanner
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.DeepEmeraldContainer
import com.example.ui.theme.LocalCyberPalette
import com.example.ui.theme.SecurityEmerald
import com.example.ui.viewmodel.AgentsViewModel

@Composable
fun AgentsScreen(
    viewModel: AgentsViewModel,
    activeReasoningLevel: ReasoningLevel,
    onFallbackToHigh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalCoreStrings.current
    val language = LocalAppLanguage.current
    val cyber = LocalCyberPalette.current

    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val lastAudit by viewModel.lastAudit.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val workflowsState by viewModel.workflowsState.collectAsStateWithLifecycle()

    var goalText by rememberSaveable { mutableStateOf("") }
    var dismissedWorkflowStreamError by remember { mutableStateOf<String?>(null) }
    val workflows = (workflowsState as? UiState.Success)?.data.orEmpty()
    val workflowStreamError = (workflowsState as? UiState.Error)?.message

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.agentsHeaderTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.agentsHeaderSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = DeepEmeraldContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, SecurityEmerald.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = strings.securityShieldTitle,
                            tint = SecurityEmerald,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.securityShieldTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.securityShieldMetric,
                                style = MaterialTheme.typography.labelMedium,
                                color = SecurityEmerald
                            )
                            lastAudit?.let { audit ->
                                Text(
                                    text = audit.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        lastAudit?.let { audit ->
                            SecurityBadge(status = audit.status, riskScore = audit.riskScore)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presetLabels = mapOf(
                        WorkflowPresetType.SCHEDULE to strings.presetSchedule,
                        WorkflowPresetType.SORTING to strings.presetSorting,
                        WorkflowPresetType.AUTOMATION to strings.presetAutomation,
                        WorkflowPresetType.CUSTOM to strings.presetCustom
                    )
                    WorkflowPresetType.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { viewModel.selectPreset(preset) },
                            label = { Text(presetLabels[preset] ?: preset.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = cyber.accent.copy(alpha = 0.2f),
                                selectedLabelColor = cyber.accent
                            )
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cyber.surface,
                    border = BorderStroke(1.dp, cyber.border)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = goalText,
                            onValueChange = { goalText = it },
                            placeholder = { Text(strings.agentGoalPlaceholder) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("agent_goal_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = cyber.accent,
                                unfocusedBorderColor = cyber.border,
                                focusedContainerColor = cyber.background,
                                unfocusedContainerColor = cyber.background
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.executeWorkflow(
                                    rawGoal = goalText,
                                    reasoningLevel = activeReasoningLevel,
                                    languageCode = language.code
                                )
                            },
                            enabled = goalText.isNotBlank() && executionState !is UiState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_agent_workflow_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cyber.accent,
                                contentColor = cyber.background
                            )
                        ) {
                            if (executionState is UiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = cyber.background, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = strings.runAgentWorkflow, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.runAgentWorkflow, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (workflowStreamError != null && dismissedWorkflowStreamError != workflowStreamError) {
                item {
                    EngineErrorBanner(
                        message = workflowStreamError,
                        onDismiss = { dismissedWorkflowStreamError = workflowStreamError }
                    )
                }
            }

            if (executionState is UiState.Error) {
                val err = executionState as UiState.Error
                item {
                    EngineErrorBanner(
                        message = err.message,
                        isXHighBillingIssue = err.isXHighBillingIssue,
                        onFallbackToHigh = {
                            onFallbackToHigh()
                            viewModel.clearStatus()
                        },
                        onDismiss = { viewModel.clearStatus() }
                    )
                }
            }

            if (executionState is UiState.Success) {
                val run = (executionState as UiState.Success<AgentWorkflowRun>).data
                item {
                    AnimatedVisibility(visible = true) {
                        AgentRunCard(run = run, onDelete = null)
                    }
                }
            }

            items(workflows, key = { it.id }) { run ->
                AgentRunCard(
                    run = run,
                    onDelete = { viewModel.deleteWorkflow(run.id) }
                )
            }
        }
    }
}

@Composable
fun AgentRunCard(
    run: AgentWorkflowRun,
    onDelete: (() -> Unit)?
) {
    val strings = LocalCoreStrings.current
    val cyber = LocalCyberPalette.current
    val secStatus = SecurityStatus.fromString(run.securityStatus)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = cyber.surface,
        border = BorderStroke(1.dp, cyber.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Hub, contentDescription = null, tint = cyber.accent, modifier = Modifier.size(18.dp))
                    Text(
                        text = "${run.presetType} · ${run.reasoningLevel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = cyber.accent
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SecurityBadge(status = secStatus, riskScore = run.injectionRiskScore)
                    if (onDelete != null && run.id.isNotBlank()) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = strings.delete, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Text(
                text = run.goal,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            run.steps.forEach { step ->
                AgentStepRow(step = step, scopeLabel = strings.permissionScopeLabel)
            }
        }
    }
}

@Composable
private fun AgentStepRow(step: AgentStepRecord, scopeLabel: String) {
    val cyber = LocalCyberPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = cyber.surfaceElevated,
        border = BorderStroke(1.dp, cyber.border.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${step.stepIndex} · ${step.title}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = step.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = SecurityEmerald
                )
            }
            Text(
                text = "${step.agentRole} · $scopeLabel: ${step.permissionScope}",
                style = MaterialTheme.typography.labelSmall,
                color = cyber.accent
            )
            if (step.output.isNotBlank()) {
                Text(
                    text = step.output,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
