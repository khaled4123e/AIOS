// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.planner

import dev.aios.shell.broker.ParsedIntent
import java.util.Calendar

// ---------------------------------------------------------------------------
// Datenmodelle
// ---------------------------------------------------------------------------

data class TaskStep(
    val stepId: Int,
    val toolId: String,
    val parameters: Map<String, Any>,
    val description: String,
    val dependsOn: List<Int> = emptyList(),
    val confidence: Float,
)

data class TaskPlan(
    val steps: List<TaskStep>,
    val explanation: String,
    val isMultiStep: Boolean,
    val estimatedRisk: String, // "low", "medium", "high"
)

// ---------------------------------------------------------------------------
// ToolCapabilityIndex -- kennt alle verfuegbaren Tools und deren Faehigkeiten
// ---------------------------------------------------------------------------

class ToolCapabilityIndex {

    data class ToolCapability(
        val toolId: String,
        val verbs: List<String>,
        val nouns: List<String>,
        val description: String,
        val riskLevel: String = "low",
    )

    private val capabilities: List<ToolCapability> = listOf(
        // ---- Systemeinstellungen ----
        ToolCapability(
            toolId = "system.settings.control_volume",
            verbs = listOf("leise", "laut", "stumm", "stummschalten", "lautstarke", "volume", "mute", "ton"),
            nouns = listOf("lautstarke", "ton", "volume", "lautsprecher", "speaker", "media"),
            description = "Lautstaerke des Geraets steuern",
        ),
        ToolCapability(
            toolId = "system.settings.set_focus_mode",
            verbs = listOf("nicht storen", "fokus", "ruhe", "dnd", "stumm", "stille", "focus"),
            nouns = listOf("nicht storen", "fokus", "ruhemodus", "dnd", "focus"),
            description = "Nicht-Stoeren-Modus aktivieren oder deaktivieren",
        ),
        ToolCapability(
            toolId = "system.settings.control_brightness",
            verbs = listOf("helligkeit", "hell", "dunkel", "dimmen", "aufhellen", "abdunkeln"),
            nouns = listOf("helligkeit", "bildschirm", "display", "screen"),
            description = "Bildschirmhelligkeit anpassen",
        ),
        ToolCapability(
            toolId = "system.settings.toggle_wifi",
            verbs = listOf("wifi", "wlan", "verbinden", "trennen", "netzwerk"),
            nouns = listOf("wifi", "wlan", "internet", "netzwerk", "verbindung"),
            description = "WLAN ein- oder ausschalten",
        ),
        ToolCapability(
            toolId = "system.settings.toggle_bluetooth",
            verbs = listOf("bluetooth", "koppeln", "entkoppeln", "verbinden"),
            nouns = listOf("bluetooth", "bt", "kopfhoerer", "lautsprecher"),
            description = "Bluetooth ein- oder ausschalten",
        ),
        ToolCapability(
            toolId = "system.settings.toggle_mobile_data",
            verbs = listOf("mobilfunk", "mobile daten", "daten"),
            nouns = listOf("mobilfunk", "mobile daten", "mobilnetz", "lte", "5g"),
            description = "Mobile Daten ein- oder ausschalten",
        ),
        ToolCapability(
            toolId = "system.settings.toggle_airplane_mode",
            verbs = listOf("flugmodus", "flugzeugmodus", "airplane"),
            nouns = listOf("flugmodus", "flugzeugmodus", "airplane mode"),
            description = "Flugmodus ein- oder ausschalten",
        ),
        ToolCapability(
            toolId = "system.settings.toggle_location",
            verbs = listOf("standort", "gps", "ortung", "location"),
            nouns = listOf("standort", "gps", "ortung", "location"),
            description = "Standortdienste ein- oder ausschalten",
        ),
        ToolCapability(
            toolId = "system.settings.toggle_flashlight",
            verbs = listOf("taschenlampe", "licht", "flash", "blitz"),
            nouns = listOf("taschenlampe", "flashlight", "licht", "blitz"),
            description = "Taschenlampe ein- oder ausschalten",
        ),
        ToolCapability(
            toolId = "system.settings.toggle_rotation_lock",
            verbs = listOf("drehung", "rotation", "hochformat", "querformat"),
            nouns = listOf("drehung", "rotation", "bildschirmdrehung"),
            description = "Bildschirmrotation sperren oder freigeben",
        ),
        ToolCapability(
            toolId = "system.settings.set_wallpaper",
            verbs = listOf("hintergrundbild", "wallpaper", "hintergrund"),
            nouns = listOf("hintergrundbild", "wallpaper", "hintergrund"),
            description = "Hintergrundbild setzen",
        ),
        ToolCapability(
            toolId = "system.settings.toggle_dark_mode",
            verbs = listOf("dunkel", "dark mode", "nachtmodus", "hell"),
            nouns = listOf("dark mode", "nachtmodus", "dunkelmodus", "design"),
            description = "Dark Mode ein- oder ausschalten",
        ),

        // ---- Kommunikation ----
        ToolCapability(
            toolId = "system.phone.call",
            verbs = listOf("anrufen", "ruf an", "telefonieren", "call", "waehl"),
            nouns = listOf("anruf", "telefon", "call", "nummer"),
            description = "Einen Telefonanruf starten",
            riskLevel = "medium",
        ),
        ToolCapability(
            toolId = "system.messages.send",
            verbs = listOf("nachricht", "schreiben", "senden", "sms", "texten", "schicken", "whatsapp", "message"),
            nouns = listOf("nachricht", "sms", "text", "message", "chat"),
            description = "Eine Textnachricht senden",
            riskLevel = "high",
        ),
        ToolCapability(
            toolId = "system.email.send",
            verbs = listOf("email", "e-mail", "mail", "mailen"),
            nouns = listOf("email", "e-mail", "mail"),
            description = "Eine E-Mail senden",
            riskLevel = "high",
        ),
        ToolCapability(
            toolId = "system.email.read",
            verbs = listOf("email lesen", "mails zeigen", "postfach", "inbox"),
            nouns = listOf("email", "postfach", "inbox", "mails"),
            description = "E-Mails anzeigen oder lesen",
        ),
        ToolCapability(
            toolId = "system.contacts.lookup",
            verbs = listOf("kontakt", "suchen", "finden", "nachschlagen"),
            nouns = listOf("kontakt", "person", "nummer", "adresse"),
            description = "Kontakt im Adressbuch nachschlagen",
        ),

        // ---- Kalender & Termine ----
        ToolCapability(
            toolId = "system.calendar.create_event",
            verbs = listOf("termin", "erstellen", "eintragen", "planen", "meeting"),
            nouns = listOf("termin", "event", "meeting", "kalender", "besprechung"),
            description = "Einen Termin im Kalender erstellen",
            riskLevel = "medium",
        ),
        ToolCapability(
            toolId = "system.calendar.list_events",
            verbs = listOf("termine", "zeigen", "anzeigen", "auflisten", "agenda"),
            nouns = listOf("termine", "kalender", "agenda", "zeitplan", "schedule"),
            description = "Anstehende Termine auflisten",
        ),
        ToolCapability(
            toolId = "system.calendar.delete_event",
            verbs = listOf("termin loeschen", "absagen", "stornieren"),
            nouns = listOf("termin", "event", "meeting"),
            description = "Einen Termin loeschen",
            riskLevel = "medium",
        ),

        // ---- Wecker & Timer ----
        ToolCapability(
            toolId = "system.alarm.set",
            verbs = listOf("wecker", "alarm", "wecken", "aufstehen"),
            nouns = listOf("wecker", "alarm", "weckzeit"),
            description = "Einen Wecker stellen",
        ),
        ToolCapability(
            toolId = "system.alarm.delete",
            verbs = listOf("wecker loeschen", "alarm loeschen", "wecker aus"),
            nouns = listOf("wecker", "alarm"),
            description = "Einen Wecker loeschen",
        ),
        ToolCapability(
            toolId = "system.timer.set",
            verbs = listOf("timer", "countdown", "stoppuhr", "zeit messen"),
            nouns = listOf("timer", "countdown", "stoppuhr", "kurzzeitmesser"),
            description = "Einen Timer starten",
        ),
        ToolCapability(
            toolId = "system.reminder.create",
            verbs = listOf("erinnern", "erinnerung", "reminder", "vergiss nicht"),
            nouns = listOf("erinnerung", "reminder", "notiz"),
            description = "Eine Erinnerung erstellen",
        ),

        // ---- Dateien & Medien ----
        ToolCapability(
            toolId = "system.files.read",
            verbs = listOf("lesen", "oeffnen", "anzeigen", "zeigen"),
            nouns = listOf("datei", "dokument", "file", "pdf", "text"),
            description = "Eine Datei lesen oder anzeigen",
        ),
        ToolCapability(
            toolId = "system.files.write",
            verbs = listOf("schreiben", "speichern", "erstellen", "notieren"),
            nouns = listOf("datei", "dokument", "notiz", "file"),
            description = "Eine Datei schreiben oder speichern",
            riskLevel = "medium",
        ),
        ToolCapability(
            toolId = "system.files.delete",
            verbs = listOf("loeschen", "entfernen"),
            nouns = listOf("datei", "dokument", "file"),
            description = "Eine Datei loeschen",
            riskLevel = "high",
        ),
        ToolCapability(
            toolId = "system.camera.take_photo",
            verbs = listOf("foto", "bild", "knipsen", "fotografieren", "aufnehmen"),
            nouns = listOf("foto", "bild", "kamera", "photo"),
            description = "Ein Foto aufnehmen",
        ),
        ToolCapability(
            toolId = "system.media.play",
            verbs = listOf("abspielen", "spielen", "play", "musik", "hoeren"),
            nouns = listOf("musik", "song", "lied", "podcast", "media", "playlist"),
            description = "Medien abspielen (Musik, Podcast)",
        ),
        ToolCapability(
            toolId = "system.media.pause",
            verbs = listOf("pause", "stoppen", "anhalten", "stop"),
            nouns = listOf("musik", "wiedergabe", "media", "player"),
            description = "Medienwiedergabe pausieren",
        ),
        ToolCapability(
            toolId = "system.media.next_track",
            verbs = listOf("naechstes", "weiter", "skip", "ueberspringen"),
            nouns = listOf("lied", "song", "track", "titel"),
            description = "Zum naechsten Titel springen",
        ),

        // ---- Navigation & Karten ----
        ToolCapability(
            toolId = "system.maps.navigate",
            verbs = listOf("navigieren", "route", "weg", "fahren", "navigation"),
            nouns = listOf("navigation", "route", "karte", "map", "weg", "adresse"),
            description = "Navigation zu einem Ziel starten",
        ),
        ToolCapability(
            toolId = "system.maps.search_nearby",
            verbs = listOf("suchen", "finden", "in der naehe"),
            nouns = listOf("restaurant", "tankstelle", "apotheke", "supermarkt", "ort"),
            description = "Orte in der Naehe suchen",
        ),

        // ---- Apps & System ----
        ToolCapability(
            toolId = "system.apps.launch",
            verbs = listOf("oeffnen", "starten", "app", "ausfuehren"),
            nouns = listOf("app", "anwendung", "programm", "application"),
            description = "Eine App starten",
        ),
        ToolCapability(
            toolId = "system.apps.close",
            verbs = listOf("schliessen", "beenden", "close"),
            nouns = listOf("app", "anwendung", "programm"),
            description = "Eine App schliessen",
        ),
        ToolCapability(
            toolId = "system.screenshot.take",
            verbs = listOf("screenshot", "bildschirmfoto", "aufnehmen"),
            nouns = listOf("screenshot", "bildschirmfoto", "bildschirmaufnahme"),
            description = "Einen Screenshot aufnehmen",
        ),
        ToolCapability(
            toolId = "system.clipboard.copy",
            verbs = listOf("kopieren", "copy", "zwischenablage"),
            nouns = listOf("text", "inhalt", "zwischenablage", "clipboard"),
            description = "Text in die Zwischenablage kopieren",
        ),

        // ---- Web & Suche ----
        ToolCapability(
            toolId = "system.web.search",
            verbs = listOf("suchen", "googlen", "recherchieren", "nachschauen"),
            nouns = listOf("web", "internet", "google", "suche", "ergebnis"),
            description = "Eine Websuche durchfuehren",
        ),
        ToolCapability(
            toolId = "system.web.open_url",
            verbs = listOf("oeffnen", "aufrufen", "besuchen"),
            nouns = listOf("webseite", "url", "link", "seite", "website"),
            description = "Eine URL im Browser oeffnen",
        ),

        // ---- Wetter & Info ----
        ToolCapability(
            toolId = "system.weather.get",
            verbs = listOf("wetter", "temperatur", "regen", "sonne"),
            nouns = listOf("wetter", "temperatur", "vorhersage", "prognose"),
            description = "Aktuelle Wetterdaten abrufen",
        ),
        ToolCapability(
            toolId = "system.battery.status",
            verbs = listOf("akku", "batterie", "battery", "laden"),
            nouns = listOf("akku", "batterie", "battery", "akkustand"),
            description = "Aktuellen Akkustand abfragen",
        ),
    )

