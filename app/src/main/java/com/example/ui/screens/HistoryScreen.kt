package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalCoreStrings
import com.example.domain.AgentWorkflowRun
import com.example.domain.CognitiveSession
import com.example.domain.UiState
import com.example.ui.components.EngineErrorBanner
import com.example.ui.theme.LocalCyberPalette
import com.example.ui.theme.SecurityEmerald
import com.example.ui.viewmodel.AgentsViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.WorkspaceViewModel

@Composable
fun HistoryScreen(
    workspaceViewModel: WorkspaceViewModel,
    agentsViewModel: AgentsViewModel,
    historyViewModel: HistoryViewModel,
    onOpenSessionInWorkspace: (CognitiveSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalCoreStrings.current
    val sessionsState by workspaceViewModel.sessionsState.collectAsStateWithLifecycle()
    val workflowsState by agentsViewModel.workflowsState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var dismissedHistoryError by remember { mutableStateOf<String?>(null) }
    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val workflows = (workflowsState as? UiState.Success)?.data.orEmpty()
    val activeStreamError = if (selectedTab == 0) {
        (sessionsState as? UiState.Error)?.message
    } else {
        (workflowsState as? UiState.Error)?.message
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = strings.historyTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("${strings.historySessionsTab} (${sessions.size})")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("${strings.historyWorkflowsTab} (${workflows.size})")
                    }
                }
            }

            if (activeStreamError != null && dismissedHistoryError != activeStreamError) {
                item {
                    EngineErrorBanner(
                        message = activeStreamError,
                        onDismiss = { dismissedHistoryError = activeStreamError }
                    )
                }
            }

            if (selectedTab == 0) {
                if (sessions.isEmpty()) {
                    item { EmptyHistoryCard(strings.historyEmpty) }
                } else {
                    items(sessions, key = { it.id }) { session ->
                        SessionHistoryCard(
                            session = session,
                            onOpen = { onOpenSessionInWorkspace(session) },
                            onDelete = { historyViewModel.deleteSession(session.id) }
                        )
                    }
                }
            } else {
                if (workflows.isEmpty()) {
                    item { EmptyHistoryCard(strings.historyEmpty) }
                } else {
                    items(workflows, key = { it.id }) { run ->
                        AgentRunCard(
                            run = run,
                            onDelete = { historyViewModel.deleteWorkflow(run.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryCard(text: String) {
    val cyber = LocalCyberPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cyber.surface,
        border = BorderStroke(1.dp, cyber.border)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = cyber.accent)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: CognitiveSession,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalCoreStrings.current
    val cyber = LocalCyberPalette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = cyber.surface,
        border = BorderStroke(1.dp, cyber.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${session.mode} · ${session.reasoningLevel} · ${session.estimatedContextTokens} tok",
                    style = MaterialTheme.typography.labelSmall,
                    color = cyber.accent
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = strings.delete, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (session.contextSummary.isNotBlank()) {
                Text(
                    text = session.contextSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = SecurityEmerald
                )
            }
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.testTag("resume_session_${session.id}"),
                border = BorderStroke(1.dp, cyber.accent)
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = strings.resumeInWorkspace, tint = cyber.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(strings.resumeInWorkspace, color = cyber.accent)
            }
        }
    }
}
