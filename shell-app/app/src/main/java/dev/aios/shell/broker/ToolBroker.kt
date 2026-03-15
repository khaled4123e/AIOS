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

/**
 * Schnittstelle fuer die tatsaechliche Ausfuehrung von System-Tools.
 * Wird vom ToolBroker aufgerufen, sobald die Policy-Pruefung bestanden ist.
 */
interface SystemToolExecutor {
    /**
     * Fuehrt das angegebene Tool aus und gibt das Ergebnis zurueck.
     *
     * @param toolId  Die eindeutige Tool-ID (z.B. "system.settings.set_wifi")
     * @param input   Die Eingabeparameter als Key-Value-Map
     * @return Ergebnis-Map mit Ausgabedaten
     */
    fun execute(toolId: String, input: Map<String, Any>): Map<String, Any>
}

class ToolBroker(
    private val policyEngine: PolicyEngine,
    private var executor: SystemToolExecutor? = null,
) {

    private val registry = mutableMapOf<String, ToolDefinition>()
    private val auditLog = mutableListOf<AuditEntry>()

    init {
        registerBuiltinTools()
    }

    /** Setzt den SystemToolExecutor nachtraeglich (z.B. wenn Android Context verfuegbar ist). */
    fun setExecutor(executor: SystemToolExecutor) {
        this.executor = executor
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
        // Delegate to real executor when available
        executor?.let { exec ->
            return exec.execute(toolId, input)
        }

        // Fallback: Simulation wenn kein Executor gesetzt ist
        return simulateToolAction(toolId, input)
    }

    private fun simulateToolAction(toolId: String, input: Map<String, Any>): Map<String, Any> {
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
            "system.settings.control_volume" -> {
                val level = input["level"] as? Int ?: 50
                val stream = input["stream"] as? String ?: "media"
                mapOf(
                    "current_level" to level,
                    "stream" to stream,
                    "message" to "Lautstaerke (${stream}) auf ${level}% gesetzt"
                )
            }
            "system.settings.set_wifi" -> {
                val enabled = input["enabled"] as? Boolean ?: true
                mapOf(
                    "status" to if (enabled) "enabled" else "disabled",
                    "message" to if (enabled) "WLAN aktiviert" else "WLAN deaktiviert"
                )
            }
            "system.settings.set_bluetooth" -> {
                val enabled = input["enabled"] as? Boolean ?: true
                mapOf(
                    "status" to if (enabled) "enabled" else "disabled",
                    "message" to if (enabled) "Bluetooth aktiviert" else "Bluetooth deaktiviert"
                )
            }
            "system.settings.set_airplane_mode" -> {
                val enabled = input["enabled"] as? Boolean ?: true
                mapOf(
                    "status" to if (enabled) "enabled" else "disabled",
                    "message" to if (enabled) "Flugmodus aktiviert" else "Flugmodus deaktiviert"
                )
            }
            "system.settings.set_rotation" -> {
                val enabled = input["enabled"] as? Boolean ?: true
                mapOf(
                    "status" to if (enabled) "auto" else "locked",
                    "message" to if (enabled) "Automatische Drehung aktiviert" else "Bildschirmdrehung gesperrt"
                )
            }
            "system.settings.set_location" -> {
                val enabled = input["enabled"] as? Boolean ?: true
                mapOf(
                    "status" to if (enabled) "enabled" else "disabled",
                    "message" to if (enabled) "Standortdienste aktiviert" else "Standortdienste deaktiviert"
                )
            }
            "system.settings.get_battery" -> {
                mapOf(
                    "level" to 75,
                    "charging" to false,
                    "message" to "Akkustand: 75% (simuliert)"
                )
            }
            "system.apps.open" -> {
                val packageName = input["package"] as? String ?: "unknown"
                mapOf(
                    "status" to "launched",
                    "message" to "App '${packageName}' gestartet (simuliert)"
                )
            }
            "system.apps.list_installed" -> {
                mapOf(
                    "apps" to listOf("com.example.app1", "com.example.app2"),
                    "message" to "Installierte Apps aufgelistet (simuliert)"
                )
            }
            "system.apps.get_info" -> {
                val packageName = input["package"] as? String ?: "unknown"
                mapOf(
                    "package" to packageName,
                    "version" to "1.0.0",
                    "message" to "App-Info fuer '${packageName}' abgerufen (simuliert)"
                )
            }
            "system.apps.force_stop" -> {
                val packageName = input["package"] as? String ?: "unknown"
                mapOf(
                    "status" to "stopped",
                    "message" to "App '${packageName}' beendet (simuliert)"
                )
            }
            "system.apps.uninstall" -> {
                val packageName = input["package"] as? String ?: "unknown"
                mapOf(
                    "status" to "uninstalled",
                    "message" to "App '${packageName}' deinstalliert (simuliert)"
                )
            }
            "system.phone.call" -> {
                val number = input["number"] as? String ?: "Unbekannt"
                mapOf(
                    "status" to "calling",
                    "message" to "Anruf an ${number} gestartet (simuliert)"
                )
            }
            "system.phone.send_sms" -> {
                val recipient = input["recipient"] as? String ?: "Unbekannt"
                val body = input["body"] as? String ?: ""
                mapOf(
                    "message_id" to UUID.randomUUID().toString(),
                    "status" to "sent",
                    "message" to "SMS an ${recipient} gesendet (simuliert)"
                )
            }
            "system.contacts.search" -> {
                val query = input["query"] as? String ?: ""
                mapOf(
                    "results" to emptyList<Map<String, String>>(),
                    "message" to "Kontaktsuche nach '${query}' ausgefuehrt (simuliert)"
                )
            }
            "system.contacts.add" -> {
                val name = input["name"] as? String ?: "Unbekannt"
                mapOf(
                    "contact_id" to UUID.randomUUID().toString(),
                    "status" to "created",
                    "message" to "Kontakt '${name}' erstellt (simuliert)"
                )
            }
            "system.media.play_pause" -> {
                mapOf(
                    "status" to "toggled",
                    "message" to "Wiedergabe umgeschaltet (simuliert)"
                )
            }
            "system.media.next_track" -> {
                mapOf(
                    "status" to "skipped",
                    "message" to "Naechster Titel (simuliert)"
                )
            }
            "system.media.prev_track" -> {
                mapOf(
                    "status" to "skipped",
                    "message" to "Vorheriger Titel (simuliert)"
                )
            }
            "system.media.take_photo" -> {
                mapOf(
                    "status" to "captured",
                    "path" to "/sdcard/DCIM/simulated_photo.jpg",
                    "message" to "Foto aufgenommen (simuliert)"
                )
            }
            "system.media.take_screenshot" -> {
                mapOf(
                    "status" to "captured",
                    "path" to "/sdcard/Pictures/Screenshots/simulated_screenshot.png",
                    "message" to "Screenshot erstellt (simuliert)"
                )
            }
            "system.calendar.create_event" -> {
                val title = input["title"] as? String ?: "Neuer Termin"
                mapOf(
                    "event_id" to UUID.randomUUID().toString(),
                    "status" to "created",
                    "message" to "Termin '${title}' erstellt (simuliert)"
                )
            }
            "system.calendar.list_events" -> {
                mapOf(
                    "events" to emptyList<Map<String, String>>(),
                    "message" to "Termine aufgelistet (simuliert)"
                )
            }
            "system.alarm.set" -> {
                val time = input["time"] as? String ?: "07:00"
                mapOf(
                    "status" to "set",
                    "time" to time,
                    "message" to "Wecker auf ${time} gestellt (simuliert)"
                )
            }
            "system.alarm.set_timer" -> {
                val seconds = input["seconds"] as? Int ?: 60
                mapOf(
                    "status" to "set",
                    "duration_seconds" to seconds,
                    "message" to "Timer auf ${seconds} Sekunden gestellt (simuliert)"
                )
            }
            "system.device.get_info" -> {
                mapOf(
                    "model" to "Simulated Device",
                    "os_version" to "Android 15",
                    "message" to "Geraeteinformationen abgerufen (simuliert)"
                )
            }
            "system.device.get_network_info" -> {
                mapOf(
                    "type" to "wifi",
                    "ssid" to "SimulatedNetwork",
                    "message" to "Netzwerkinformationen abgerufen (simuliert)"
                )
            }
            "system.device.get_storage" -> {
                mapOf(
                    "total_gb" to 128,
                    "used_gb" to 64,
                    "free_gb" to 64,
                    "message" to "Speicherinformationen abgerufen (simuliert)"
                )
            }
            "system.clipboard.copy" -> {
                val text = input["text"] as? String ?: ""
                mapOf(
                    "status" to "copied",
                    "message" to "Text in Zwischenablage kopiert (simuliert)"
                )
            }
            "system.clipboard.paste" -> {
                mapOf(
                    "content" to "[Simulierter Zwischenablage-Inhalt]",
                    "message" to "Zwischenablage gelesen (simuliert)"
                )
            }
            "system.notifications.list" -> {
                mapOf(
                    "notifications" to emptyList<Map<String, String>>(),
                    "message" to "Benachrichtigungen aufgelistet (simuliert)"
                )
            }
            "system.notifications.dismiss" -> {
                val notificationId = input["id"] as? String ?: "unknown"
                mapOf(
                    "status" to "dismissed",
                    "message" to "Benachrichtigung '${notificationId}' entfernt (simuliert)"
                )
            }
            "system.notifications.dismiss_all" -> {
                mapOf(
                    "status" to "cleared",
                    "message" to "Alle Benachrichtigungen entfernt (simuliert)"
                )
            }
            "system.notifications.send" -> {
                val title = input["title"] as? String ?: "Benachrichtigung"
                mapOf(
                    "notification_id" to UUID.randomUUID().toString(),
                    "status" to "sent",
                    "message" to "Benachrichtigung '${title}' gesendet (simuliert)"
                )
            }
            "system.files.read" -> {
                val path = input["path"] as? String ?: ""
                mapOf(
                    "content" to "[Simulierter Inhalt von ${path}]",
                    "message" to "Datei '${path}' gelesen (simuliert)"
                )
            }
            "system.files.write" -> {
                val path = input["path"] as? String ?: ""
                mapOf(
                    "status" to "written",
                    "message" to "Datei '${path}' geschrieben (simuliert)"
                )
            }
            "system.files.list" -> {
                val path = input["path"] as? String ?: "/"
                mapOf(
                    "files" to emptyList<String>(),
                    "message" to "Verzeichnis '${path}' aufgelistet (simuliert)"
                )
            }
            "system.files.search" -> {
                val query = input["query"] as? String ?: ""
                mapOf(
                    "results" to emptyList<String>(),
                    "message" to "Dateisuche nach '${query}' ausgefuehrt (simuliert)"
                )
            }
            "system.files.delete" -> {
                val path = input["path"] as? String ?: ""
                mapOf(
                    "status" to "deleted",
                    "message" to "Datei '${path}' geloescht (simuliert)"
                )
            }
            "system.files.create_dir" -> {
                val path = input["path"] as? String ?: ""
                mapOf(
                    "status" to "created",
                    "message" to "Verzeichnis '${path}' erstellt (simuliert)"
                )
            }
            "system.shell.execute" -> {
                val command = input["command"] as? String ?: ""
                mapOf(
                    "stdout" to "[Simulierte Ausgabe fuer: ${command}]",
                    "exit_code" to 0,
                    "message" to "Shell-Befehl ausgefuehrt (simuliert)"
                )
            }
            "system.shell.get_prop" -> {
                val prop = input["property"] as? String ?: ""
                mapOf(
                    "value" to "[Simulierter Wert fuer ${prop}]",
                    "message" to "System-Eigenschaft '${prop}' gelesen (simuliert)"
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

    @Suppress("LongMethod")
    private fun registerBuiltinTools() {
        // -- Settings --
        registerTool(ToolDefinition(
            "system.settings.set_focus_mode", "Nicht storen",
            "Aktiviert/deaktiviert Nicht-storen-Modus",
            RiskClass.LOW, listOf("settings.notifications.modify"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.settings.control_brightness", "Helligkeit",
            "Setzt die Bildschirmhelligkeit",
            RiskClass.LOW, listOf("settings.display.modify"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.settings.control_volume", "Lautstaerke",
            "Setzt die Systemlautstaerke",
            RiskClass.LOW, listOf("settings.audio.modify"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.settings.set_wifi", "WLAN",
            "Aktiviert/deaktiviert WLAN",
            RiskClass.MEDIUM, listOf("settings.network.modify"), listOf("modifies_connectivity")
        ))
        registerTool(ToolDefinition(
            "system.settings.set_bluetooth", "Bluetooth",
            "Aktiviert/deaktiviert Bluetooth",
            RiskClass.MEDIUM, listOf("settings.network.modify"), listOf("modifies_connectivity")
        ))
        registerTool(ToolDefinition(
            "system.settings.set_airplane_mode", "Flugmodus",
            "Aktiviert/deaktiviert den Flugmodus",
            RiskClass.MEDIUM, listOf("settings.network.modify"), listOf("modifies_connectivity")
        ))
        registerTool(ToolDefinition(
            "system.settings.set_rotation", "Bildschirmdrehung",
            "Aktiviert/deaktiviert die automatische Bildschirmdrehung",
            RiskClass.LOW, listOf("settings.display.modify"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.settings.set_location", "Standortdienste",
            "Aktiviert/deaktiviert Standortdienste",
            RiskClass.MEDIUM, listOf("settings.location.modify"), listOf("modifies_privacy_settings")
        ))
        registerTool(ToolDefinition(
            "system.settings.get_battery", "Akkustand",
            "Liest den aktuellen Akkustand aus",
            RiskClass.LOW, listOf("device.info.read"), emptyList()
        ))

        // -- Apps --
        registerTool(ToolDefinition(
            "system.apps.open", "App oeffnen",
            "Startet eine installierte App",
            RiskClass.LOW, listOf("apps.launch"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.apps.list_installed", "Installierte Apps",
            "Listet alle installierten Apps auf",
            RiskClass.LOW, listOf("apps.info.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.apps.get_info", "App-Info",
            "Ruft Informationen zu einer App ab",
            RiskClass.LOW, listOf("apps.info.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.apps.force_stop", "App beenden",
            "Erzwingt das Beenden einer App",
            RiskClass.HIGH, listOf("apps.manage"), listOf("kills_process")
        ))
        registerTool(ToolDefinition(
            "system.apps.uninstall", "App deinstallieren",
            "Deinstalliert eine App vom Geraet",
            RiskClass.CRITICAL, listOf("apps.manage"), listOf("removes_app")
        ))

        // -- Communication --
        registerTool(ToolDefinition(
            "system.phone.call", "Anrufen",
            "Startet einen Telefonanruf",
            RiskClass.HIGH, listOf("phone.call"), listOf("initiates_call")
        ))
        registerTool(ToolDefinition(
            "system.phone.send_sms", "SMS senden",
            "Sendet eine SMS-Nachricht",
            RiskClass.HIGH, listOf("messages.sms.send"), listOf("sends_external_communication")
        ))
        registerTool(ToolDefinition(
            "system.contacts.search", "Kontakte suchen",
            "Durchsucht die Kontaktliste",
            RiskClass.LOW, listOf("contacts.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.contacts.add", "Kontakt hinzufuegen",
            "Fuegt einen neuen Kontakt hinzu",
            RiskClass.MEDIUM, listOf("contacts.write"), listOf("modifies_contacts")
        ))

        // -- Media --
        registerTool(ToolDefinition(
            "system.media.play_pause", "Wiedergabe",
            "Startet/pausiert die Medienwiedergabe",
            RiskClass.LOW, listOf("media.playback.control"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.media.next_track", "Naechster Titel",
            "Springt zum naechsten Titel",
            RiskClass.LOW, listOf("media.playback.control"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.media.prev_track", "Vorheriger Titel",
            "Springt zum vorherigen Titel",
            RiskClass.LOW, listOf("media.playback.control"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.media.take_photo", "Foto aufnehmen",
            "Nimmt ein Foto mit der Kamera auf",
            RiskClass.MEDIUM, listOf("camera.capture"), listOf("captures_media")
        ))
        registerTool(ToolDefinition(
            "system.media.take_screenshot", "Screenshot",
            "Erstellt einen Screenshot",
            RiskClass.MEDIUM, listOf("screen.capture"), listOf("captures_screen")
        ))

        // -- Calendar & Alarm --
        registerTool(ToolDefinition(
            "system.calendar.create_event", "Termin erstellen",
            "Erstellt einen neuen Kalendereintrag",
            RiskClass.MEDIUM, listOf("calendar.events.write"), listOf("modifies_calendar")
        ))
        registerTool(ToolDefinition(
            "system.calendar.list_events", "Termine auflisten",
            "Listet anstehende Kalendereintraege auf",
            RiskClass.LOW, listOf("calendar.events.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.alarm.set", "Wecker stellen",
            "Stellt einen Wecker",
            RiskClass.LOW, listOf("alarm.write"), listOf("sets_alarm")
        ))
        registerTool(ToolDefinition(
            "system.alarm.set_timer", "Timer stellen",
            "Stellt einen Countdown-Timer",
            RiskClass.LOW, listOf("alarm.write"), listOf("sets_timer")
        ))

        // -- Device Info --
        registerTool(ToolDefinition(
            "system.device.get_info", "Geraeteinformationen",
            "Liest allgemeine Geraeteinformationen aus",
            RiskClass.LOW, listOf("device.info.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.device.get_network_info", "Netzwerkinformationen",
            "Liest aktuelle Netzwerkinformationen aus",
            RiskClass.LOW, listOf("device.network.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.device.get_storage", "Speicherinformationen",
            "Liest Speicherbelegung des Geraets aus",
            RiskClass.LOW, listOf("device.storage.read"), emptyList()
        ))

        // -- Clipboard --
        registerTool(ToolDefinition(
            "system.clipboard.copy", "Kopieren",
            "Kopiert Text in die Zwischenablage",
            RiskClass.LOW, listOf("clipboard.write"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.clipboard.paste", "Einfuegen",
            "Liest den Inhalt der Zwischenablage",
            RiskClass.MEDIUM, listOf("clipboard.read"), listOf("reads_clipboard")
        ))

        // -- Notifications --
        registerTool(ToolDefinition(
            "system.notifications.list", "Benachrichtigungen auflisten",
            "Listet aktuelle Benachrichtigungen auf",
            RiskClass.LOW, listOf("notifications.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.notifications.dismiss", "Benachrichtigung entfernen",
            "Entfernt eine einzelne Benachrichtigung",
            RiskClass.LOW, listOf("notifications.modify"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.notifications.dismiss_all", "Alle Benachrichtigungen entfernen",
            "Entfernt alle Benachrichtigungen",
            RiskClass.MEDIUM, listOf("notifications.modify"), listOf("clears_all_notifications")
        ))
        registerTool(ToolDefinition(
            "system.notifications.send", "Benachrichtigung senden",
            "Sendet eine lokale Benachrichtigung",
            RiskClass.LOW, listOf("notifications.write"), emptyList()
        ))

        // -- File System --
        registerTool(ToolDefinition(
            "system.files.read", "Datei lesen",
            "Liest eine Datei im Nutzerbereich",
            RiskClass.LOW, listOf("files.user_documents.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.files.write", "Datei schreiben",
            "Schreibt Daten in eine Datei",
            RiskClass.MEDIUM, listOf("files.user_documents.write"), listOf("modifies_filesystem")
        ))
        registerTool(ToolDefinition(
            "system.files.list", "Verzeichnis auflisten",
            "Listet Dateien in einem Verzeichnis auf",
            RiskClass.LOW, listOf("files.user_documents.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.files.search", "Datei suchen",
            "Durchsucht Dateien nach Name oder Inhalt",
            RiskClass.LOW, listOf("files.user_documents.read"), emptyList()
        ))
        registerTool(ToolDefinition(
            "system.files.delete", "Datei loeschen",
            "Loescht eine Datei oder ein Verzeichnis",
            RiskClass.HIGH, listOf("files.user_documents.write"), listOf("deletes_data")
        ))
        registerTool(ToolDefinition(
            "system.files.create_dir", "Verzeichnis erstellen",
            "Erstellt ein neues Verzeichnis",
            RiskClass.LOW, listOf("files.user_documents.write"), emptyList()
        ))

        // -- Shell --
        registerTool(ToolDefinition(
            "system.shell.execute", "Shell-Befehl",
            "Fuehrt einen beliebigen Shell-Befehl aus",
            RiskClass.CRITICAL, listOf("system.shell.execute"), listOf("arbitrary_command")
        ))
        registerTool(ToolDefinition(
            "system.shell.get_prop", "System-Eigenschaft",
            "Liest eine Android-System-Eigenschaft aus",
            RiskClass.LOW, listOf("device.info.read"), emptyList()
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
    val timestamp: Long,
    val durationMs: Long,
)