    /**
     * Findet das passende Tool fuer eine Aktion und ein Zielobjekt.
     * Gibt die toolId zurueck oder null, wenn kein passendes Tool gefunden wurde.
     */
    fun findToolFor(action: String, target: String): String? {
        val normalizedAction = normalize(action)
        val normalizedTarget = normalize(target)

        // Exakte Uebereinstimmung: Verb UND Noun treffen zu
        val exactMatch = capabilities.find { cap ->
            cap.verbs.any { normalizedAction.contains(normalize(it)) } &&
                cap.nouns.any { normalizedTarget.contains(normalize(it)) }
        }
        if (exactMatch != null) return exactMatch.toolId

        // Teiluebereinstimmung: nur Verb ODER nur Noun
        val verbMatch = capabilities.find { cap ->
            cap.verbs.any { normalizedAction.contains(normalize(it)) }
        }
        if (verbMatch != null) return verbMatch.toolId

        val nounMatch = capabilities.find { cap ->
            cap.nouns.any { normalizedTarget.contains(normalize(it)) }
        }
        return nounMatch?.toolId
    }

    /**
     * Sucht Tools anhand einer Freitext-Anfrage.
     * Bewertet jedes Tool nach Trefferzahl und gibt sortierte Liste zurueck.
     */
    fun findToolsByCapability(query: String): List<ToolCapability> {
        val normalizedQuery = normalize(query)
        val tokens = normalizedQuery.split("\\s+".toRegex()).filter { it.length > 2 }

        data class ScoredCapability(val capability: ToolCapability, val score: Int)

        val scored = capabilities.map { cap ->
            var score = 0
            val allKeywords = cap.verbs + cap.nouns

            for (token in tokens) {
                for (keyword in allKeywords) {
                    if (normalize(keyword).contains(token)) {
                        score++
                    }
                }
                if (normalize(cap.description).contains(token)) {
                    score++
                }
            }
            ScoredCapability(cap, score)
        }

        return scored
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .map { it.capability }
    }

