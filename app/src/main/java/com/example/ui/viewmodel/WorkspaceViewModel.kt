package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CognitiveAiRepository
import com.example.data.FirestoreSyncRepository
import com.example.data.PromptInjectionShield
import com.example.domain.CognitiveSession
import com.example.domain.ContextAttachment
import com.example.domain.MessageRole
import com.example.domain.ReasoningLevel
import com.example.domain.SecurityStatus
import com.example.domain.SessionMessage
import com.example.domain.SessionMode
import com.example.domain.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "WorkspaceViewModel"
private const val STOP_TIMEOUT_MS = 5_000L

class WorkspaceViewModel(
    private val syncRepository: FirestoreSyncRepository,
    private val aiRepository: CognitiveAiRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _reasoningLevel = MutableStateFlow(ReasoningLevel.HIGH)
    val reasoningLevel: StateFlow<ReasoningLevel> = _reasoningLevel.asStateFlow()

    @Volatile
    private var localTierDirty = false

    init {
        viewModelScope.launch {
            syncRepository.observeUserProfile(currentUserId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing user profile reasoning tier", firestoreError ?: error)
                }
                .collect { profile ->
                    if (profile != null && !localTierDirty) {
                        _reasoningLevel.value = ReasoningLevel.fromString(profile.preferredReasoningLevel)
                    }
                }
        }
    }

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _attachments = MutableStateFlow<List<ContextAttachment>>(emptyList())
    val attachments: StateFlow<List<ContextAttachment>> = _attachments.asStateFlow()

    private val _generationState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val generationState: StateFlow<UiState<String>> = _generationState.asStateFlow()

    private val _codeMathState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val codeMathState: StateFlow<UiState<String>> = _codeMathState.asStateFlow()

    private val _securityNotice = MutableStateFlow<String?>(null)
    val securityNotice: StateFlow<String?> = _securityNotice.asStateFlow()

    val sessionsState: StateFlow<UiState<List<CognitiveSession>>> =
        syncRepository.observeSessions(currentUserId)
            .map<List<CognitiveSession>, UiState<List<CognitiveSession>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing sessions", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to observe sessions"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMessages: StateFlow<List<SessionMessage>> =
        _activeSessionId.flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                syncRepository.observeMessages(currentUserId, sessionId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing messages", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    fun setReasoningLevel(level: ReasoningLevel) {
        if (_reasoningLevel.value == level && localTierDirty) return
        localTierDirty = true
        _reasoningLevel.value = level
        viewModelScope.launch {
            syncRepository.saveUserReasoningLevel(level)
                .onSuccess {
                    localTierDirty = false
                }
                .onFailure { err ->
                    localTierDirty = false
                    Log.w(TAG, "Failed to persist reasoning tier to Firestore profile", err)
                }
        }
    }

    fun selectSession(session: CognitiveSession) {
        _activeSessionId.value = session.id
        _reasoningLevel.value = ReasoningLevel.fromString(session.reasoningLevel)
        _generationState.value = UiState.Idle
    }

    fun startNewSession() {
        _activeSessionId.value = null
        _attachments.value = emptyList()
        _generationState.value = UiState.Idle
        _securityNotice.value = null
    }

    fun addAttachment(attachment: ContextAttachment) {
        val current = _attachments.value
        if (current.size >= 8) return
        _attachments.value = current + attachment
    }

    fun removeAttachment(attachmentId: String) {
        _attachments.value = _attachments.value.filterNot { it.id == attachmentId }
    }

    fun clearError() {
        _generationState.value = UiState.Idle
        _codeMathState.value = UiState.Idle
        _securityNotice.value = null
    }

    fun estimatedContextTokens(extraPrompt: String = ""): Long {
        val attachmentTokens = _attachments.value.sumOf { it.estimatedTokens }
        val messageTokens = activeMessages.value.sumOf { it.tokenEstimate }
        val promptTokens = (extraPrompt.length / 4L).coerceAtLeast(if (extraPrompt.isNotBlank()) 1L else 0L)
        return (attachmentTokens + messageTokens + promptTokens).coerceIn(0L, 1_000_000L)
    }

    fun sendWorkspacePrompt(rawPrompt: String, languageCode: String) {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty() && _attachments.value.isEmpty()) return

        val isArabic = languageCode == "ar"
        val combinedTextForAudit = buildString {
            append(trimmed)
            _attachments.value.forEach { att ->
                if (!att.textContent.isNullOrBlank()) {
                    append("\n").append(att.textContent.take(4000))
                }
            }
        }
        val audit = PromptInjectionShield.audit(combinedTextForAudit, isArabic)
        if (audit.status == SecurityStatus.BLOCKED) {
            _securityNotice.value = null
            _generationState.value = UiState.Error(audit.notes)
            return
        } else if (audit.status == SecurityStatus.SANITIZED) {
            _securityNotice.value = audit.notes
        } else {
            _securityNotice.value = null
        }

        val currentAttachments = _attachments.value
        val activeLevel = _reasoningLevel.value
        val effectivePrompt = trimmed.ifBlank {
            if (isArabic) "حلل الوسائط والملفات المرفقة بدقة." else "Analyze the attached media and context files."
        }
        val userTokens = (effectivePrompt.length / 4L + currentAttachments.sumOf { it.estimatedTokens }).coerceAtLeast(1L)

        viewModelScope.launch {
            _generationState.value = UiState.Loading
            val startTime = System.currentTimeMillis()
            try {
                // Snapshot prior conversation history BEFORE writing the new USER message
                val historySnapshot = if (_activeSessionId.value != null) activeMessages.value else emptyList()

                val sessionId = ensureActiveSession(
                    title = effectivePrompt.take(70),
                    mode = SessionMode.WORKSPACE,
                    level = activeLevel,
                    totalTokens = estimatedContextTokens(effectivePrompt),
                    summary = currentAttachments.joinToString(", ") { it.name },
                    languageCode = languageCode
                )

                val attachmentNames = currentAttachments.map { it.name }
                syncRepository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.USER,
                    content = effectivePrompt,
                    reasoningLevel = activeLevel,
                    attachmentNames = attachmentNames,
                    latencyMs = 0L,
                    tokenEstimate = userTokens
                )

                val builder = StringBuilder()
                aiRepository.streamWorkspaceResponse(
                    prompt = effectivePrompt,
                    reasoningLevel = activeLevel,
                    attachments = currentAttachments,
                    history = historySnapshot,
                    languageCode = languageCode
                ).collect { chunk ->
                    builder.append(chunk)
                    _generationState.value = UiState.Streaming(builder.toString())
                }

                val finalOutput = builder.toString()
                if (finalOutput.isBlank()) {
                    _generationState.value = UiState.Error("Model returned an empty response.")
                    return@launch
                }

                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                val outTokens = (finalOutput.length / 4L).coerceAtLeast(1L)

                syncRepository.addMessage(
                    sessionId = sessionId,
                    role = MessageRole.MODEL,
                    content = finalOutput,
                    reasoningLevel = activeLevel,
                    attachmentNames = emptyList(),
                    latencyMs = elapsed,
                    tokenEstimate = outTokens
                )

                syncRepository.updateSessionContext(
                    sessionId = sessionId,
                    reasoningLevel = activeLevel,
                    estimatedContextTokens = estimatedContextTokens() + outTokens,
                    contextSummary = currentAttachments.joinToString(", ") { it.name },
                    languageCode = languageCode
                )

                _attachments.value = emptyList()
                _generationState.value = UiState.Success(finalOutput)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Workspace generation timed out", e)
                _generationState.value = UiState.Error("Generation timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Workspace generation failed", e)
                val msg = e.localizedMessage ?: "Cognitive generation failed."
                _generationState.value = UiState.Error(
                    message = msg,
                    isXHighBillingIssue = aiRepository.isXHighBillingError(e, activeLevel)
                )
            }
        }
    }

    fun runCodeMathAnalysis(
        codeOrTable: String,
        objective: String,
        languageCode: String
    ) {
        if (codeOrTable.isBlank() && objective.isBlank()) return
        val level = _reasoningLevel.value

        viewModelScope.launch {
            _codeMathState.value = UiState.Loading
            val startTime = System.currentTimeMillis()
            val builder = StringBuilder()
            try {
                aiRepository.streamCodeMathAnalysis(
                    codeOrTable = codeOrTable,
                    objective = objective,
                    reasoningLevel = level,
                    languageCode = languageCode
                ).collect { chunk ->
                    builder.append(chunk)
                    _codeMathState.value = UiState.Streaming(builder.toString())
                }

                val result = builder.toString()
                if (result.isBlank()) {
                    _codeMathState.value = UiState.Error("Model returned an empty analysis.")
                    return@launch
                }

                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                val sessionResult = syncRepository.createSession(
                    title = objective.trim().ifBlank { "Code & Math Analysis" }.take(80),
                    mode = SessionMode.CODE_MATH,
                    reasoningLevel = level,
                    estimatedContextTokens = ((codeOrTable.length + result.length) / 4L).coerceAtLeast(1L),
                    contextSummary = "Code & Math Lab",
                    languageCode = languageCode
                )
                sessionResult.getOrNull()?.let { sid ->
                    syncRepository.addMessage(
                        sessionId = sid,
                        role = MessageRole.USER,
                        content = "${objective.trim()}\n\n```\n${codeOrTable.take(12000)}\n```",
                        reasoningLevel = level,
                        attachmentNames = emptyList(),
                        latencyMs = 0L,
                        tokenEstimate = (codeOrTable.length / 4L).coerceAtLeast(1L)
                    )
                    syncRepository.addMessage(
                        sessionId = sid,
                        role = MessageRole.MODEL,
                        content = result,
                        reasoningLevel = level,
                        attachmentNames = emptyList(),
                        latencyMs = elapsed,
                        tokenEstimate = (result.length / 4L).coerceAtLeast(1L)
                    )
                }

                _codeMathState.value = UiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Code/Math analysis timed out", e)
                _codeMathState.value = UiState.Error("Analysis timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Code/Math analysis failed", e)
                val msg = e.localizedMessage ?: "Code & Math analysis failed."
                _codeMathState.value = UiState.Error(
                    message = msg,
                    isXHighBillingIssue = aiRepository.isXHighBillingError(e, level)
                )
            }
        }
    }

    private suspend fun ensureActiveSession(
        title: String,
        mode: SessionMode,
        level: ReasoningLevel,
        totalTokens: Long,
        summary: String,
        languageCode: String
    ): String {
        val existing = _activeSessionId.value
        if (!existing.isNullOrBlank()) return existing
        val created = syncRepository.createSession(
            title = title,
            mode = mode,
            reasoningLevel = level,
            estimatedContextTokens = totalTokens,
            contextSummary = summary,
            languageCode = languageCode
        ).getOrThrow()
        _activeSessionId.value = created
        return created
    }
}
