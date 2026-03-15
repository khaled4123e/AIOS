// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.broker

import dev.aios.shell.policy.PolicyEngine
import dev.aios.shell.policy.PolicyContext
import dev.aios.shell.policy.Decision
import java.util.UUID

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
    val timestamp: Long = System.currentTimeMillis(),
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
        registerBuiltinTools()
    }

    fun registerTool(tool: ToolDefinition) {
        registry[tool.id] = tool
    }

    fun availableTools(): List<ToolDefinition> = registry.values.toList()

    fun execute(call: ToolCall): ToolResult {
        val start = System.currentTimeMillis()

        val tool = registry[call.toolId]
            ?: return ToolResult(
                callId = call.callId,
                toolId = call.toolId,
                output = mapOf("error" to "Tool '${call.toolId}' not found"),
                status = ExecutionStatus.FAILED,
                durationMs = System.currentTimeMillis() - start,
            )

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
                output = mapOf("error" to "Denied: ${policyResult.reason}"),
                status = ExecutionStatus.DENIED,
                durationMs = System.currentTimeMillis() - start,
            )
            logAudit(call, result, policyResult.decision)
            return result
        }

        val output = executeToolAction(tool.id, call.input)

        val result = ToolResult(
            callId = call.callId,
            toolId = call.toolId,
            output = output,
            status = ExecutionStatus.SUCCESS,
            durationMs = System.currentTimeMillis() - start,
        )

        logAudit(call, result, policyResult.decision)
        return result
    }

    fun getAuditLog(): List<AuditEntry> = auditLog.toList()

    private fun executeToolAction(toolId: String, input: Map<String, Any>): Map<String, Any> {
        return when (toolId) {
            "system.settings.set_focus_mode" -> {
                val enabled = input["enabled"] as? Boolean ?: true
                val until = input["until"] as? String
                mapOf(
                    "status" to if (enabled) "activated" else "deactivated",
                    "active_until" to (until ?: "indefinite"),
                    "message" to if (enabled) "Nicht storen aktiviert" else "Nicht storen deaktiviert"
                )
            }
            "system.settings.control_brightness" -> {
                val level = input["level"] as? Int ?: 50
                mapOf(
                    "current_level" to level,
                    "message" to "Helligkeit auf ${level}% gesetzt"
                )
            }
            "system.calendar.create_event" -> {
                val title = input["title"] as? String ?: "Neuer Termin"
                mapOf(
                    "event_id" to UUID.randomUUID().toString(),
                    "status" to "created",
                    "message" to "Termin '${title}' erstellt"
                )
            }
            "system.files.read" -> {
                val path = input["path"] as? String ?: ""
                mapOf(
                    "content" to "[Simulierter Inhalt von ${path}]",
                    "message" to "Datei '${path}' gelesen"
                )
            }
            "system.messages.send" -> {
                val recipient = input["recipient"] as? String ?: "Unbekannt"
                val body = input["body"] as? String ?: ""
                mapOf(
                    "message_id" to UUID.randomUUID().toString(),
                    "status" to "sent",
                    "message" to "Nachricht an ${recipient} gesendet"
                )
            }
            else -> mapOf("message" to "Tool ${toolId} ausgefuehrt (simuliert)")
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
        registerTool(ToolDefinition("system.settings.set_focus_mode", "Nicht storen", "Aktiviert/deaktiviert Nicht-storen-Modus", RiskClass.LOW, listOf("settings.notifications.modify"), listOf("modifies_notification_settings")))
        registerTool(ToolDefinition("system.settings.control_brightness", "Helligkeit", "Setzt die Bildschirmhelligkeit", RiskClass.LOW, listOf("settings.display.modify"), listOf("modifies_display_settings")))
        registerTool(ToolDefinition("system.calendar.create_event", "Termin erstellen", "Erstellt einen neuen Kalendereintrag", RiskClass.MEDIUM, listOf("calendar.events.write"), listOf("modifies_calendar")))
        registerTool(ToolDefinition("system.files.read", "Datei lesen", "Liest eine Datei im Nutzerbereich", RiskClass.LOW, listOf("files.user_documents.read"), emptyList()))
        registerTool(ToolDefinition("system.messages.send", "Nachricht senden", "Sendet eine Textnachricht", RiskClass.HIGH, listOf("messages.sms.send", "contacts.list.read"), listOf("sends_external_communication")))
    }
}

data class AuditEntry(
    val callId: String,
    val toolId: String,
    val input: Map<String, Any>,
    val output: Map<String, Any>,
    val decision: Decision,
    val status: ExecutionStatus,
    val timestamp: Long,
    val durationMs: Long,
)