    /**
     * Gibt das Risikolevel fuer eine bestimmte toolId zurueck.
     */
    fun getRiskLevel(toolId: String): String {
        return capabilities.find { it.toolId == toolId }?.riskLevel ?: "low"
    }

    /**
     * Gibt alle registrierten Tool-IDs zurueck.
     */
    fun allToolIds(): List<String> = capabilities.map { it.toolId }

    private fun normalize(input: String): String {
        return input.lowercase()
            .replace("\u00e4", "ae")
            .replace("\u00f6", "oe")
            .replace("\u00fc", "ue")
            .replace("\u00df", "ss")
            .trim()
    }
}

// ---------------------------------------------------------------------------
// TaskPlanner -- zerlegt natuerliche Sprache in ausfuehrbare Schritte
// ---------------------------------------------------------------------------

class TaskPlanner {

    private val capabilityIndex = ToolCapabilityIndex()

    companion object {
        // Trennwoerter fuer zusammengesetzte Befehle
        private val COMPOUND_SEPARATORS = listOf(
            "und dann",
            "und danach",
            "und ausserdem",
            "und aussserdem",
            "danach",
            "ausserdem",
            "dann",
            " und ",
        )
    }

    // -----------------------------------------------------------------------
    // Oeffentliche Schnittstelle
    // -----------------------------------------------------------------------

