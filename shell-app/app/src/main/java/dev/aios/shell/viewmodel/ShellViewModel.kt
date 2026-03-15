// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.viewmodel

import androidx.lifecycle.ViewModel
import dev.aios.shell.broker.*
import dev.aios.shell.policy.Decision
import dev.aios.shell.policy.PolicyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/// ShellViewModel — like an ObservableObject in SwiftUI.
/// Manages the conversation state and orchestrates the full pipeline:
/// Input → Intent Parse → Plan → Policy → Execute → Audit

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val toolId: String? = null,
    val decision: Decision? = null,
    val type: MessageType = MessageType.TEXT,
)

enum class MessageType {
    TEXT,           // Normal message
    PLAN,           // Shows the execution plan
    POLICY_RESULT,  // Shows policy decision
    TOOL_RESULT,    // Shows tool execution result
    ERROR,          // Error message
}

data class PendingAction(
    val intent: ParsedIntent,
    val policyDecision: Decision,
    val consentRequired: String?,
)

data class ShellUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isProcessing: Boolean = false,
    val pendingAction: PendingAction? = null,
)

class ShellViewModel : ViewModel() {

    private val policyEngine = PolicyEngine()
    private val toolBroker = ToolBroker(policyEngine)
    private val intentParser = IntentParser()

    private val _uiState = MutableStateFlow(ShellUiState(
        messages = listOf(
            ChatMessage(
                text = "Hallo! Ich bin AIOS, dein KI-Assistent. " +
                    "Ich kann Systemeinstellungen ändern, Termine verwalten und mehr. " +
                    "Was kann ich für dich tun?",
                isUser = false,
            )
        )
    ))
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    /// User sends a message — the main entry point.
    fun onUserInput(text: String) {
        if (text.isBlank()) return

        // Add user message
        addMessage(ChatMessage(text = text, isUser = true))
        _uiState.update { it.copy(isProcessing = true) }

        // 1. Parse intent
        val intent = intentParser.parse(text)

        if (intent == null) {
            addMessage(ChatMessage(
                text = "Ich habe leider nicht verstanden, was du möchtest. " +
                    "Versuche z.B.:\n" +
                    "• \"Stelle auf Nicht stören\"\n" +
                    "• \"Helligkeit auf 70%\"\n" +
                    "• \"Erstelle einen Termin Meeting\"\n" +
                    "• \"Schick Max eine Nachricht\"",
                isUser = false,
                type = MessageType.ERROR,
            ))
            _uiState.update { it.copy(isProcessing = false) }
            return
        }

        // 2. Show the plan
        addMessage(ChatMessage(
            text = "📋 Plan: ${intent.explanation}\n" +
                "Tool: ${intent.toolId}\n" +
                "Konfidenz: ${(intent.confidence * 100).toInt()}%",
            isUser = false,
            toolId = intent.toolId,
            type = MessageType.PLAN,
        ))

        // 3. Check policy
        val context = dev.aios.shell.policy.PolicyContext(
            toolId = intent.toolId,
            riskClass = toolBroker.availableTools()
                .find { it.id == intent.toolId }?.riskClass ?: RiskClass.LOW,
            capabilities = toolBroker.availableTools()
                .find { it.id == intent.toolId }?.capabilities ?: emptyList(),
            sideEffects = toolBroker.availableTools()
                .find { it.id == intent.toolId }?.sideEffects ?: emptyList(),
        )
        val policyResult = policyEngine.evaluate(context)

        addMessage(ChatMessage(
            text = "🛡️ Policy: ${policyResult.decision.name}\n${policyResult.reason}" +
                (policyResult.matchedRule?.let { "\nRegel: $it" } ?: ""),
            isUser = false,
            decision = policyResult.decision,
            type = MessageType.POLICY_RESULT,
        ))

        when (policyResult.decision) {
            Decision.DENY, Decision.QUARANTINE -> {
                addMessage(ChatMessage(
                    text = "❌ Aktion wurde von der Policy Engine blockiert.",
                    isUser = false,
                    type = MessageType.ERROR,
                ))
                _uiState.update { it.copy(isProcessing = false) }
            }
            Decision.REQUIRE_CONFIRMATION -> {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        pendingAction = PendingAction(
                            intent = intent,
                            policyDecision = policyResult.decision,
                            consentRequired = policyResult.consentRequired,
                        ),
                    )
                }
            }
            Decision.ALLOW, Decision.ALLOW_WITH_LOG -> {
                executeAction(intent)
            }
        }
    }

    /// User approves a pending action.
    fun onApprove() {
        val pending = _uiState.value.pendingAction ?: return
        _uiState.update { it.copy(pendingAction = null, isProcessing = true) }
        addMessage(ChatMessage(text = "✅ Freigabe erteilt", isUser = true))
        executeAction(pending.intent)
    }

    /// User denies a pending action.
    fun onDeny() {
        _uiState.update { it.copy(pendingAction = null) }
        addMessage(ChatMessage(
            text = "Aktion abgebrochen.",
            isUser = false,
        ))
    }

    private fun executeAction(intent: ParsedIntent) {
        // 4. Execute via Tool Broker
        val call = ToolCall(toolId = intent.toolId, input = intent.parameters)
        val result = toolBroker.execute(call)

        // 5. Show result
        val message = result.output["message"] as? String ?: "Ausgeführt"
        addMessage(ChatMessage(
            text = "✅ $message\n⏱️ ${result.durationMs}ms | 📝 Audit: ${result.callId.take(8)}…",
            isUser = false,
            toolId = result.toolId,
            type = MessageType.TOOL_RESULT,
        ))

        _uiState.update { it.copy(isProcessing = false) }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { state ->
            state.copy(messages = state.messages + message)
        }
    }
}
