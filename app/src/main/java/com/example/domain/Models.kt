package com.example.domain

import android.graphics.Bitmap
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ReasoningLevel(
    val modelName: String,
    val codeTag: String,
    val targetLatencyLabel: String
) {
    LOW("gemini-3.5-flash-lite", "LOW", "~0.4s"),
    MEDIUM("gemini-3.8-flash", "MED", "~1.1s"),
    HIGH("gemini-3.8-flash", "HIGH", "~2.0s"),
    XHIGH("gemini-4-argon", "xhigh", "~4.5s");

    companion object {
        fun fromString(raw: String?): ReasoningLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: HIGH
    }
}

enum class SessionMode {
    WORKSPACE,
    CODE_MATH;

    companion object {
        fun fromString(raw: String?): SessionMode =
            entries.firstOrNull { it.name == raw } ?: WORKSPACE
    }
}

enum class MessageRole {
    USER,
    MODEL;

    companion object {
        fun fromString(raw: String?): MessageRole =
            entries.firstOrNull { it.name == raw } ?: USER
    }
}

enum class WorkflowPresetType {
    SCHEDULE,
    SORTING,
    AUTOMATION,
    CUSTOM;

    companion object {
        fun fromString(raw: String?): WorkflowPresetType =
            entries.firstOrNull { it.name == raw } ?: CUSTOM
    }
}

enum class SecurityStatus {
    SAFE,
    SANITIZED,
    BLOCKED;

    companion object {
        fun fromString(raw: String?): SecurityStatus =
            entries.firstOrNull { it.name == raw } ?: SAFE
    }
}

enum class IdeType(val displayName: String, val extensionId: String) {
    VS_CODE("VS Code", "coremind.vscode-bridge"),
    PYCHARM("PyCharm", "com.coremind.pycharm.bridge"),
    INTELLIJ("IntelliJ IDEA", "com.coremind.intellij.bridge");

    companion object {
        fun fromString(raw: String?): IdeType =
            entries.firstOrNull { it.name == raw } ?: VS_CODE
    }
}

enum class IdeTaskType {
    DIAGNOSTIC,
    PATCH_REVIEW,
    AGENT_HANDOFF;

    companion object {
        fun fromString(raw: String?): IdeTaskType =
            entries.firstOrNull { it.name == raw } ?: DIAGNOSTIC
    }
}

enum class IdeTaskStatus {
    PENDING_ANALYSIS,
    PATCH_READY,
    APPROVED,
    REJECTED,
    APPLIED;

    companion object {
        fun fromString(raw: String?): IdeTaskStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING_ANALYSIS
    }
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromString(raw: String?): RiskLevel =
            entries.firstOrNull { it.name == raw } ?: LOW
    }
}

data class ContextAttachment(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val estimatedTokens: Long,
    val textContent: String? = null,
    val bitmap: Bitmap? = null,
    val mediaBytes: ByteArray? = null
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val preferredReasoningLevel: String = ReasoningLevel.HIGH.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CognitiveSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val mode: String = SessionMode.WORKSPACE.name,
    val reasoningLevel: String = ReasoningLevel.HIGH.name,
    val estimatedContextTokens: Long = 0L,
    val contextSummary: String = "",
    val languageCode: String = "ar",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SessionMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = MessageRole.USER.name,
    val content: String = "",
    val reasoningLevel: String = ReasoningLevel.HIGH.name,
    val attachmentNames: List<String> = emptyList(),
    val latencyMs: Long = 0L,
    val tokenEstimate: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AgentStepRecord(
    val stepIndex: Long = 0L,
    val title: String = "",
    val agentRole: String = "",
    val permissionScope: String = "READ_ONLY",
    val status: String = "COMPLETED",
    val output: String = ""
)

@Keep
@IgnoreExtraProperties
data class AgentWorkflowRun(
    @DocumentId val id: String = "",
    val userId: String = "",
    val goal: String = "",
    val presetType: String = WorkflowPresetType.CUSTOM.name,
    val reasoningLevel: String = ReasoningLevel.HIGH.name,
    val securityStatus: String = SecurityStatus.SAFE.name,
    val injectionRiskScore: Long = 0L,
    val securityNotes: String = "",
    val steps: List<AgentStepRecord> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IdeWorkspace(
    @DocumentId val id: String = "",
    val userId: String = "",
    val ideType: String = IdeType.VS_CODE.name,
    val projectName: String = "",
    val branchName: String = "main",
    val pairingCode: String = "",
    val bridgeStatus: String = "CONNECTED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class IdeTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val workspaceId: String = "",
    val ideType: String = IdeType.VS_CODE.name,
    val taskType: String = IdeTaskType.DIAGNOSTIC.name,
    val title: String = "",
    val filePath: String = "",
    val language: String = "kotlin",
    val codeSnippet: String = "",
    val stackTrace: String = "",
    val diagnosis: String = "",
    val unifiedDiff: String = "",
    val riskLevel: String = RiskLevel.LOW.name,
    val reasoningLevel: String = ReasoningLevel.HIGH.name,
    val securityStatus: String = SecurityStatus.SAFE.name,
    val status: String = IdeTaskStatus.PENDING_ANALYSIS.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Streaming(val text: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val isXHighBillingIssue: Boolean = false) : UiState<Nothing>
}
