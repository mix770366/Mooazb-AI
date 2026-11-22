package com.example.data

import android.util.Log
import com.example.domain.AgentStepRecord
import com.example.domain.ContextAttachment
import com.example.domain.IdeTaskType
import com.example.domain.IdeType
import com.example.domain.MessageRole
import com.example.domain.ReasoningLevel
import com.example.domain.RiskLevel
import com.example.domain.SessionMessage
import com.example.domain.WorkflowPresetType
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "CognitiveAiRepo"
private const val INFERENCE_TIMEOUT_MS = 60_000L
private const val MAX_INLINE_MEDIA_BYTES = 20L * 1024L * 1024L

data class IdePatchAnalysis(
    val diagnosis: String,
    val unifiedDiff: String,
    val riskLevel: RiskLevel
)

class CognitiveAiRepository {

    private val lowModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ReasoningLevel.LOW.modelName,
            systemInstruction = content {
                text("You are Mooazb AI Cognitive Engine running at LOW reasoning tier: deliver ultra-fast, concise, accurate responses with minimal overhead.")
            }
        )
    }

    private val mediumModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ReasoningLevel.MEDIUM.modelName,
            systemInstruction = content {
                text("You are Mooazb AI Cognitive Engine running at MEDIUM reasoning tier: balance high execution speed with clear analytical justification.")
            }
        )
    }

    private val highModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ReasoningLevel.HIGH.modelName,
            systemInstruction = content {
                text("You are Mooazb AI Cognitive Engine running at HIGH (default) reasoning tier: provide thorough, structured engineering, coding, and analytical reasoning.")
            }
        )
    }

    private val xHighModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ReasoningLevel.XHIGH.modelName,
            systemInstruction = content {
                text("You are Mooazb AI Cognitive Engine running at xhigh reasoning tier: perform deep multi-step deductive verification, edge-case proof, and rigorous architectural analysis.")
            }
        )
    }

    private val agentSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "High-level security and execution summary"),
            "steps" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "stepIndex" to Schema.integer(description = "1-based sequential step number"),
                        "title" to Schema.string(description = "Concise step title"),
                        "agentRole" to Schema.string(description = "Assigned specialized sub-agent role"),
                        "permissionScope" to Schema.enumeration(
                            values = listOf("READ_ONLY", "CONTEXT_INDEX", "PATCH_PROPOSAL", "SANDBOX_COMPUTE"),
                            description = "Strict least-privilege boundary"
                        ),
                        "status" to Schema.enumeration(
                            values = listOf("COMPLETED", "VERIFIED", "GUARDED"),
                            description = "Execution status"
                        ),
                        "output" to Schema.string(description = "Concrete outcome produced by this step")
                    )
                ),
                description = "Sequential multi-step agent execution plan (max 6 steps)"
            )
        )
    )

    private val idePatchSchema = Schema.obj(
        properties = mapOf(
            "diagnosis" to Schema.string(description = "Root-cause explanation and technical fix rationale"),
            "unifiedDiff" to Schema.string(description = "Valid unified diff patch with --- +++ @@ - + lines"),
            "riskLevel" to Schema.enumeration(
                values = listOf("LOW", "MEDIUM", "HIGH"),
                description = "Estimated change risk level"
            )
        )
    )

    private fun modelFor(level: ReasoningLevel): GenerativeModel = when (level) {
        ReasoningLevel.LOW -> lowModel
        ReasoningLevel.MEDIUM -> mediumModel
        ReasoningLevel.HIGH -> highModel
        ReasoningLevel.XHIGH -> xHighModel
    }

    fun isXHighBillingError(error: Throwable, level: ReasoningLevel): Boolean {
        if (level != ReasoningLevel.XHIGH) return false
        val msg = error.localizedMessage.orEmpty()
        return msg.contains("billing", ignoreCase = true) ||
            msg.contains("quota", ignoreCase = true) ||
            msg.contains("permission", ignoreCase = true) ||
            msg.contains("403", ignoreCase = true) ||
            msg.contains("not found", ignoreCase = true) ||
            msg.contains("blaze", ignoreCase = true)
    }

    internal fun buildNormalizedChatHistory(history: List<SessionMessage>): List<Content> {
        // Ensure alternating user -> model pairs ending on "model" for startChat()
        val coalesced = mutableListOf<Pair<String, String>>()
        for (msg in history.takeLast(16)) {
            val role = if (MessageRole.fromString(msg.role) == MessageRole.USER) "user" else "model"
            val text = msg.content.trim()
            if (text.isEmpty()) continue
            if (coalesced.isEmpty()) {
                if (role == "user") coalesced.add(role to text)
            } else if (coalesced.last().first == role) {
                val prev = coalesced.removeAt(coalesced.lastIndex)
                coalesced.add(role to "${prev.second}\n\n$text")
            } else {
                coalesced.add(role to text)
            }
        }
        if (coalesced.isNotEmpty() && coalesced.last().first == "user") {
            coalesced.removeAt(coalesced.lastIndex)
        }
        return coalesced.map { (role, text) ->
            content(role = role) { text(text) }
        }
    }

    fun streamWorkspaceResponse(
        prompt: String,
        reasoningLevel: ReasoningLevel,
        attachments: List<ContextAttachment>,
        history: List<SessionMessage>,
        languageCode: String
    ): Flow<String> = flow {
        val model = modelFor(reasoningLevel)
        val langDirective = if (languageCode == "ar") {
            "Respond clearly in Arabic while keeping code snippets, technical symbols, and identifiers in English."
        } else {
            "Respond clearly in English."
        }
        val reasoningDirective = when (reasoningLevel) {
            ReasoningLevel.LOW -> "[Tier: LOW — concise, fast direct answer]"
            ReasoningLevel.MEDIUM -> "[Tier: MEDIUM — balanced analysis]"
            ReasoningLevel.HIGH -> "[Tier: HIGH — thorough step-by-step engineering reasoning]"
            ReasoningLevel.XHIGH -> "[Tier: xhigh — exhaustive multi-step deductive verification]"
        }

        val chatHistory: List<Content> = buildNormalizedChatHistory(history)

        val userContent = content(role = "user") {
            text("$langDirective\n$reasoningDirective\n\n")
            attachments.forEach { attachment ->
                when {
                    attachment.bitmap != null && attachment.sizeBytes <= MAX_INLINE_MEDIA_BYTES -> {
                        text("[Attached Image: ${attachment.name}]\n")
                        image(attachment.bitmap)
                    }
                    attachment.mediaBytes != null && attachment.sizeBytes <= MAX_INLINE_MEDIA_BYTES -> {
                        text("[Attached Media: ${attachment.name} (${attachment.mimeType})]\n")
                        inlineData(attachment.mediaBytes, attachment.mimeType)
                    }
                    !attachment.textContent.isNullOrBlank() -> {
                        text("[Attached Context File: ${attachment.name}]\n${attachment.textContent}\n\n")
                    }
                }
            }
            text(prompt)
        }

        val stream = if (chatHistory.isNotEmpty() && attachments.none { it.bitmap != null || it.mediaBytes != null }) {
            val chat = model.startChat(history = chatHistory)
            chat.sendMessageStream(userContent)
        } else {
            model.generateContentStream(userContent)
        }

        withTimeout(INFERENCE_TIMEOUT_MS) {
            stream.collect { chunk ->
                val chunkText = chunk.text
                if (!chunkText.isNullOrEmpty()) {
                    emit(chunkText)
                }
            }
        }
    }

    suspend fun planAgenticWorkflow(
        sanitizedGoal: String,
        presetType: WorkflowPresetType,
        reasoningLevel: ReasoningLevel,
        languageCode: String
    ): List<AgentStepRecord> {
        val structuredModel = Firebase.ai.generativeModel(
            modelName = reasoningLevel.modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = agentSchema
            }
        )
        val langPrompt = if (languageCode == "ar") {
            "Write step titles and outputs in Arabic (keeping technical roles and identifiers concise)."
        } else {
            "Write step titles and outputs in English."
        }
        val promptText = """
            You are Mooazb AI Agentic Orchestrator (Preset: ${presetType.name}, Reasoning: ${reasoningLevel.codeTag}).
            Decompose the user's goal into 3 to 5 safe, verifiable execution steps under strict least-privilege permissions.
            $langPrompt
            Goal: $sanitizedGoal
        """.trimIndent()

        try {
            val response = withTimeout(INFERENCE_TIMEOUT_MS) {
                structuredModel.generateContent(promptText)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty agentic response from model.")
            val root = JSONObject(jsonText)
            val stepsArray = root.getJSONArray("steps")
            return (0 until stepsArray.length()).map { idx ->
                val item = stepsArray.getJSONObject(idx)
                AgentStepRecord(
                    stepIndex = item.optLong("stepIndex", (idx + 1).toLong()),
                    title = item.optString("title", "Step ${idx + 1}"),
                    agentRole = item.optString("agentRole", "CoreAgent"),
                    permissionScope = item.optString("permissionScope", "READ_ONLY"),
                    status = item.optString("status", "COMPLETED"),
                    output = item.optString("output", "")
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Agentic planning timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Agentic planning failed", e)
            throw e
        }
    }

    suspend fun analyzeIdeTaskAndGeneratePatch(
        ideType: IdeType,
        taskType: IdeTaskType,
        filePath: String,
        language: String,
        codeSnippet: String,
        stackTrace: String,
        reasoningLevel: ReasoningLevel,
        languageCode: String
    ): IdePatchAnalysis {
        val patchModel = Firebase.ai.generativeModel(
            modelName = reasoningLevel.modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = idePatchSchema
            }
        )
        val explanationLang = if (languageCode == "ar") {
            "Write 'diagnosis' in Arabic, and keep 'unifiedDiff' strictly in valid unified diff syntax for $filePath."
        } else {
            "Write 'diagnosis' in English, and keep 'unifiedDiff' strictly in valid unified diff syntax for $filePath."
        }
        val promptText = """
            You are Mooazb AI IDE Bridge Engine connected to ${ideType.displayName} (Task: ${taskType.name}, Tier: ${reasoningLevel.codeTag}).
            $explanationLang
            File: $filePath ($language)
            Code Snippet:
            $codeSnippet
            
            Stacktrace / Diagnostics:
            ${stackTrace.ifBlank { "None provided — perform static bug, safety, and performance audit." }}
        """.trimIndent()

        try {
            val response = withTimeout(INFERENCE_TIMEOUT_MS) {
                patchModel.generateContent(promptText)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty IDE patch response from model.")
            val root = JSONObject(jsonText)
            return IdePatchAnalysis(
                diagnosis = root.optString("diagnosis", ""),
                unifiedDiff = root.optString("unifiedDiff", ""),
                riskLevel = RiskLevel.fromString(root.optString("riskLevel", "LOW"))
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "IDE analysis timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "IDE analysis failed", e)
            throw e
        }
    }

    fun streamCodeMathAnalysis(
        codeOrTable: String,
        objective: String,
        reasoningLevel: ReasoningLevel,
        languageCode: String
    ): Flow<String> = flow {
        val model = modelFor(reasoningLevel)
        val langDirective = if (languageCode == "ar") {
            "Explain findings clearly in Arabic, keeping code blocks, formulas, and numerical tables cleanly formatted."
        } else {
            "Explain findings clearly in English with clean code blocks and step-by-step calculations."
        }
        val fullPrompt = """
            $langDirective
            [Code & Computational Engine · Reasoning Tier: ${reasoningLevel.codeTag}]
            Objective: $objective
            
            Input Code / Mathematical Data:
            $codeOrTable
        """.trimIndent()

        withTimeout(INFERENCE_TIMEOUT_MS) {
            model.generateContentStream(fullPrompt).collect { chunk ->
                val text = chunk.text
                if (!text.isNullOrEmpty()) {
                    emit(text)
                }
            }
        }
    }
}
