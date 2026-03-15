// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.broker

import dev.aios.shell.policy.PolicyEngine
import dev.aios.shell.policy.PolicyContext
import dev.aios.shell.policy.Decision
import java.time.Instant
import java.util.UUID

/// Think of this like a Coordinator/Router in iOS — it routes
/// AI tool calls through validation → policy → execution → audit.

data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val riskClass: RiskClass,
    val capabilities: List<String>,
    val sideEffects: List<String>,
)

enum class RiskClass { LOW, MEDIUM, HIGH, CRITICAL }

data class ToolCall(
    val callId: String = UUID.randomUUID().toString(),
    val toolId: String,
    val input: Map<String, Any>,
    val timestamp: Instant = Instant.now(),
)

data class ToolResult(
    val callId: String,
    val toolId: String,
    val output: Map<String, Any>,
    val status: ExecutionStatus,
    val durationMs: Long,
)

enum class ExecutionStatus { SUCCESS, FAILED, DENIED }

class ToolBroker(private val policyEngine: PolicyEngine) {

    private val registry = mutableMapOf<String, ToolDefinition>()
    private val auditLog = mutableListOf<AuditEntry>()

    init {
        // Register MVP tools
        registerBuiltinTools()
    }

    fun registerTool(tool: ToolDefinition) {
        registry[tool.id] = tool
    }

    fun availableTools(): List<ToolDefinition> = registry.values.toList()

    /// Execute a tool call — the full pipeline.
    fun execute(call: ToolCall): ToolResult {
        val start = System.currentTimeMillis()

        // 1. Find tool in registry
        val tool = registry[call.toolId]
            ?: return ToolResult(
                callId = call.callId,
                toolId = call.toolId,
                output = mapOf("error" to "Tool '${call.toolId}' not found"),
                status = ExecutionStatus.FAILED,
                durationMs = System.currentTimeMillis() - start,
            )

        // 2. Policy check
        val context = PolicyContext(
            toolId = tool.id,
            riskClass = tool.riskClass,
            capabilities = tool.capabilities,
            sideEffects = tool.sideEffects,
        )
        val policyResult = policyEngine.evaluate(context)

        if (policyResult.decision == Decision.DENY) {
            val result = ToolResult(
                callId = call.callId,
                toolId = call.toolId,
                output = mapOf("error" to "Denied by policy: ${policyResult.reason}"),
                status = ExecutionStatus.DENIED,
                durationMs = System.currentTimeMillis() - start,
            )
            logAudit(call, result, policyResult.decision)
            return result
        }

        // 3. Execute tool (simulated for MVP)
        val output = executeToolAction(tool.id, call.input)

        val result = ToolResult(
            callId = call.callId,
            toolId = call.toolId,
            output = output,
            status = ExecutionStatus.SUCCESS,
            durationMs = System.currentTimeMillis() - start,
        )

        // 4. Audit log
        logAudit(call, result, policyResult.decision)

        return result
    }

    fun getAuditLog(): List<AuditEntry> = auditLog.toList()

    private fun executeToolAction(toolId: String, input: Map<String, Any>): Map<String, Any> {
        // MVP: Simulated tool execution
        return when (toolId) {
            "system.settings.set_focus_mode" -> {
                val enabled = input["enabled"] as? Boolean ?: true
                val until = input["until"] as? String
                mapOf(
                    "status" to if (enabled) "activated" else "deactivated",
                    "active_until" to (until ?: "indefinite"),
                    "message" to if (enabled) "Nicht stören wurde aktiviert" else "Nicht stören wurde deaktiviert"
                )
            }
            "system.settings.control_brightness" -> {
                val level = input["level"] as? Int ?: 50
                mapOf(
                    "current_level" to level,
                    "message" to "Helligkeit auf $level% gesetzt"
                )
            }
            "system.calendar.create_event" -> {
                val title = input["title"] as? String ?: "Neuer Termin"
                mapOf(
                    "event_id" to UUID.randomUUID().toString(),
                    "status" to "created",
                    "message" to "Termin '$title' wurde erstellt"
                )
            }
            "system.files.read" -> {
                val path = input["path"] as? String ?: ""
                mapOf(
                    "content" to "[Simulierter Inhalt von $path]",
                    "message" to "Datei '$path' gelesen"
                )
            }
            "system.messages.send" -> {
                val recipient = input["recipient"] as? String ?: "Unbekannt"
                val body = input["body"] as? String ?: ""
                mapOf(
                    "message_id" to UUID.randomUUID().toString(),
                    "status" to "sent",
                    "message" to "Nachricht an $recipient gesendet: \"$body\""
                )
            }
            else -> mapOf("message" to "Tool $toolId ausgeführt (simuliert)")
        }
    }

    private fun logAudit(call: ToolCall, result: ToolResult, decision: Decision) {
        auditLog.add(
            AuditEntry(
                callId = call.callId,
                toolId = call.toolId,
                input = call.input,
                output = result.output,
                decision = decision,
                status = result.status,
                timestamp = call.timestamp,
                durationMs = result.durationMs,
            )
        )
    }

    private fun registerBuiltinTools() {
        registerTool(ToolDefinition(
            id = "system.settings.set_focus_mode",
            name = "Nicht stören",
            description = "Aktiviert/deaktiviert den Nicht-stören-Modus",
            riskClass = RiskClass.LOW,
            capabilities = listOf("settings.notifications.modify"),
            sideEffects = listOf("modifies_notification_settings"),
        ))
        registerTool(ToolDefinition(
            id = "system.settings.control_brightness",
            name = "Helligkeit",
            description = "Setzt die Bildschirmhelligkeit",
            riskClass = RiskClass.LOW,
            capabilities = listOf("settings.display.modify"),
            sideEffects = listOf("modifies_display_settings"),
        ))
        registerTool(ToolDefinition(
            id = "system.calendar.create_event",
            name = "Termin erstellen",
            description = "Erstellt einen neuen Kalendereintrag",
            riskClass = RiskClass.MEDIUM,
            capabilities = listOf("calendar.events.write"),
            sideEffects = listOf("modifies_calendar"),
        ))
        registerTool(ToolDefinition(
            id = "system.files.read",
            name = "Datei lesen",
            description = "Liest eine Datei im Nutzerbereich",
            riskClass = RiskClass.LOW,
            capabilities = listOf("files.user_documents.read"),
            sideEffects = emptyList(),
        ))
        registerTool(ToolDefinition(
            id = "system.messages.send",
            name = "Nachricht senden",
            description = "Sendet eine Textnachricht an einen Kontakt",
            riskClass = RiskClass.HIGH,
            capabilities = listOf("messages.sms.send", "contacts.list.read"),
            sideEffects = listOf("sends_external_communication"),
        ))
    }
}

data class AuditEntry(
    val callId: String,
    val toolId: String,
    val input: Map<String, Any>,
    val output: Map<String, Any>,
    val decision: Decision,
    val status: ExecutionStatus,
    val timestamp: Instant,
    val durationMs: Long,
)
