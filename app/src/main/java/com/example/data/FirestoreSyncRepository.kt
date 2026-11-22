package com.example.data

import android.content.Context
import com.example.R
import com.example.domain.AgentStepRecord
import com.example.domain.AgentWorkflowRun
import com.example.domain.CognitiveSession
import com.example.domain.IdeTask
import com.example.domain.IdeTaskStatus
import com.example.domain.IdeTaskType
import com.example.domain.IdeType
import com.example.domain.IdeWorkspace
import com.example.domain.MessageRole
import com.example.domain.ReasoningLevel
import com.example.domain.RiskLevel
import com.example.domain.SecurityStatus
import com.example.domain.SessionMessage
import com.example.domain.SessionMode
import com.example.domain.UserProfile
import com.example.domain.WorkflowPresetType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> safeSnapshotFlow(
        path: String,
        uid: String,
        flowFactory: () -> Flow<T>
    ): Flow<T> = flow {
        emitAll(
            flowFactory().catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
        )
    }

    // --- USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return safeSnapshotFlow(path, userId) {
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
        }
    }

    suspend fun saveUserReasoningLevel(level: ReasoningLevel): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            try {
                docRef.update(
                    mapOf(
                        "preferredReasoningLevel" to level.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                    val createPayload = mapOf(
                        "userId" to uid,
                        "preferredReasoningLevel" to level.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(createPayload).await()
                } else {
                    throw e
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    // --- SESSIONS & MESSAGES ---

    fun observeSessions(userId: String): Flow<List<CognitiveSession>> {
        val path = "users/$userId/sessions"
        return safeSnapshotFlow(path, userId) {
            db.collection("users").document(userId).collection("sessions")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CognitiveSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }
    }

    fun observeMessages(userId: String, sessionId: String): Flow<List<SessionMessage>> {
        val path = "users/$userId/sessions/$sessionId/messages"
        return safeSnapshotFlow(path, userId) {
            db.collection("users").document(userId)
                .collection("sessions").document(sessionId)
                .collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SessionMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }
    }

    suspend fun createSession(
        title: String,
        mode: SessionMode,
        reasoningLevel: ReasoningLevel,
        estimatedContextTokens: Long,
        contextSummary: String,
        languageCode: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("sessions").document()
            docPath = docRef.path
            val safeTitle = title.trim().ifBlank { "Mooazb AI Session" }.take(160)
            val safeLang = if (languageCode == "en") "en" else "ar"
            val payload = mapOf(
                "userId" to uid,
                "title" to safeTitle,
                "mode" to mode.name,
                "reasoningLevel" to reasoningLevel.name,
                "estimatedContextTokens" to estimatedContextTokens.coerceIn(0L, 1_000_000L),
                "contextSummary" to contextSummary.take(2000),
                "languageCode" to safeLang,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSessionContext(
        sessionId: String,
        reasoningLevel: ReasoningLevel,
        estimatedContextTokens: Long,
        contextSummary: String,
        languageCode: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
            docPath = docRef.path
            val safeLang = if (languageCode == "en") "en" else "ar"
            docRef.update(
                mapOf(
                    "reasoningLevel" to reasoningLevel.name,
                    "estimatedContextTokens" to estimatedContextTokens.coerceIn(0L, 1_000_000L),
                    "contextSummary" to contextSummary.take(2000),
                    "languageCode" to safeLang,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        sessionId: String,
        role: MessageRole,
        content: String,
        reasoningLevel: ReasoningLevel,
        attachmentNames: List<String>,
        latencyMs: Long,
        tokenEstimate: Long
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions/$sessionId/messages"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
                .collection("messages").document()
            docPath = docRef.path
            val safeContent = content.trim().ifBlank { "-" }.take(32000)
            val safeAttachments = attachmentNames.take(10).map { it.take(120) }
            val payload = mapOf(
                "userId" to uid,
                "role" to role.name,
                "content" to safeContent,
                "reasoningLevel" to reasoningLevel.name,
                "attachmentNames" to safeAttachments,
                "latencyMs" to latencyMs.coerceIn(0L, 600_000L),
                "tokenEstimate" to tokenEstimate.coerceIn(0L, 1_000_000L),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            require(sessionId.isNotBlank())
            val sessionRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
            docPath = sessionRef.path
            val messagesSnap = sessionRef.collection("messages").get().await()
            messagesSnap.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            sessionRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- AGENT WORKFLOWS ---

    fun observeWorkflows(userId: String): Flow<List<AgentWorkflowRun>> {
        val path = "users/$userId/workflows"
        return safeSnapshotFlow(path, userId) {
            db.collection("users").document(userId).collection("workflows")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AgentWorkflowRun::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }
    }

    suspend fun saveWorkflowRun(
        goal: String,
        presetType: WorkflowPresetType,
        reasoningLevel: ReasoningLevel,
        securityStatus: SecurityStatus,
        injectionRiskScore: Long,
        securityNotes: String,
        steps: List<AgentStepRecord>
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workflows"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("workflows").document()
            docPath = docRef.path
            val stepMaps = steps.take(15).map { step ->
                mapOf(
                    "stepIndex" to step.stepIndex,
                    "title" to step.title.take(200),
                    "agentRole" to step.agentRole.take(100),
                    "permissionScope" to step.permissionScope.take(60),
                    "status" to step.status.take(40),
                    "output" to step.output.take(2000)
                )
            }
            val payload = mapOf(
                "userId" to uid,
                "goal" to goal.trim().ifBlank { "Workflow" }.take(2000),
                "presetType" to presetType.name,
                "reasoningLevel" to reasoningLevel.name,
                "securityStatus" to securityStatus.name,
                "injectionRiskScore" to injectionRiskScore.coerceIn(0L, 100L),
                "securityNotes" to securityNotes.take(1000),
                "steps" to stepMaps,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWorkflowRun(workflowId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/workflows/$workflowId"
        return try {
            val uid = requireUserId()
            require(workflowId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("workflows").document(workflowId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- IDE BRIDGE WORKSPACES & TASKS ---

    fun observeIdeWorkspaces(userId: String): Flow<List<IdeWorkspace>> {
        val path = "users/$userId/ideWorkspaces"
        return safeSnapshotFlow(path, userId) {
            db.collection("users").document(userId).collection("ideWorkspaces")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        IdeWorkspace::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }
    }

    fun observeIdeTasks(userId: String): Flow<List<IdeTask>> {
        val path = "users/$userId/ideTasks"
        return safeSnapshotFlow(path, userId) {
            db.collection("users").document(userId).collection("ideTasks")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        IdeTask::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
        }
    }

    suspend fun createIdeWorkspace(
        ideType: IdeType,
        projectName: String,
        branchName: String,
        pairingCode: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/ideWorkspaces"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("ideWorkspaces").document()
            docPath = docRef.path
            val safeProject = projectName.trim().ifBlank { "coremind-workspace" }.take(120)
            val safeBranch = branchName.trim().ifBlank { "main" }.take(80)
            val safeCode = pairingCode.trim().take(24).padEnd(4, '0')
            val payload = mapOf(
                "userId" to uid,
                "ideType" to ideType.name,
                "projectName" to safeProject,
                "branchName" to safeBranch,
                "pairingCode" to safeCode,
                "bridgeStatus" to "CONNECTED",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteIdeWorkspace(workspaceId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/ideWorkspaces/$workspaceId"
        return try {
            val uid = requireUserId()
            require(workspaceId.isNotBlank())
            val wsRef = db.collection("users").document(uid).collection("ideWorkspaces").document(workspaceId)
            docPath = wsRef.path
            val linkedTasks = db.collection("users").document(uid).collection("ideTasks")
                .whereEqualTo("workspaceId", workspaceId)
                .get()
                .await()
            linkedTasks.documents.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            wsRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createIdeTask(
        workspaceId: String,
        ideType: IdeType,
        taskType: IdeTaskType,
        title: String,
        filePath: String,
        language: String,
        codeSnippet: String,
        stackTrace: String,
        diagnosis: String,
        unifiedDiff: String,
        riskLevel: RiskLevel,
        reasoningLevel: ReasoningLevel,
        securityStatus: SecurityStatus,
        status: IdeTaskStatus
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/ideTasks"
        return try {
            val uid = requireUserId()
            val safeWorkspaceId = workspaceId.trim()
            require(safeWorkspaceId.isNotBlank()) { "A paired IDE workspace is required." }
            val docRef = db.collection("users").document(uid).collection("ideTasks").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "workspaceId" to safeWorkspaceId,
                "ideType" to ideType.name,
                "taskType" to taskType.name,
                "title" to title.trim().ifBlank { "IDE Diagnostic" }.take(160),
                "filePath" to filePath.trim().take(300),
                "language" to language.trim().ifBlank { "kotlin" }.take(40),
                "codeSnippet" to codeSnippet.take(20000),
                "stackTrace" to stackTrace.take(10000),
                "diagnosis" to diagnosis.take(15000),
                "unifiedDiff" to unifiedDiff.take(20000),
                "riskLevel" to riskLevel.name,
                "reasoningLevel" to reasoningLevel.name,
                "securityStatus" to securityStatus.name,
                "status" to status.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateIdeTaskAnalysis(
        taskId: String,
        diagnosis: String,
        unifiedDiff: String,
        riskLevel: RiskLevel,
        reasoningLevel: ReasoningLevel,
        securityStatus: SecurityStatus,
        status: IdeTaskStatus
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/ideTasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("ideTasks").document(taskId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "diagnosis" to diagnosis.take(15000),
                    "unifiedDiff" to unifiedDiff.take(20000),
                    "riskLevel" to riskLevel.name,
                    "reasoningLevel" to reasoningLevel.name,
                    "securityStatus" to securityStatus.name,
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateIdeTaskStatus(
        taskId: String,
        status: IdeTaskStatus
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/ideTasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("ideTasks").document(taskId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteIdeTask(taskId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/ideTasks/$taskId"
        return try {
            val uid = requireUserId()
            require(taskId.isNotBlank())
            val docRef = db.collection("users").document(uid).collection("ideTasks").document(taskId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
