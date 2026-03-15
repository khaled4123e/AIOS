// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.aios.shell.ai.AIManager
import dev.aios.shell.ai.AIResponse
import dev.aios.shell.broker.*
import dev.aios.shell.planner.TaskPlanner
import dev.aios.shell.planner.TaskPlan
import dev.aios.shell.planner.TaskStep
import dev.aios.shell.policy.Decision
import dev.aios.shell.policy.PolicyEngine
import dev.aios.shell.tools.SystemToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val toolId: String? = null,
    val decision: Decision? = null,
    val type: MessageType = MessageType.TEXT,
)

enum class MessageType {
    TEXT,
    PLAN,
    POLICY_RESULT,
    TOOL_RESULT,
    ERROR,
    MULTI_STEP,
    AI_THINKING,
}

data class PendingAction(
    val intent: ParsedIntent,
    val policyDecision: Decision,
    val consentRequired: String?,
    val plan: TaskPlan? = null,
)

enum class AvatarMood {
    IDLE,
    LISTENING,
    THINKING,
    EXECUTING,
    SUCCESS,
    BLOCKED,
    ALERT,
}

data class ShellUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val pendingAction: PendingAction? = null,
    val avatarMood: AvatarMood = AvatarMood.IDLE,
    val statusText: String = "Bereit",
    val isChatOpen: Boolean = false,
    val toolCount: Int = 0,
    val auditCount: Int = 0,
    val aiAvailable: Boolean = false,
)

class ShellViewModel(context: Context) : ViewModel() {

    private val policyEngine = PolicyEngine()
    private val systemExecutor = SystemToolExecutor(context)
    private val toolBroker = ToolBroker(policyEngine, systemExecutor)
    private val intentParser = IntentParser()
    private val taskPlanner = TaskPlanner()
    private val aiManager = AIManager(context)

