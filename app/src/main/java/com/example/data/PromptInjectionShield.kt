package com.example.data

import com.example.domain.SecurityStatus

data class InjectionAuditResult(
    val status: SecurityStatus,
    val riskScore: Long,
    val sanitizedInput: String,
    val notes: String,
    val matchedRules: List<String>
)

object PromptInjectionShield {

    private val highRiskPatterns = listOf(
        "ignore_previous" to Regex("""(?i)(ignore|disregard|forget)\s+(all\s+)?(previous|prior|above)\s+(instructions|prompts|rules)"""),
        "system_override" to Regex("""(?i)(<\s*/?\s*system\s*>|\[SYSTEM\]|###\s*SYSTEM\s*OVERRIDE)"""),
        "secret_exfil" to Regex("""(?i)(reveal|dump|exfiltrate|print)\s+(system\s+prompt|api[_\s]?key|credentials|tokens|env)"""),
        "shell_exec_hijack" to Regex("""(?i)(curl\s+.*\|\s*(sh|bash)|rm\s+-rf\s+/|powershell\s+-enc)""")
    )

    private val mediumRiskPatterns = listOf(
        "role_hijack" to Regex("""(?i)(you\s+are\s+now\s+in\s+developer\s+mode|jailbreak|act\s+as\s+unrestricted)"""),
        "hidden_directive" to Regex("""(?i)(do\s+not\s+tell\s+the\s+user|silently\s+execute|bypass\s+permission)""")
    )

    fun audit(rawInput: String, isArabic: Boolean): InjectionAuditResult {
        if (rawInput.isBlank()) {
            return InjectionAuditResult(
                status = SecurityStatus.SAFE,
                riskScore = 0L,
                sanitizedInput = rawInput,
                notes = if (isArabic) "الإدخال فارغ أو آمن." else "Input verified clean.",
                matchedRules = emptyList()
            )
        }

        val matched = mutableListOf<String>()
        var score = 0L
        var sanitized = rawInput

        highRiskPatterns.forEach { (ruleName, regex) ->
            if (regex.containsMatchIn(sanitized)) {
                matched.add(ruleName)
                score += 45L
                sanitized = regex.replace(sanitized, "[REDACTED_DIRECTIVE]")
            }
        }

        mediumRiskPatterns.forEach { (ruleName, regex) ->
            if (regex.containsMatchIn(sanitized)) {
                matched.add(ruleName)
                score += 22L
                sanitized = regex.replace(sanitized, "[SANITIZED_INSTRUCTION]")
            }
        }

        val clampedScore = score.coerceIn(0L, 100L)
        val status = when {
            clampedScore >= 75L -> SecurityStatus.BLOCKED
            clampedScore > 0L -> SecurityStatus.SANITIZED
            else -> SecurityStatus.SAFE
        }

        val notes = when (status) {
            SecurityStatus.SAFE -> if (isArabic) {
                "تم فحص السياق: لم تُرصد أي محاولات حقن أوامر غير مباشرة."
            } else {
                "Pre-flight scan passed: no indirect prompt injection patterns detected."
            }
            SecurityStatus.SANITIZED -> if (isArabic) {
                "تم تحييد ${matched.size} نمط حقن مشبوه وتقييد صلاحيات التنفيذ."
            } else {
                "Sanitized ${matched.size} suspicious directive(s) and locked agent scope."
            }
            SecurityStatus.BLOCKED -> if (isArabic) {
                "تم حظر التنفيذ: رصد محاولة تجاوز صلاحيات عالية الخطورة (${matched.joinToString()})."
            } else {
                "Execution blocked: high-risk instruction hijacking attempt (${matched.joinToString()})."
            }
        }

        return InjectionAuditResult(
            status = status,
            riskScore = clampedScore,
            sanitizedInput = sanitized,
            notes = notes,
            matchedRules = matched
        )
    }
}
