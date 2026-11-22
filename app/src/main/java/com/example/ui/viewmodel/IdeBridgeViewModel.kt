package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CognitiveAiRepository
import com.example.data.FirestoreSyncRepository
import com.example.data.PromptInjectionShield
import com.example.domain.IdeTask
import com.example.domain.IdeTaskStatus
import com.example.domain.IdeTaskType
import com.example.domain.IdeType
import com.example.domain.IdeWorkspace
import com.example.domain.ReasoningLevel
import com.example.domain.RiskLevel
import com.example.domain.SecurityStatus
import com.example.domain.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val TAG = "IdeBridgeViewModel"
private const val STOP_TIMEOUT_MS = 5_000L

class IdeBridgeViewModel(
    private val syncRepository: FirestoreSyncRepository,
    private val aiRepository: CognitiveAiRepository,
    private val currentUserId: String,
    private val databaseId: String,
    private val projectId: String
) : ViewModel() {

    private val _selectedWorkspaceId = MutableStateFlow<String?>(null)
    val selectedWorkspaceId: StateFlow<String?> = _selectedWorkspaceId.asStateFlow()

    private val _actionState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val actionState: StateFlow<UiState<String>> = _actionState.asStateFlow()

    val workspacesState: StateFlow<UiState<List<IdeWorkspace>>> =
        syncRepository.observeIdeWorkspaces(currentUserId)
            .map<List<IdeWorkspace>, UiState<List<IdeWorkspace>>> { list ->
                if (_selectedWorkspaceId.value == null && list.isNotEmpty()) {
                    _selectedWorkspaceId.value = list.first().id
                }
                UiState.Success(list)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing IDE workspaces", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load IDE workspaces"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val tasksState: StateFlow<UiState<List<IdeTask>>> =
        syncRepository.observeIdeTasks(currentUserId)
            .map<List<IdeTask>, UiState<List<IdeTask>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing IDE tasks", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load IDE task queue"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    fun selectWorkspace(workspaceId: String) {
        _selectedWorkspaceId.value = workspaceId
    }

    fun clearActionState() {
        _actionState.value = UiState.Idle
    }

    fun pairIdeWorkspace(
        ideType: IdeType,
        projectName: String,
        branchName: String
    ) {
        if (projectName.isBlank()) return
        val code = "CM-${Random.nextInt(1000, 9999)}"
        viewModelScope.launch {
            _actionState.value = UiState.Loading
            val res = syncRepository.createIdeWorkspace(
                ideType = ideType,
                projectName = projectName,
                branchName = branchName.ifBlank { "main" },
                pairingCode = code
            )
            res.onSuccess { newId ->
                _selectedWorkspaceId.value = newId
                _actionState.value = UiState.Success(newId)
            }.onFailure { err ->
                _actionState.value = UiState.Error(err.localizedMessage ?: "Failed to pair IDE workspace")
            }
        }
    }

    fun removeWorkspace(workspaceId: String) {
        viewModelScope.launch {
            syncRepository.deleteIdeWorkspace(workspaceId).onSuccess {
                if (_selectedWorkspaceId.value == workspaceId) {
                    _selectedWorkspaceId.value = null
                }
            }.onFailure { err ->
                _actionState.value = UiState.Error(err.localizedMessage ?: "Failed to delete workspace")
            }
        }
    }

    fun submitDiagnosticAndGeneratePatch(
        workspace: IdeWorkspace,
        taskType: IdeTaskType,
        title: String,
        filePath: String,
        language: String,
        codeSnippet: String,
        stackTrace: String,
        reasoningLevel: ReasoningLevel,
        languageCode: String
    ) {
        val isArabic = languageCode == "ar"
        val audit = PromptInjectionShield.audit("$title\n$codeSnippet\n$stackTrace", isArabic)
        if (audit.status == SecurityStatus.BLOCKED) {
            _actionState.value = UiState.Error(audit.notes)
            return
        }

        val ideType = IdeType.fromString(workspace.ideType)
        viewModelScope.launch {
            _actionState.value = UiState.Loading
            try {
                val analysis = aiRepository.analyzeIdeTaskAndGeneratePatch(
                    ideType = ideType,
                    taskType = taskType,
                    filePath = filePath.ifBlank { "src/Main.kt" },
                    language = language.ifBlank { "kotlin" },
                    codeSnippet = codeSnippet,
                    stackTrace = stackTrace,
                    reasoningLevel = reasoningLevel,
                    languageCode = languageCode
                )

                val createRes = syncRepository.createIdeTask(
                    workspaceId = workspace.id,
                    ideType = ideType,
                    taskType = taskType,
                    title = title.ifBlank { "IDE Patch: ${filePath.ifBlank { "buffer" }}" },
                    filePath = filePath.ifBlank { "src/Main.kt" },
                    language = language.ifBlank { "kotlin" },
                    codeSnippet = codeSnippet,
                    stackTrace = stackTrace,
                    diagnosis = analysis.diagnosis,
                    unifiedDiff = analysis.unifiedDiff,
                    riskLevel = analysis.riskLevel,
                    reasoningLevel = reasoningLevel,
                    securityStatus = audit.status,
                    status = IdeTaskStatus.PATCH_READY
                )

                createRes.onSuccess { id ->
                    _actionState.value = UiState.Success(id)
                }.onFailure { err ->
                    _actionState.value = UiState.Error(err.localizedMessage ?: "Failed to save IDE patch")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "IDE analysis timed out", e)
                _actionState.value = UiState.Error("IDE diagnostic analysis timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "IDE analysis failed", e)
                val msg = e.localizedMessage ?: "Failed to analyze IDE diagnostic."
                _actionState.value = UiState.Error(
                    message = msg,
                    isXHighBillingIssue = aiRepository.isXHighBillingError(e, reasoningLevel)
                )
            }
        }
    }

    fun updatePatchStatus(taskId: String, newStatus: IdeTaskStatus) {
        viewModelScope.launch {
            syncRepository.updateIdeTaskStatus(taskId, newStatus).onFailure { err ->
                _actionState.value = UiState.Error(err.localizedMessage ?: "Failed to update patch status")
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            syncRepository.deleteIdeTask(taskId).onFailure { err ->
                _actionState.value = UiState.Error(err.localizedMessage ?: "Failed to delete IDE task")
            }
        }
    }

    fun buildBridgeConfigJson(workspace: IdeWorkspace): String {
        val ide = IdeType.fromString(workspace.ideType)
        return """
            {
              "bridge": "coremind-firestore-v1",
              "ide": "${ide.name}",
              "extensionId": "${ide.extensionId}",
              "workspaceId": "${workspace.id}",
              "pairingCode": "${workspace.pairingCode}",
              "project": "${workspace.projectName}",
              "branch": "${workspace.branchName}",
              "firebase": {
                "projectId": "$projectId",
                "databaseId": "$databaseId",
                "userId": "$currentUserId",
                "tasksCollection": "users/$currentUserId/ideTasks",
                "workspacesCollection": "users/$currentUserId/ideWorkspaces"
              },
              "capabilities": ["diagnostics_sync", "unified_diff_queue", "agent_handoff"]
            }
        """.trimIndent()
    }
}
