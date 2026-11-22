package com.example.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalAppLanguage
import com.example.core.LocalCoreStrings
import com.example.domain.IdeTask
import com.example.domain.IdeTaskStatus
import com.example.domain.IdeTaskType
import com.example.domain.IdeType
import com.example.domain.IdeWorkspace
import com.example.domain.ReasoningLevel
import com.example.domain.RiskLevel
import com.example.domain.SecurityStatus
import com.example.domain.UiState
import com.example.ui.components.CodeBlockCard
import com.example.ui.components.EngineErrorBanner
import com.example.ui.components.SecurityBadge
import com.example.ui.components.UnifiedDiffViewer
import com.example.ui.components.copyToClipboard
import com.example.ui.theme.ComputeAmber
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepEmeraldContainer
import com.example.ui.theme.IntelliJViolet
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCyberPalette
import com.example.ui.theme.PyCharmEmerald
import com.example.ui.theme.SecurityEmerald
import com.example.ui.theme.VsCodeBlue
import com.example.ui.viewmodel.IdeBridgeViewModel

private fun ideAccentColor(ideType: IdeType): Color = when (ideType) {
    IdeType.VS_CODE -> VsCodeBlue
    IdeType.PYCHARM -> PyCharmEmerald
    IdeType.INTELLIJ -> IntelliJViolet
}

