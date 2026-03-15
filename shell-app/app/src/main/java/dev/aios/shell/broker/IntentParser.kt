// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.broker

/// Simple keyword-based Intent Parser for MVP.
/// In production, this will be replaced by a local AI model (Gemma/Phi).
/// Think of it like NLU (Natural Language Understanding).

data class ParsedIntent(
    val toolId: String,
    val parameters: Map<String, Any>,
    val confidence: Float,
    val explanation: String,
)

class IntentParser {

    /// Parse natural language input into a tool call intent.
    fun parse(input: String): ParsedIntent? {
        val lower = input.lowercase().trim()

        return when {
            // Focus mode / Do Not Disturb
            lower.containsAny("nicht stören", "fokus", "ruhe", "do not disturb", "dnd", "focus") -> {
                val disable = lower.containsAny("aus", "deaktiv", "off", "stopp", "beende")
                val until = extractTime(lower)
                ParsedIntent(
                    toolId = "system.settings.set_focus_mode",
                    parameters = buildMap {
                        put("enabled", !disable)
                        if (until != null) put("until", until)
                    },
                    confidence = 0.9f,
                    explanation = if (disable) "Nicht stören deaktivieren" else "Nicht stören aktivieren" +
                        if (until != null) " bis $until" else "",
                )
            }

            // Brightness
            lower.containsAny("helligkeit", "brightness", "bildschirm hell", "display") -> {
                val level = extractNumber(lower) ?: 50
                ParsedIntent(
                    toolId = "system.settings.control_brightness",
                    parameters = mapOf("level" to level),
                    confidence = 0.85f,
                    explanation = "Helligkeit auf $level% setzen",
                )
            }

            // Calendar
            lower.containsAny("termin", "kalender", "calendar", "meeting", "event") -> {
                val title = extractAfter(lower, listOf("termin", "meeting", "event"))
                    ?: "Neuer Termin"
                ParsedIntent(
                    toolId = "system.calendar.create_event",
                    parameters = mapOf(
                        "title" to title,
                        "start_time" to "2026-03-16T10:00:00",
                        "end_time" to "2026-03-16T11:00:00",
                    ),
                    confidence = 0.7f,
                    explanation = "Termin '$title' erstellen",
                )
            }

            // Messages
            lower.containsAny("nachricht", "schreib", "sende", "schick", "message", "sms") -> {
                val recipient = extractAfter(lower, listOf("an", "für", "to")) ?: "Unbekannt"
                val body = extractAfter(lower, listOf("dass", ":", "nachricht")) ?: input
                ParsedIntent(
                    toolId = "system.messages.send",
                    parameters = mapOf("recipient" to recipient, "body" to body),
                    confidence = 0.75f,
                    explanation = "Nachricht an $recipient senden",
                )
            }

            // File reading
            lower.containsAny("datei", "lese", "öffne", "file", "dokument") -> {
                val path = extractAfter(lower, listOf("datei", "file", "dokument")) ?: "unknown"
                ParsedIntent(
                    toolId = "system.files.read",
                    parameters = mapOf("path" to path, "scope" to "documents"),
                    confidence = 0.7f,
                    explanation = "Datei '$path' lesen",
                )
            }

            else -> null
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    private fun extractTime(input: String): String? {
        val timePattern = Regex("""(\d{1,2})[:.:](\d{2})""")
        val match = timePattern.find(input)
        return match?.let {
            val hour = it.groupValues[1].padStart(2, '0')
            val minute = it.groupValues[2].padStart(2, '0')
            "2026-03-15T${hour}:${minute}:00"
        }
    }

    private fun extractNumber(input: String): Int? {
        val numberPattern = Regex("""(\d+)\s*%?""")
        return numberPattern.find(input)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractAfter(input: String, keywords: List<String>): String? {
        for (keyword in keywords) {
            val idx = input.indexOf(keyword)
            if (idx >= 0) {
                val after = input.substring(idx + keyword.length).trim()
                if (after.isNotBlank()) return after
            }
        }
        return null
    }
}
