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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.LocalAppLanguage
import com.example.core.LocalCoreStrings
import com.example.domain.ReasoningLevel
import com.example.domain.UiState
import com.example.ui.components.CodeBlockCard
import com.example.ui.components.EngineErrorBanner
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalCyberPalette
import com.example.ui.viewmodel.WorkspaceViewModel

@Composable
fun CodeMathScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val strings = LocalCoreStrings.current
    val language = LocalAppLanguage.current
    val cyber = LocalCyberPalette.current
    val reasoningLevel by viewModel.reasoningLevel.collectAsStateWithLifecycle()
    val codeMathState by viewModel.codeMathState.collectAsStateWithLifecycle()

    var codeOrTable by rememberSaveable {
        mutableStateOf(
            """
            | Quarter | Compute_ms | Throughput | Error_Rate |
            | Q1      | 42.1       | 14200      | 0.018      |
            | Q2      | 31.8       | 19500      | 0.009      |
            | Q3      | 24.4       | 25100      | 0.004      |
            """.trimIndent()
        )
    }
    var objective by rememberSaveable {
        mutableStateOf(
            if (language.code == "ar") {
                "احسب نسبة التحسن في الإنتاجية وانخفاض معدل الأخطاء عبر الأرباع الثلاثة."
            } else {
                "Compute throughput growth rate and error reduction across all three quarters."
            }
        )
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = strings.codeLabTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = strings.codeLabSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            codeOrTable = """
                                suspend fun fetchParallel(ids: List<String>) = coroutineScope {
                                    ids.map { async { loadItem(it) } }.awaitAll()
                                }
                            """.trimIndent()
                            objective = if (language.code == "ar") {
                                "اكتشف مخاطر التزامن في هذا الكود وأضف معالجة إلغاء وأخطاء آمنة."
                            } else {
                                "Audit concurrency risks and add safe cancellation and error handling."
                            }
                        },
                        leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Kotlin Debug") }
                    )
                    AssistChip(
                        onClick = {
                            codeOrTable = """
                                Matrix A = [[4, 2, 1], [0, 3, -1], [2, 1, 5]]
                                Target Vector b = [12, 5, 19]
                            """.trimIndent()
                            objective = if (language.code == "ar") {
                                "حل النظام الخطي Ax = b خطوة بخطوة وتحقق من المحدد."
                            } else {
                                "Solve the linear system Ax = b step-by-step and verify the determinant."
                            }
                        },
                        leadingIcon = { Icon(Icons.Outlined.Calculate, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Linear Math") }
                    )
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
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            OutlinedTextField(
                                value = codeOrTable,
                                onValueChange = { codeOrTable = it },
                                label = { Text(strings.codeInputLabel) },
                                minLines = 5,
                                maxLines = 12,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("code_lab_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = cyber.accent,
                                    unfocusedBorderColor = cyber.border,
                                    focusedContainerColor = cyber.background,
                                    unfocusedContainerColor = cyber.background
                                )
                            )
                        }
                        OutlinedTextField(
                            value = objective,
                            onValueChange = { objective = it },
                            label = { Text(strings.taskPromptLabel) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("code_lab_objective"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = cyber.accent,
                                unfocusedBorderColor = cyber.border,
                                focusedContainerColor = cyber.background,
                                unfocusedContainerColor = cyber.background
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.runCodeMathAnalysis(codeOrTable, objective, language.code)
                            },
                            enabled = codeMathState !is UiState.Loading && codeMathState !is UiState.Streaming,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_code_math_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = cyber.accent, contentColor = cyber.background)
                        ) {
                            if (codeMathState is UiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = cyber.background, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = strings.runCodeAnalysis, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${strings.runCodeAnalysis} · ${reasoningLevel.codeTag}", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (codeMathState is UiState.Error) {
                val err = codeMathState as UiState.Error
                item {
                    EngineErrorBanner(
                        message = err.message,
                        isXHighBillingIssue = err.isXHighBillingIssue,
                        onFallbackToHigh = {
                            viewModel.setReasoningLevel(ReasoningLevel.HIGH)
                            viewModel.clearError()
                        },
                        onDismiss = { viewModel.clearError() }
                    )
                }
            }

            val outputText = when (val st = codeMathState) {
                is UiState.Streaming -> st.text
                is UiState.Success -> st.data
                else -> null
            }

            if (!outputText.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = cyber.surface,
                        border = BorderStroke(1.dp, cyber.accent.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val segments = remember(outputText) { splitTextAndCodeBlocks(outputText) }
                            segments.forEach { (isCode, part) ->
                                if (isCode) {
                                    CodeBlockCard(title = "OUTPUT", code = part)
                                } else if (part.isNotBlank()) {
                                    Text(
                                        text = part,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