@Composable
fun IdeBridgeScreen(
    viewModel: IdeBridgeViewModel,
    activeReasoningLevel: ReasoningLevel,
    onFallbackToHigh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalCoreStrings.current
    val language = LocalAppLanguage.current
    val cyber = LocalCyberPalette.current

    val workspacesState by viewModel.workspacesState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val selectedWorkspaceId by viewModel.selectedWorkspaceId.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    var showPairDialog by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showNewTaskDialog by remember { mutableStateOf(false) }
    var statusFilter by rememberSaveable { mutableStateOf<IdeTaskStatus?>(null) }
    var dismissedStreamError by remember { mutableStateOf<String?>(null) }

    val workspaces = (workspacesState as? UiState.Success)?.data.orEmpty()
    val activeWorkspace = workspaces.firstOrNull { it.id == selectedWorkspaceId } ?: workspaces.firstOrNull()
    val tasks = (tasksState as? UiState.Success)?.data.orEmpty()
    val filteredTasks = remember(tasks, statusFilter) {
        if (statusFilter == null) tasks else tasks.filter { IdeTaskStatus.fromString(it.status) == statusFilter }
    }
    val streamErrorMessage = (workspacesState as? UiState.Error)?.message
        ?: (tasksState as? UiState.Error)?.message

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
                        text = strings.ideHeaderTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.ideHeaderSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showPairDialog = true },
                        modifier = Modifier.testTag("pair_ide_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = cyber.accent,
                            contentColor = cyber.background
                        )
                    ) {
                        Icon(Icons.Outlined.AddLink, contentDescription = strings.connectIdeButton, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.connectIdeButton, fontWeight = FontWeight.Bold)
                    }
                    if (activeWorkspace != null) {
                        OutlinedButton(
                            onClick = { showConfigDialog = true },
                            modifier = Modifier.testTag("bridge_config_button"),
                            border = BorderStroke(1.dp, cyber.accent)
                        ) {
                            Icon(Icons.Outlined.SettingsEthernet, contentDescription = strings.bridgeConfigButton, tint = cyber.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.bridgeConfigButton, color = cyber.accent)
                        }
                        OutlinedButton(
                            onClick = { showNewTaskDialog = true },
                            modifier = Modifier.testTag("new_ide_diagnostic_button"),
                            border = BorderStroke(1.dp, SecurityEmerald)
                        ) {
                            Icon(Icons.Outlined.BugReport, contentDescription = strings.newDiagnosticButton, tint = SecurityEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.newDiagnosticButton, color = SecurityEmerald)
                        }
                    }
                }
            }

            if (actionState is UiState.Loading) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cyber.surface,
                        border = BorderStroke(1.dp, cyber.accent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = cyber.accent, strokeWidth = 2.dp)
                            Text(strings.streamingLabel, color = cyber.accent, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            if (streamErrorMessage != null && dismissedStreamError != streamErrorMessage) {
                item {
                    EngineErrorBanner(
                        message = streamErrorMessage,
                        onDismiss = { dismissedStreamError = streamErrorMessage }
                    )
                }
            }

            if (actionState is UiState.Error) {
                val err = actionState as UiState.Error
                item {
                    EngineErrorBanner(
                        message = err.message,
                        isXHighBillingIssue = err.isXHighBillingIssue,
                        onFallbackToHigh = {
                            onFallbackToHigh()
                            viewModel.clearActionState()
                        },
                        onDismiss = { viewModel.clearActionState() }
                    )
                }
            }

            item {
                Text(
                    text = strings.pairedWorkspacesTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (workspaces.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cyber.surface,
                        border = BorderStroke(1.dp, cyber.border)
                    ) {
                        Text(
                            text = strings.noIdeWorkspacesEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(workspaces, key = { it.id }) { ws ->
                            IdeWorkspaceCard(
                                workspace = ws,
                                isSelected = ws.id == activeWorkspace?.id,
                                onSelect = { viewModel.selectWorkspace(ws.id) },
                                onDelete = { viewModel.removeWorkspace(ws.id) }
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.ideQueueTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { statusFilter = null },
                            label = { Text("ALL (${tasks.size})", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("ide_filter_all")
                        )
                        listOf(
                            IdeTaskStatus.PATCH_READY,
                            IdeTaskStatus.APPROVED,
                            IdeTaskStatus.APPLIED
                        ).forEach { st ->
                            val count = tasks.count { IdeTaskStatus.fromString(it.status) == st }
                            FilterChip(
                                selected = statusFilter == st,
                                onClick = { statusFilter = if (statusFilter == st) null else st },
                                label = { Text("${st.name} ($count)", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("ide_filter_${st.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cyber.surface,
                        border = BorderStroke(1.dp, cyber.border)
                    ) {
                        Text(
                            text = strings.noIdeTasksEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    IdeTaskCard(
                        task = task,
                        onApprove = { viewModel.updatePatchStatus(task.id, IdeTaskStatus.APPROVED) },
                        onReject = { viewModel.updatePatchStatus(task.id, IdeTaskStatus.REJECTED) },
                        onMarkApplied = { viewModel.updatePatchStatus(task.id, IdeTaskStatus.APPLIED) },
                        onDelete = { viewModel.deleteTask(task.id) }
                    )
                }
            }
        }
    }

    if (showPairDialog) {
        PairIdeDialog(
            onDismiss = { showPairDialog = false },
            onConfirm = { ideType, project, branch ->
                viewModel.pairIdeWorkspace(ideType, project, branch)
                showPairDialog = false
            }
        )
    }

    if (showConfigDialog && activeWorkspace != null) {
        val configJson = remember(activeWorkspace.id) {
            viewModel.buildBridgeConfigJson(activeWorkspace)
        }
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            containerColor = cyber.surface,
            title = {
                Text(
                    text = "${IdeType.fromString(activeWorkspace.ideType).displayName} · ${activeWorkspace.projectName}",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CodeBlockCard(title = "coremind-bridge.json", code = configJson)
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfigDialog = false }) {
                    Text(strings.close, color = cyber.accent)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { copyToClipboard(context, "coremind-bridge.json", configJson, strings.copiedToast) },
                    border = BorderStroke(1.dp, cyber.accent)
                ) {
                    Text(strings.copyCode, color = cyber.accent)
                }
            }
        )
    }

    if (showNewTaskDialog && activeWorkspace != null) {
        NewIdeDiagnosticDialog(
            workspace = activeWorkspace,
            onDismiss = { showNewTaskDialog = false },
            onSubmit = { taskType, title, filePath, lang, code, trace ->
                viewModel.submitDiagnosticAndGeneratePatch(
                    workspace = activeWorkspace,
                    taskType = taskType,
                    title = title,
                    filePath = filePath,
                    language = lang,
                    codeSnippet = code,
                    stackTrace = trace,
                    reasoningLevel = activeReasoningLevel,
                    languageCode = language.code
                )
                showNewTaskDialog = false
            }
        )
    }
}

@Composable
private fun IdeWorkspaceCard(
    workspace: IdeWorkspace,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val cyber = LocalCyberPalette.current
    val ide = IdeType.fromString(workspace.ideType)
    val accent = ideAccentColor(ide)

    Surface(
        onClick = onSelect,
        modifier = Modifier
            .width(230.dp)
            .testTag("ide_workspace_card_${workspace.id}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) accent.copy(alpha = 0.14f) else cyber.surface,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else cyber.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = ide.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = workspace.projectName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${workspace.branchName} · ${workspace.pairingCode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = workspace.bridgeStatus,
                style = MaterialTheme.typography.labelSmall,
                color = SecurityEmerald
            )
        }
    }
}

@Composable
private fun IdeTaskCard(
    task: IdeTask,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onMarkApplied: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalCoreStrings.current
    val cyber = LocalCyberPalette.current
    val ide = IdeType.fromString(task.ideType)
    val accent = ideAccentColor(ide)
    val status = IdeTaskStatus.fromString(task.status)
    val risk = RiskLevel.fromString(task.riskLevel)
    val secStatus = SecurityStatus.fromString(task.securityStatus)

    val statusColor = when (status) {
        IdeTaskStatus.APPROVED, IdeTaskStatus.APPLIED -> SecurityEmerald
        IdeTaskStatus.REJECTED -> CrimsonAlert
        IdeTaskStatus.PATCH_READY -> cyber.accent
        IdeTaskStatus.PENDING_ANALYSIS -> ComputeAmber
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ide_task_card_${task.id}"),
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ide.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        text = "· ${status.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SecurityBadge(status = secStatus)
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = strings.delete, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (task.filePath.isNotBlank()) {
                Text(
                    text = "${task.filePath} (${task.language}) · RISK: ${risk.name} · ${task.reasoningLevel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (task.diagnosis.isNotBlank()) {
                Text(
                    text = task.diagnosis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (task.unifiedDiff.isNotBlank()) {
                UnifiedDiffViewer(unifiedDiff = task.unifiedDiff)
            }

            when (status) {
                IdeTaskStatus.PENDING_ANALYSIS, IdeTaskStatus.PATCH_READY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onApprove,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("approve_patch_${task.id}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepEmeraldContainer,
                                contentColor = SecurityEmerald
                            )
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = strings.approvePatch, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.approvePatch)
                        }
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.testTag("reject_patch_${task.id}"),
                            border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f))
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = strings.rejectPatch, tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.rejectPatch, color = CrimsonAlert)
                        }
                    }
                }
                IdeTaskStatus.APPROVED -> {
                    OutlinedButton(
                        onClick = onMarkApplied,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mark_applied_${task.id}"),
                        border = BorderStroke(1.dp, SecurityEmerald)
                    ) {
                        Icon(Icons.Outlined.DoneAll, contentDescription = strings.markApplied, tint = SecurityEmerald, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.markApplied, color = SecurityEmerald)
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun PairIdeDialog(
    onDismiss: () -> Unit,
    onConfirm: (IdeType, String, String) -> Unit
) {
    val strings = LocalCoreStrings.current
    val cyber = LocalCyberPalette.current
    var selectedIde by rememberSaveable { mutableStateOf(IdeType.VS_CODE) }
    var projectName by rememberSaveable { mutableStateOf("coremind-service") }
    var branchName by rememberSaveable { mutableStateOf("main") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cyber.surface,
        title = { Text(strings.connectIdeButton, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IdeType.entries.forEach { ide ->
                        val accent = ideAccentColor(ide)
                        FilterChip(
                            selected = selectedIde == ide,
                            onClick = { selectedIde = ide },
                            label = { Text(ide.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.2f),
                                selectedLabelColor = accent
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text(strings.projectNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = branchName,
                    onValueChange = { branchName = it },
                    label = { Text(strings.branchNameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedIde, projectName, branchName) },
                enabled = projectName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = cyber.accent, contentColor = cyber.background)
            ) {
                Text(strings.create, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
private fun NewIdeDiagnosticDialog(
    workspace: IdeWorkspace,
    onDismiss: () -> Unit,
    onSubmit: (IdeTaskType, String, String, String, String, String) -> Unit
) {
    val strings = LocalCoreStrings.current
    val cyber = LocalCyberPalette.current
    val ideType = IdeType.fromString(workspace.ideType)
    val defaultTemplate = remember(workspace.id, ideType) {
        when (ideType) {
            IdeType.VS_CODE -> Triple(
                "src/auth/tokenParser.ts" to "typescript",
                "export function parseToken(raw?: string): string {\n  return raw!.trim().toUpperCase();\n}",
                "TypeError: Cannot read properties of undefined (reading 'trim') at tokenParser.ts:2"
            )
            IdeType.PYCHARM -> Triple(
                "app/services/session_handler.py" to "python",
                "def parse_token(raw: str | None) -> str:\n    return raw.strip().upper()",
                "AttributeError: 'NoneType' object has no attribute 'strip' in session_handler.py:2"
            )
            IdeType.INTELLIJ -> Triple(
                "src/main/kotlin/SessionHandler.kt" to "kotlin",
                "fun processToken(raw: String?): String {\n    val clean = raw!!.trim()\n    return clean.uppercase()\n}",
                "java.lang.NullPointerException at SessionHandler.kt:2"
            )
        }
    }

    var taskType by rememberSaveable(workspace.id) { mutableStateOf(IdeTaskType.PATCH_REVIEW) }
    var title by rememberSaveable(workspace.id) { mutableStateOf("Fix nullability & concurrency guard") }
    var filePath by rememberSaveable(workspace.id) { mutableStateOf(defaultTemplate.first.first) }
    var language by rememberSaveable(workspace.id) { mutableStateOf(defaultTemplate.first.second) }
    var codeSnippet by rememberSaveable(workspace.id) { mutableStateOf(defaultTemplate.second) }
    var stackTrace by rememberSaveable(workspace.id) { mutableStateOf(defaultTemplate.third) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cyber.surface,
        title = {
            Text(
                text = "${IdeType.fromString(workspace.ideType).displayName} · ${workspace.projectName}",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IdeTaskType.entries.forEach { t ->
                        FilterChip(
                            selected = taskType == t,
                            onClick = { taskType = t },
                            label = { Text(t.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(strings.taskTitleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = filePath,
                        onValueChange = { filePath = it },
                        label = { Text(strings.filePathLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1.6f)
                    )
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text(strings.languageLabel) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = codeSnippet,
                        onValueChange = { codeSnippet = it },
                        label = { Text(strings.codeInputLabel) },
                        minLines = 4,
                        maxLines = 7,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = stackTrace,
                        onValueChange = { stackTrace = it },
                        label = { Text(strings.stackTraceLabel) },
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(taskType, title, filePath, language, codeSnippet, stackTrace) },
                enabled = title.isNotBlank() && codeSnippet.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = cyber.accent, contentColor = cyber.background)
            ) {
                Text(strings.analyzeAndPatch, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
