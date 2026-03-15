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
}

data class PendingAction(
    val intent: ParsedIntent,
    val policyDecision: Decision,
    val consentRequired: String?,
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
)

class ShellViewModel : ViewModel() {

    private val policyEngine = PolicyEngine()
    private val toolBroker = ToolBroker(policyEngine)
    private val intentParser = IntentParser()

    private val _uiState = MutableStateFlow(ShellUiState(
        messages = listOf(
            ChatMessage(
                text = "Hallo! Tippe mich an oder schreib mir, was ich tun soll.",
                isUser = false,
            )
        )
    ))
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

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
                statusText = "Verstehe...",
            )
        }

        val intent = intentParser.parse(text)

        if (intent == null) {
            _uiState.update {
                it.copy(
                    avatarMood = AvatarMood.IDLE,
                    statusText = "Bereit",
                    isProcessing = false,
                )
            }
            addMessage(ChatMessage(
                text = "Das habe ich nicht verstanden. Versuch z.B.:\n" +
                    "- Stelle auf Nicht storen\n" +
                    "- Helligkeit auf 70%\n" +
                    "- Erstelle einen Termin\n" +
                    "- Schick Max eine Nachricht",
                isUser = false,
                type = MessageType.ERROR,
            ))
            return
        }

        addMessage(ChatMessage(
            text = "Plan: ${intent.explanation}\nTool: ${intent.toolId}\nKonfidenz: ${(intent.confidence * 100).toInt()}%",
            isUser = false,
            toolId = intent.toolId,
            type = MessageType.PLAN,
        ))

        _uiState.update { it.copy(statusText = intent.explanation) }

        val tool = toolBroker.availableTools().find { it.id == intent.toolId }
        val context = dev.aios.shell.policy.PolicyContext(
            toolId = intent.toolId,
            riskClass = tool?.riskClass ?: RiskClass.LOW,
            capabilities = tool?.capabilities ?: emptyList(),
            sideEffects = tool?.sideEffects ?: emptyList(),
        )
        val policyResult = policyEngine.evaluate(context)

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
        executeAction(pending.intent)
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
            )
        }
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.update { state ->
            state.copy(messages = state.messages + message)
        }
    }
}