    /**
     * Erstellt einen TaskPlan aus der Nutzereingabe.
     * Wenn ein ParsedIntent vorliegt, wird dieser als erster Schritt verwendet.
     * Zusammengesetzte Befehle werden automatisch erkannt und aufgeteilt.
     */
    fun plan(input: String, parsedIntent: ParsedIntent?): TaskPlan {
        val subCommands = splitCompoundCommand(input)

        // Einzelbefehl mit bereits geparsetem Intent
        if (subCommands.size <= 1 && parsedIntent != null) {
            val context = detectContext()
            val step = TaskStep(
                stepId = 0,
                toolId = parsedIntent.toolId,
                parameters = enrichParameters(parsedIntent.parameters, context),
                description = parsedIntent.explanation,
                dependsOn = emptyList(),
                confidence = parsedIntent.confidence,
            )
            val contextSteps = suggestContextualSteps(parsedIntent, context, startId = 1)
            val allSteps = listOf(step) + contextSteps

            return TaskPlan(
                steps = allSteps,
                explanation = buildExplanation(allSteps),
                isMultiStep = allSteps.size > 1,
                estimatedRisk = assessOverallRisk(allSteps),
            )
        }

        // Zusammengesetzter Befehl oder kein geparster Intent
        if (subCommands.size > 1) {
            return planCompound(input)
        }

        // Kein Intent vorhanden -- versuche ueber CapabilityIndex zu loesen
        return planFromCapabilities(input)
    }

    /**
     * Verarbeitet zusammengesetzte Befehle, die durch "und", "dann", "danach",
     * "ausserdem" oder "und dann" verbunden sind.
     */
    fun planCompound(input: String): TaskPlan {
        val subCommands = splitCompoundCommand(input)
        val context = detectContext()
        val steps = mutableListOf<TaskStep>()

        for ((index, subCommand) in subCommands.withIndex()) {
            val trimmed = subCommand.trim()
            if (trimmed.isEmpty()) continue

            val step = resolveSubCommand(trimmed, index, context)
            // Sequenzielle Abhaengigkeit: jeder Schritt haengt vom vorherigen ab
            val dependencies = if (index > 0) listOf(index - 1) else emptyList()
            steps.add(step.copy(dependsOn = dependencies))
        }

        if (steps.isEmpty()) {
            return TaskPlan(
                steps = emptyList(),
                explanation = "Konnte keinen ausfuehrbaren Plan erstellen.",
                isMultiStep = false,
                estimatedRisk = "low",
            )
        }

        return TaskPlan(
            steps = steps,
            explanation = buildExplanation(steps),
            isMultiStep = steps.size > 1,
            estimatedRisk = assessOverallRisk(steps),
        )
    }

    // -----------------------------------------------------------------------
    // Private Hilfsmethoden
    // -----------------------------------------------------------------------

    /**
     * Teilt einen zusammengesetzten Befehl an Trennwoertern auf.
     * Erkennt: "und dann", "und danach", "danach", "ausserdem", "dann", "und".
     * Die Reihenfolge der Trennwoerter ist wichtig -- laengere zuerst,
     * damit "und dann" vor "und" erkannt wird.
     */
    private fun splitCompoundCommand(input: String): List<String> {
        val normalized = input.lowercase()
        var workingInput = input
        val placeholder = "\u0000SPLIT\u0000"

        // Ersetze Trennwoerter durch Platzhalter (laengste zuerst)
        for (separator in COMPOUND_SEPARATORS) {
            val regex = Regex("(?i)${Regex.escape(separator)}")
            workingInput = regex.replace(workingInput, placeholder)
        }

        val parts = workingInput.split(placeholder)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (parts.size > 1) parts else listOf(input)
    }