    private val _uiState = MutableStateFlow(ShellUiState(
        messages = listOf(
            ChatMessage(
                text = "Hallo! Ich bin AIOS, dein System-Assistent. " +
                    "Ich kann dein Geraet steuern, Apps oeffnen, Nachrichten senden und vieles mehr. " +
                    "Tippe mich an und sag mir, was ich tun soll.",
                isUser = false,
            )
        ),
        toolCount = 0,
    ))
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(toolCount = toolBroker.availableTools().size) }

        // Initialize AI backends in background
        viewModelScope.launch(Dispatchers.IO) {
            aiManager.initialize()
            val backends = aiManager.getAvailableBackends()
            val anyAvailable = backends.any { it.available }
            _uiState.update { it.copy(aiAvailable = anyAvailable) }
            if (anyAvailable) {
                val status = aiManager.getStatus()
                addMessage(ChatMessage(
                    text = "KI-Backend verbunden (${status.activeBackend.name}). Ich verstehe jetzt natuerliche Sprache.",
                    isUser = false,
                    type = MessageType.AI_THINKING,
                ))
            }
        }
    }

    fun onAvatarTapped() {
        _uiState.update { it.copy(isChatOpen = !it.isChatOpen) }
    }

    fun onChatDismiss() {
        _uiState.update { it.copy(isChatOpen = false) }
    }

    fun onUserTyping() {
        _uiState.update { it.copy(avatarMood = AvatarMood.LISTENING) }
    }

    fun onUserInput(text: String) {
        if (text.isBlank()) return

        addMessage(ChatMessage(text = text, isUser = true))
        _uiState.update {
            it.copy(
                isProcessing = true,
                avatarMood = AvatarMood.THINKING,
                statusText = if (_uiState.value.aiAvailable) "KI denkt nach..." else "Verstehe...",
            )
        }

        // Run processing on IO thread to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            processInput(text)
        }
    }

    private suspend fun processInput(text: String) {
        // Try AI first, then fall back to keyword parser
        if (_uiState.value.aiAvailable) {
            val history = _uiState.value.messages.map { msg ->
                mapOf<String, Any>("text" to msg.text, "isUser" to msg.isUser)
            }

            val aiResponse = aiManager.chat(text, history)

            if (aiResponse.fromLLM && aiResponse.steps.isNotEmpty()) {
                // LLM returned tool calls — use them
                handleAIResponse(aiResponse)
                return
            } else if (aiResponse.fromLLM && aiResponse.steps.isEmpty() && aiResponse.confidence > 0.3f) {
                // LLM gave a conversational response (no tools needed)
                withContext(Dispatchers.Main) {
                    addMessage(ChatMessage(
                        text = aiResponse.message,
                        isUser = false,
                        type = MessageType.TEXT,
                    ))
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            avatarMood = AvatarMood.SUCCESS,
                            statusText = "Bereit",
                        )
                    }
                }
                return
            }
            // If AI failed, fall through to keyword parser
        }

        // Fallback: Keyword-based parsing
        withContext(Dispatchers.Main) {
            processWithKeywords(text)
        }
    }

    private suspend fun handleAIResponse(aiResponse: AIResponse) {
        // Convert AI steps to a TaskPlan
        val steps = aiResponse.steps.mapIndexed { index, aiStep ->
            TaskStep(
                stepId = index + 1,
                toolId = aiStep.toolId,
                parameters = aiStep.params,
                description = aiStep.description,
                confidence = aiResponse.confidence,
            )
        }

        val plan = TaskPlan(
            steps = steps,
            explanation = aiResponse.message,
            isMultiStep = steps.size > 1,
            estimatedRisk = assessRisk(steps),
        )

        withContext(Dispatchers.Main) {
            // Show plan
            val planText = if (plan.isMultiStep) {
                "KI-Plan (${plan.steps.size} Schritte):\n" +
                    plan.steps.joinToString("\n") { step ->
                        "  ${step.stepId}. ${step.description} [${step.toolId}]"
                    } +
                    "\nInferenz: ${aiResponse.durationMs}ms"
            } else {
                val step = plan.steps.first()
                "KI: ${step.description}\nTool: ${step.toolId}\nInferenz: ${aiResponse.durationMs}ms"
            }

            addMessage(ChatMessage(
                text = planText,
                isUser = false,
                toolId = plan.steps.first().toolId,
                type = if (plan.isMultiStep) MessageType.MULTI_STEP else MessageType.PLAN,
            ))

            _uiState.update { it.copy(statusText = aiResponse.message) }

            // Policy check
            evaluateAndExecutePlan(plan)
        }
    }

    private fun processWithKeywords(text: String) {
        val plan = taskPlanner.plan(text, intentParser.parse(text))

        if (plan.steps.isEmpty()) {
            _uiState.update {
                it.copy(
                    avatarMood = AvatarMood.IDLE,
                    statusText = "Bereit",
                    isProcessing = false,
                )
            }
            addMessage(ChatMessage(
                text = "Das habe ich nicht verstanden. Ich kann z.B.:\n" +
                    "- Nicht Stoeren aktivieren\n" +
                    "- Helligkeit auf 70% setzen\n" +
                    "- WiFi ein/ausschalten\n" +
                    "- Apps oeffnen\n" +
                    "- Termine erstellen\n" +
                    "- Wecker stellen\n" +
                    "...und vieles mehr!",
                isUser = false,
                type = MessageType.ERROR,
            ))
            return
        }

        val planText = if (plan.isMultiStep) {
            "Plan (${plan.steps.size} Schritte):\n" +
                plan.steps.joinToString("\n") { step ->
                    "  ${step.stepId}. ${step.description} [${step.toolId}]"
                } +
                "\nRisiko: ${plan.estimatedRisk}"
        } else {
            val step = plan.steps.first()
            "Plan: ${step.description}\nTool: ${step.toolId}\nKonfidenz: ${(step.confidence * 100).toInt()}%"
        }

        addMessage(ChatMessage(
            text = planText,
            isUser = false,
            toolId = plan.steps.first().toolId,
            type = if (plan.isMultiStep) MessageType.MULTI_STEP else MessageType.PLAN,
        ))

        _uiState.update { it.copy(statusText = plan.explanation) }
        evaluateAndExecutePlan(plan)
    }

    private fun evaluateAndExecutePlan(plan: TaskPlan) {
        val highestRiskStep = plan.steps.maxByOrNull { step ->
            val tool = toolBroker.availableTools().find { it.id == step.toolId }
            tool?.riskClass?.ordinal ?: 0
        } ?: plan.steps.first()

        val tool = toolBroker.availableTools().find { it.id == highestRiskStep.toolId }
        val policyContext = dev.aios.shell.policy.PolicyContext(
            toolId = highestRiskStep.toolId,
            riskClass = tool?.riskClass ?: RiskClass.LOW,
            capabilities = tool?.capabilities ?: emptyList(),
            sideEffects = tool?.sideEffects ?: emptyList(),
        )
        val policyResult = policyEngine.evaluate(policyContext)

        addMessage(ChatMessage(
            text = "Policy: ${policyResult.decision.name}\n${policyResult.reason}",
            isUser = false,
            decision = policyResult.decision,
            type = MessageType.POLICY_RESULT,
        ))

        when (policyResult.decision) {
            Decision.DENY, Decision.QUARANTINE -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        avatarMood = AvatarMood.BLOCKED,
                        statusText = "Blockiert",
                    )
                }
                addMessage(ChatMessage(
                    text = "Aktion blockiert von Policy Engine.",
                    isUser = false,
                    type = MessageType.ERROR,
                ))
            }
            Decision.REQUIRE_CONFIRMATION -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        avatarMood = AvatarMood.ALERT,
                        statusText = "Bestaetigung noetig",
                        pendingAction = PendingAction(
                            intent = ParsedIntent(
                                toolId = highestRiskStep.toolId,
                                parameters = highestRiskStep.parameters,
                                confidence = highestRiskStep.confidence,
                                explanation = plan.explanation,
                            ),
                            policyDecision = policyResult.decision,
                            consentRequired = policyResult.consentRequired,
                            plan = plan,
                        ),
                    )
                }
            }
            Decision.ALLOW, Decision.ALLOW_WITH_LOG -> {
                executePlan(plan)
            }
        }
    }

    fun onApprove() {
        val pending = _uiState.value.pendingAction ?: return
        _uiState.update {
            it.copy(
                pendingAction = null,
                isProcessing = true,
                avatarMood = AvatarMood.EXECUTING,
                statusText = "Fuehre aus...",
            )
        }
        addMessage(ChatMessage(text = "Freigabe erteilt", isUser = true))

        if (pending.plan != null) {
            executePlan(pending.plan)
        } else {
            executeAction(pending.intent)
        }
    }

    fun onDeny() {
        _uiState.update {
            it.copy(
                pendingAction = null,
                avatarMood = AvatarMood.IDLE,
                statusText = "Bereit",
            )
        }
        addMessage(ChatMessage(text = "Aktion abgebrochen.", isUser = false))
    }

    private fun executePlan(plan: TaskPlan) {
        _uiState.update {
            it.copy(avatarMood = AvatarMood.EXECUTING, statusText = "Fuehre aus...")
        }

        val results = mutableListOf<String>()
        var allSuccess = true

        for (step in plan.steps) {
            val call = ToolCall(toolId = step.toolId, input = step.parameters)
            val result = toolBroker.execute(call)

            val message = result.output["message"] as? String ?: "Ausgefuehrt"

            if (result.status != ExecutionStatus.SUCCESS) {
                allSuccess = false
                results.add("FEHLER Schritt ${step.stepId}: $message")
            } else {
                results.add("Schritt ${step.stepId}: $message")
            }
        }

        val summaryText = if (plan.isMultiStep) {
            results.joinToString("\n") +
                "\n\nAlle ${plan.steps.size} Schritte abgeschlossen."
        } else {
            results.firstOrNull() ?: "Ausgefuehrt"
        }

        addMessage(ChatMessage(
            text = summaryText,
            isUser = false,
            type = MessageType.TOOL_RESULT,
        ))

        _uiState.update {
            it.copy(
                isProcessing = false,
                avatarMood = if (allSuccess) AvatarMood.SUCCESS else AvatarMood.BLOCKED,
                statusText = if (allSuccess) "Erledigt" else "Teilweise fehlgeschlagen",
                auditCount = toolBroker.getAuditLog().size,
            )
        }
    }

    private fun executeAction(intent: ParsedIntent) {
        _uiState.update {
            it.copy(avatarMood = AvatarMood.EXECUTING, statusText = "Fuehre aus...")
        }

        val call = ToolCall(toolId = intent.toolId, input = intent.parameters)
        val result = toolBroker.execute(call)

        val message = result.output["message"] as? String ?: "Ausgefuehrt"
        addMessage(ChatMessage(
            text = "${message}\nDauer: ${result.durationMs}ms | Audit: ${result.callId.take(8)}",
            isUser = false,
            toolId = result.toolId,
            type = MessageType.TOOL_RESULT,
        ))

        _uiState.update {
            it.copy(
                isProcessing = false,
                avatarMood = AvatarMood.SUCCESS,
                statusText = message,
                auditCount = toolBroker.getAuditLog().size,
            )
        }
    }

    private fun assessRisk(steps: List<TaskStep>): String {
        var maxRisk = 0
        for (step in steps) {
            val tool = toolBroker.availableTools().find { it.id == step.toolId }
            val risk = tool?.riskClass?.ordinal ?: 0
            if (risk > maxRisk) maxRisk = risk
        }
        return when (maxRisk) {
            0 -> "niedrig"
            1 -> "mittel"
            2 -> "hoch"
            else -> "kritisch"
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { state ->
            state.copy(messages = state.messages + message)
        }
    }

    // --- Settings-Integration ---

    fun setPreferredBackend(type: dev.aios.shell.ai.BackendType) {
        viewModelScope.launch(Dispatchers.IO) {
            aiManager.setPreferredBackend(type)
            val backends = aiManager.getAvailableBackends()
            val anyAvailable = backends.any { it.available }
            _uiState.update { it.copy(aiAvailable = anyAvailable) }
        }
    }

    fun configureExternalAPI(type: String, key: String, model: String, url: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val apiType = try {
                dev.aios.shell.ai.APIType.valueOf(type)
            } catch (_: Exception) {
                dev.aios.shell.ai.APIType.CUSTOM
            }
            aiManager.configureExternalAPI(apiType, key, model, url)
            val status = aiManager.getStatus()
            _uiState.update { it.copy(aiAvailable = status.apiConfigured) }
            withContext(Dispatchers.Main) {
                addMessage(ChatMessage(
                    text = "API konfiguriert: ${apiType.name} ($model)",
                    isUser = false,
                    type = MessageType.AI_THINKING,
                ))
            }
        }
    }

    fun configureLocalModel(modelName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            aiManager.configureLocalModel(modelName)
        }
    }

    fun updateSetting(key: String, value: Any) {
        viewModelScope.launch(Dispatchers.IO) {
            when (key) {
                "temperature" -> aiManager.config.temperature = (value as Number).toFloat()
                "maxTokens" -> aiManager.config.maxTokens = (value as Number).toInt()
                "contextSize" -> aiManager.config.contextSize = (value as Number).toInt()
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShellViewModel(context.applicationContext) as T
        }
    }
}
