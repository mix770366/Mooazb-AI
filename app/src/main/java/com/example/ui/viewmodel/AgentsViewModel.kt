package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CognitiveAiRepository
import com.example.data.FirestoreSyncRepository
import com.example.data.InjectionAuditResult
import com.example.data.PromptInjectionShield
import com.example.domain.AgentWorkflowRun
import com.example.domain.ReasoningLevel
import com.example.domain.SecurityStatus
import com.example.domain.UiState
import com.example.domain.WorkflowPresetType
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

private const val TAG = "AgentsViewModel"
private const val STOP_TIMEOUT_MS = 5_000L

class AgentsViewModel(
    private val syncRepository: FirestoreSyncRepository,
    private val aiRepository: CognitiveAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedPreset = MutableStateFlow(WorkflowPresetType.AUTOMATION)
    val selectedPreset: StateFlow<WorkflowPresetType> = _selectedPreset.asStateFlow()

    private val _lastAudit = MutableStateFlow<InjectionAuditResult?>(null)
    val lastAudit: StateFlow<InjectionAuditResult?> = _lastAudit.asStateFlow()

    private val _executionState = MutableStateFlow<UiState<AgentWorkflowRun>>(UiState.Idle)
    val executionState: StateFlow<UiState<AgentWorkflowRun>> = _executionState.asStateFlow()

    val workflowsState: StateFlow<UiState<List<AgentWorkflowRun>>> =
        syncRepository.observeWorkflows(currentUserId)
            .map<List<AgentWorkflowRun>, UiState<List<AgentWorkflowRun>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing workflows", root)
                emit(UiState.Error(root.localizedMessage ?: "Failed to load agentic workflows"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    fun selectPreset(preset: WorkflowPresetType) {
        _selectedPreset.value = preset
    }

    fun clearStatus() {
        _executionState.value = UiState.Idle
    }

    fun executeWorkflow(
        rawGoal: String,
        reasoningLevel: ReasoningLevel,
        languageCode: String
    ) {
        val trimmed = rawGoal.trim()
        if (trimmed.isEmpty()) return

        val isArabic = languageCode == "ar"
        val audit = PromptInjectionShield.audit(trimmed, isArabic)
        _lastAudit.value = audit

        if (audit.status == SecurityStatus.BLOCKED) {
            viewModelScope.launch {
                syncRepository.saveWorkflowRun(
                    goal = trimmed.take(500),
                    presetType = _selectedPreset.value,
                    reasoningLevel = reasoningLevel,
                    securityStatus = SecurityStatus.BLOCKED,
                    injectionRiskScore = audit.riskScore,
                    securityNotes = audit.notes,
                    steps = emptyList()
                )
            }
            _executionState.value = UiState.Error(audit.notes)
            return
        }

        viewModelScope.launch {
            _executionState.value = UiState.Loading
            try {
                val steps = aiRepository.planAgenticWorkflow(
                    sanitizedGoal = audit.sanitizedInput,
                    presetType = _selectedPreset.value,
                    reasoningLevel = reasoningLevel,
                    languageCode = languageCode
                )

                val run = AgentWorkflowRun(
                    userId = currentUserId,
                    goal = trimmed,
                    presetType = _selectedPreset.value.name,
                    reasoningLevel = reasoningLevel.name,
                    securityStatus = audit.status.name,
                    injectionRiskScore = audit.riskScore,
                    securityNotes = audit.notes,
                    steps = steps
                )

                syncRepository.saveWorkflowRun(
                    goal = run.goal,
                    presetType = _selectedPreset.value,
                    reasoningLevel = reasoningLevel,
                    securityStatus = audit.status,
                    injectionRiskScore = audit.riskScore,
                    securityNotes = audit.notes,
                    steps = steps
                ).onFailure { err ->
                    Log.w(TAG, "Failed to persist workflow run", err)
                }

                _executionState.value = UiState.Success(run)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Workflow planning timed out", e)
                _executionState.value = UiState.Error("Agentic planning timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Workflow planning failed", e)
                val msg = e.localizedMessage ?: "Agentic workflow planning failed."
                _executionState.value = UiState.Error(
                    message = msg,
                    isXHighBillingIssue = aiRepository.isXHighBillingError(e, reasoningLevel)
                )
            }
        }
    }

    fun deleteWorkflow(workflowId: String) {
        viewModelScope.launch {
            syncRepository.deleteWorkflowRun(workflowId)
        }
    }
}