    /**
     * Loest einen einzelnen Teilbefehl ueber den CapabilityIndex auf.
     */
    private fun resolveSubCommand(
        command: String,
        stepIndex: Int,
        context: Map<String, Any>,
    ): TaskStep {
        val normalized = command.lowercase()

        // Spezielle Muster erkennen
        val specialStep = detectSpecialPattern(normalized, stepIndex, context)
        if (specialStep != null) return specialStep

        // Ueber CapabilityIndex suchen
        val matches = capabilityIndex.findToolsByCapability(command)
        if (matches.isNotEmpty()) {
            val best = matches.first()
            val parameters = extractParameters(normalized, best.toolId, context)
            return TaskStep(
                stepId = stepIndex,
                toolId = best.toolId,
                parameters = parameters,
                description = best.description,
                dependsOn = emptyList(),
                confidence = calculateConfidence(normalized, best),
            )
        }

        // Fallback: unbekannter Befehl
        return TaskStep(
            stepId = stepIndex,
            toolId = "system.unknown",
            parameters = mapOf("raw_input" to command),
            description = "Unbekannter Befehl: $command",
            dependsOn = emptyList(),
            confidence = 0.1f,
        )
    }

    /**
     * Erkennt spezielle Befehlsmuster und gibt einen passenden TaskStep zurueck.
     */
    private fun detectSpecialPattern(
        normalized: String,
        stepIndex: Int,
        context: Map<String, Any>,
    ): TaskStep? {
        // Anruf-Muster: "ruf ... an", "anrufen"
        if (normalized.contains("ruf") && normalized.contains("an") ||
            normalized.contains("anrufen") || normalized.contains("telefonier")
        ) {
            val recipient = extractRecipient(normalized)
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.phone.call",
                parameters = mapOf("recipient" to recipient),
                description = "Anruf an $recipient starten",
                confidence = 0.85f,
            )
        }

        // Nachricht-Muster: "schreib ... dass", "sag ... dass"
        if (normalized.contains("schreib") || normalized.contains("sag") ||
            normalized.contains("nachricht") || normalized.contains("sms")
        ) {
            val recipient = extractRecipient(normalized)
            val body = extractMessageBody(normalized)
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.messages.send",
                parameters = mapOf("recipient" to recipient, "body" to body),
                description = "Nachricht an $recipient senden",
                confidence = 0.80f,
            )
        }

        // Lautstaerke-Muster
        if (normalized.contains("leise") || normalized.contains("stumm") ||
            normalized.contains("laut") || normalized.contains("lautstarke") ||
            normalized.contains("lautstärke") || normalized.contains("ton aus")
        ) {
            val level = when {
                normalized.contains("stumm") || normalized.contains("ton aus") -> 0
                normalized.contains("leise") -> 20
                normalized.contains("laut") -> 80
                else -> extractNumber(normalized) ?: 50
            }
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.settings.control_volume",
                parameters = mapOf("level" to level),
                description = "Lautstaerke auf $level% setzen",
                confidence = 0.90f,
            )
        }

        // Wecker-Muster
        if (normalized.contains("wecker") || normalized.contains("weck mich") ||
            normalized.contains("alarm")
        ) {
            val time = extractTime(normalized) ?: suggestAlarmTime(context)
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.alarm.set",
                parameters = mapOf("time" to time),
                description = "Wecker auf $time stellen",
                confidence = 0.85f,
            )
        }

        // Timer-Muster
        if (normalized.contains("timer") || normalized.contains("countdown") ||
            normalized.contains("stoppuhr")
        ) {
            val minutes = extractDuration(normalized) ?: 5
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.timer.set",
                parameters = mapOf("duration_minutes" to minutes),
                description = "Timer auf $minutes Minuten stellen",
                confidence = 0.85f,
            )
        }

        // Nicht-Stoeren-Muster
        if (normalized.contains("nicht stor") || normalized.contains("nicht stör") ||
            normalized.contains("dnd") || normalized.contains("ruhemodus")
        ) {
            val enable = !normalized.contains("aus") && !normalized.contains("deaktiv")
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.settings.set_focus_mode",
                parameters = mapOf("enabled" to enable),
                description = if (enable) "Nicht-Stoeren aktivieren" else "Nicht-Stoeren deaktivieren",
                confidence = 0.90f,
            )
        }

        // Helligkeit-Muster
        if (normalized.contains("helligkeit") || normalized.contains("hell") ||
            normalized.contains("dunkel") || normalized.contains("dimm")
        ) {
            val level = when {
                normalized.contains("dunkel") || normalized.contains("dimm") -> 20
                normalized.contains("hell") && !normalized.contains("helligkeit") -> 80
                else -> extractNumber(normalized) ?: 50
            }
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.settings.control_brightness",
                parameters = mapOf("level" to level),
                description = "Helligkeit auf $level% setzen",
                confidence = 0.85f,
            )
        }

        // Termin-Anzeige-Muster
        if ((normalized.contains("termin") || normalized.contains("kalender") ||
                normalized.contains("agenda")) &&
            (normalized.contains("zeig") || normalized.contains("was") ||
                normalized.contains("liste") || normalized.contains("anzeig"))
        ) {
            val day = when {
                normalized.contains("morgen") -> "morgen"
                normalized.contains("heute") -> "heute"
                normalized.contains("uebermorgen") || normalized.contains("übermorgen") -> "uebermorgen"
                else -> "heute"
            }
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.calendar.list_events",
                parameters = mapOf("day" to day),
                description = "Termine fuer $day anzeigen",
                confidence = 0.85f,
            )
        }

        // Navigation-Muster
        if (normalized.contains("navigier") || normalized.contains("route") ||
            normalized.contains("weg nach") || normalized.contains("fahr")
        ) {
            val destination = extractDestination(normalized)
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.maps.navigate",
                parameters = mapOf("destination" to destination),
                description = "Navigation nach $destination starten",
                confidence = 0.80f,
            )
        }

        // Wetter-Muster
        if (normalized.contains("wetter") || normalized.contains("temperatur") ||
            normalized.contains("regnet") || normalized.contains("regen")
        ) {
            val location = extractAfterKeyword(normalized, listOf("in ", "fuer ", "für ")) ?: "aktueller Standort"
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.weather.get",
                parameters = mapOf("location" to location),
                description = "Wetter fuer $location abrufen",
                confidence = 0.85f,
            )
        }

        // Musik-Muster
        if (normalized.contains("musik") || normalized.contains("spiel") ||
            normalized.contains("abspielen") || normalized.contains("hoer")
        ) {
            val query = extractAfterKeyword(normalized, listOf("spiel ", "spiele ", "hoer ", "höre ")) ?: ""
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.media.play",
                parameters = if (query.isNotBlank()) mapOf("query" to query) else emptyMap(),
                description = if (query.isNotBlank()) "\"$query\" abspielen" else "Musik abspielen",
                confidence = 0.80f,
            )
        }

        // App-Start-Muster
        if (normalized.contains("oeffne") || normalized.contains("öffne") ||
            normalized.contains("starte") || normalized.contains("start ")
        ) {
            val appName = extractAfterKeyword(
                normalized,
                listOf("oeffne ", "öffne ", "starte ", "start "),
            ) ?: "unbekannte App"
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.apps.launch",
                parameters = mapOf("app_name" to appName),
                description = "App \"$appName\" starten",
                confidence = 0.80f,
            )
        }

        // Screenshot-Muster
        if (normalized.contains("screenshot") || normalized.contains("bildschirmfoto")) {
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.screenshot.take",
                parameters = emptyMap(),
                description = "Screenshot aufnehmen",
                confidence = 0.90f,
            )
        }

        // Foto-Muster
        if (normalized.contains("foto") || normalized.contains("bild") &&
            normalized.contains("aufnehm")
        ) {
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.camera.take_photo",
                parameters = emptyMap(),
                description = "Foto aufnehmen",
                confidence = 0.85f,
            )
        }

        // Websuche-Muster
        if (normalized.contains("such") && (normalized.contains("web") ||
                normalized.contains("internet") || normalized.contains("google"))
        ) {
            val query = extractAfterKeyword(normalized, listOf("nach ", "suche ", "google ")) ?: normalized
            return TaskStep(
                stepId = stepIndex,
                toolId = "system.web.search",
                parameters = mapOf("query" to query),
                description = "Websuche nach \"$query\"",
                confidence = 0.80f,
            )
        }

        return null
    }

    /**
     * Erstellt einen Plan ausschliesslich ueber den CapabilityIndex,
     * wenn kein ParsedIntent vorliegt.
     */
    private fun planFromCapabilities(input: String): TaskPlan {
        val context = detectContext()
        val step = resolveSubCommand(input, 0, context)

        val steps = if (step.toolId == "system.unknown") {
            listOf(step)
        } else {
            val contextSteps = suggestContextualStepsForTool(step.toolId, context, startId = 1)
            listOf(step) + contextSteps
        }

        return TaskPlan(
            steps = steps,
            explanation = buildExplanation(steps),
            isMultiStep = steps.size > 1,
            estimatedRisk = assessOverallRisk(steps),
        )
    }

    /**
     * Erkennt Kontext basierend auf Tageszeit, Wochentag usw.
     */
    private fun detectContext(): Map<String, Any> {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val timeOfDay = when {
            hour in 5..8 -> "frueh_morgens"
            hour in 9..11 -> "vormittag"
            hour in 12..13 -> "mittag"
            hour in 14..17 -> "nachmittag"
            hour in 18..21 -> "abend"
            else -> "nacht"
        }

        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

        return mapOf(
            "hour" to hour,
            "time_of_day" to timeOfDay,
            "day_of_week" to dayOfWeek,
            "is_weekend" to isWeekend,
        )
    }

    /**
     * Schlaegt kontextbasierte Zusatzschritte vor, basierend auf dem ParsedIntent.
     * Beispiel: Abends Nicht-Stoeren aktivieren, morgens Wecker vorschlagen.
     */
    private fun suggestContextualSteps(
        intent: ParsedIntent,
        context: Map<String, Any>,
        startId: Int,
    ): List<TaskStep> {
        return suggestContextualStepsForTool(intent.toolId, context, startId)
    }

    /**
     * Schlaegt kontextbasierte Zusatzschritte vor, basierend auf einer toolId.
     */
    private fun suggestContextualStepsForTool(
        toolId: String,
        context: Map<String, Any>,
        startId: Int,
    ): List<TaskStep> {
        val timeOfDay = context["time_of_day"] as? String ?: ""
        val suggestions = mutableListOf<TaskStep>()

        // Abends: wenn Lautstaerke geaendert wird, Nicht-Stoeren vorschlagen
        if (timeOfDay in listOf("abend", "nacht") && toolId == "system.settings.control_volume") {
            suggestions.add(
                TaskStep(
                    stepId = startId,
                    toolId = "system.settings.set_focus_mode",
                    parameters = mapOf("enabled" to true),
                    description = "[Vorschlag] Abends: Nicht-Stoeren-Modus aktivieren",
                    dependsOn = listOf(startId - 1),
                    confidence = 0.5f,
                )
            )
        }

        // Abends: Wecker vorschlagen wenn Nicht-Stoeren aktiviert wird
        if (timeOfDay in listOf("abend", "nacht") && toolId == "system.settings.set_focus_mode") {
            suggestions.add(
                TaskStep(
                    stepId = startId + suggestions.size,
                    toolId = "system.alarm.set",
                    parameters = mapOf("time" to "07:00"),
                    description = "[Vorschlag] Wecker fuer morgen frueh stellen",
                    dependsOn = listOf(startId - 1),
                    confidence = 0.4f,
                )
            )
        }

        // Morgens: Nicht-Stoeren deaktivieren wenn Wecker gestellt wird
        if (timeOfDay == "frueh_morgens" && toolId == "system.alarm.set") {
            suggestions.add(
                TaskStep(
                    stepId = startId + suggestions.size,
                    toolId = "system.settings.set_focus_mode",
                    parameters = mapOf("enabled" to false),
                    description = "[Vorschlag] Morgens: Nicht-Stoeren deaktivieren",
                    dependsOn = listOf(startId - 1),
                    confidence = 0.45f,
                )
            )
        }

        // Navigation: Wetter am Zielort vorschlagen
        if (toolId == "system.maps.navigate") {
            suggestions.add(
                TaskStep(
                    stepId = startId + suggestions.size,
                    toolId = "system.weather.get",
                    parameters = mapOf("location" to "zielort"),
                    description = "[Vorschlag] Wetter am Zielort pruefen",
                    dependsOn = emptyList(),
                    confidence = 0.35f,
                )
            )
        }

        return suggestions
    }

    /**
     * Reichert Parameter mit Kontextinformationen an.
     */
    private fun enrichParameters(
        parameters: Map<String, Any>,
        context: Map<String, Any>,
    ): Map<String, Any> {
        val enriched = parameters.toMutableMap()

        // Zeitkontext hinzufuegen falls nicht vorhanden
        if (!enriched.containsKey("context_time_of_day")) {
            enriched["context_time_of_day"] = context["time_of_day"] ?: "unbekannt"
        }

        return enriched
    }

    /**
     * Extrahiert Parameter aus dem normalisierten Input fuer ein bestimmtes Tool.
     */
    private fun extractParameters(
        normalized: String,
        toolId: String,
        context: Map<String, Any>,
    ): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        params["context_time_of_day"] = context["time_of_day"] ?: "unbekannt"

        when {
            toolId.contains("volume") -> {
                params["level"] = extractNumber(normalized) ?: 50
            }
            toolId.contains("brightness") -> {
                params["level"] = extractNumber(normalized) ?: 50
            }
            toolId.contains("focus") || toolId.contains("dnd") -> {
                val disable = normalized.contains("aus") || normalized.contains("deaktiv")
                params["enabled"] = !disable
            }
            toolId.contains("alarm") -> {
                params["time"] = extractTime(normalized) ?: suggestAlarmTime(context)
            }
            toolId.contains("timer") -> {
                params["duration_minutes"] = extractDuration(normalized) ?: 5
            }
            toolId.contains("call") -> {
                params["recipient"] = extractRecipient(normalized)
            }
            toolId.contains("message") || toolId.contains("send") -> {
                params["recipient"] = extractRecipient(normalized)
                params["body"] = extractMessageBody(normalized)
            }
            toolId.contains("navigate") -> {
                params["destination"] = extractDestination(normalized)
            }
            toolId.contains("weather") -> {
                params["location"] = extractAfterKeyword(normalized, listOf("in ", "fuer ")) ?: "aktueller Standort"
            }
            toolId.contains("launch") -> {
                params["app_name"] = extractAfterKeyword(normalized, listOf("oeffne ", "starte ")) ?: "unbekannt"
            }
            toolId.contains("search") -> {
                params["query"] = extractAfterKeyword(normalized, listOf("nach ", "suche ")) ?: normalized
            }
        }

        return params
    }

    /**
     * Berechnet die Konfidenz fuer einen CapabilityIndex-Treffer.
     */
    private fun calculateConfidence(
        normalized: String,
        capability: ToolCapabilityIndex.ToolCapability,
    ): Float {
        val allKeywords = capability.verbs + capability.nouns
        val tokens = normalized.split("\\s+".toRegex())
        var matches = 0
        var total = tokens.size.coerceAtLeast(1)

        for (token in tokens) {
            if (allKeywords.any { it.contains(token) || token.contains(it) }) {
                matches++
            }
        }

        val ratio = matches.toFloat() / total
        return (ratio * 0.9f).coerceIn(0.1f, 0.95f)
    }

    /**
     * Bewertet das Gesamtrisiko aller Schritte im Plan.
     * Das hoechste Einzelrisiko bestimmt das Gesamtrisiko.
     */
    private fun assessOverallRisk(steps: List<TaskStep>): String {
        if (steps.isEmpty()) return "low"

        val riskLevels = steps.map { capabilityIndex.getRiskLevel(it.toolId) }

        return when {
            riskLevels.any { it == "high" } -> "high"
            riskLevels.any { it == "medium" } -> "medium"
            else -> "low"
        }
    }

    /**
     * Erstellt eine menschenlesbare Erklaerung fuer den gesamten Plan.
     */
    private fun buildExplanation(steps: List<TaskStep>): String {
        if (steps.isEmpty()) return "Kein ausfuehrbarer Plan."
        if (steps.size == 1) return steps.first().description

        val mainSteps = steps.filter { !it.description.startsWith("[Vorschlag]") }
        val suggestions = steps.filter { it.description.startsWith("[Vorschlag]") }

        val builder = StringBuilder()
        builder.append("Plan mit ${mainSteps.size} Schritt(en):")
        for ((i, step) in mainSteps.withIndex()) {
            builder.append("\n  ${i + 1}. ${step.description}")
        }
        if (suggestions.isNotEmpty()) {
            builder.append("\nZusaetzliche Vorschlaege:")
            for (suggestion in suggestions) {
                builder.append("\n  - ${suggestion.description}")
            }
        }
        return builder.toString()
    }

    // -----------------------------------------------------------------------
    // Textextraktions-Hilfsmethoden
    // -----------------------------------------------------------------------

    private fun extractRecipient(input: String): String {
        // Muster: "ruf Mama an", "schreib an Peter", "nachricht an Lisa"
        val afterAn = extractAfterKeyword(input, listOf("an "))
        if (afterAn != null) {
            // Abschneiden bei "und", "dass", "sag"
            val cutoff = afterAn.indexOfFirst { word ->
                listOf("und", "dass", "sag", "schreib", "an").any { afterAn.startsWith(it) }
            }
            val cleaned = afterAn
                .split(" dass ", " und ", " sag ")
                .firstOrNull()
                ?.trim()
                ?: afterAn
            // Entferne "an" am Ende (z.B. "ruf Mama an" -> "Mama")
            return cleaned.removeSuffix(" an").removeSuffix(" an").trim()
                .ifBlank { "Unbekannt" }
        }

        // Muster: "ruf [Name] an"
        val rufPattern = Regex("""ruf\s+(\w+)\s+an""", RegexOption.IGNORE_CASE)
        rufPattern.find(input)?.let {
            return it.groupValues[1]
        }

        return "Unbekannt"
    }

    private fun extractMessageBody(input: String): String {
        val afterDass = extractAfterKeyword(input, listOf("dass ", "das ", ": "))
        if (afterDass != null) return afterDass

        val afterSag = extractAfterKeyword(input, listOf("sag ihr ", "sag ihm ", "sag "))
        if (afterSag != null) return afterSag

        return input
    }

    private fun extractDestination(input: String): String {
        val destination = extractAfterKeyword(
            input,
            listOf("nach ", "zu ", "zum ", "zur ", "richtung ", "navigiere nach ", "navigier nach "),
        )
        return destination?.trim() ?: "unbekanntes Ziel"
    }

    private fun extractTime(input: String): String? {
        // Muster: "6:30", "06:30", "6.30", "6 uhr 30", "6 uhr"
        val timePattern = Regex("""(\d{1,2})[:\.](\d{2})""")
        timePattern.find(input)?.let {
            val h = it.groupValues[1].padStart(2, '0')
            val m = it.groupValues[2].padStart(2, '0')
            return "$h:$m"
        }

        val uhrPattern = Regex("""(\d{1,2})\s*uhr\s*(\d{2})?""")
        uhrPattern.find(input)?.let {
            val h = it.groupValues[1].padStart(2, '0')
            val m = (it.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "00")
                .padStart(2, '0')
            return "$h:$m"
        }

        return null
    }

    private fun suggestAlarmTime(context: Map<String, Any>): String {
        val timeOfDay = context["time_of_day"] as? String ?: ""
        val isWeekend = context["is_weekend"] as? Boolean ?: false

        return when {
            timeOfDay in listOf("abend", "nacht") && isWeekend -> "09:00"
            timeOfDay in listOf("abend", "nacht") -> "07:00"
            else -> "07:00"
        }
    }

    private fun extractDuration(input: String): Int? {
        // Muster: "5 minuten", "10 min", "eine stunde"
        val minutePattern = Regex("""(\d+)\s*(min|minuten|minute)""")
        minutePattern.find(input)?.let {
            return it.groupValues[1].toIntOrNull()
        }

        val hourPattern = Regex("""(\d+)\s*(stunde|stunden|std|h)""")
        hourPattern.find(input)?.let {
            val hours = it.groupValues[1].toIntOrNull() ?: 1
            return hours * 60
        }

        if (input.contains("eine stunde") || input.contains("1 stunde")) return 60
        if (input.contains("halbe stunde")) return 30

        return null
    }

    private fun extractNumber(input: String): Int? {
        val numberPattern = Regex("""(\d+)\s*%?""")
        return numberPattern.find(input)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractAfterKeyword(input: String, keywords: List<String>): String? {
        for (keyword in keywords) {
            val idx = input.indexOf(keyword, ignoreCase = true)
            if (idx >= 0) {
                val after = input.substring(idx + keyword.length).trim()
                if (after.isNotBlank()) return after
            }
        }
        return null
    }
}
