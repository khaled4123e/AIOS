// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.broker

data class ParsedIntent(
    val toolId: String,
    val parameters: Map<String, Any>,
    val confidence: Float,
    val explanation: String,
)

class IntentParser {

    fun parse(input: String): ParsedIntent? {
        val lower = normalize(input)

        return when {
            // ---------------------------------------------------------------
            // 18. Screenshot (before Photo so "bildschirmfoto" is not caught by "foto")
            // ---------------------------------------------------------------
            lower.containsAny("screenshot", "bildschirmfoto") -> {
                ParsedIntent(
                    toolId = "system.media.take_screenshot",
                    parameters = emptyMap(),
                    confidence = 0.95f,
                    explanation = "Screenshot aufnehmen",
                )
            }

            // ---------------------------------------------------------------
            // 3. Volume -- "stumm schalten" / "mute" must come BEFORE Focus/DND
            //    so that "stumm schalten" is not swallowed by the DND branch.
            // ---------------------------------------------------------------
            lower.containsAny(
                "lautstarke", "volume", "leise", "laut schalten", "laut machen",
                "ton ", "sound", "stumm schalten", "mute", "lautsprecher",
            ) || (lower.containsAny("laut") && lower.containsAny("stell", "mach", "setz", "dreh")) -> {
                val mute = lower.containsAny("stumm schalten", "mute", "tonlos")
                val level: Int? = when {
                    mute -> 0
                    lower.containsAny("leise") -> 20
                    lower.containsAny("laut") -> 80
                    else -> extractNumber(lower)
                }
                val stream = when {
                    lower.containsAny("musik", "media", "song") -> "media"
                    lower.containsAny("klingel", "klingelton", "ring") -> "ring"
                    lower.containsAny("wecker", "alarm") -> "alarm"
                    else -> "media"
                }
                ParsedIntent(
                    toolId = "system.settings.control_volume",
                    parameters = buildMap {
                        if (level != null) put("level", level)
                        put("stream", stream)
                        put("mute", mute)
                    },
                    confidence = if (level != null || mute) 0.9f else 0.75f,
                    explanation = when {
                        mute -> "Ton stummschalten"
                        level != null -> "Lautstarke auf ${level}% setzen (${stream})"
                        else -> "Lautstarke anpassen"
                    },
                )
            }

            // ---------------------------------------------------------------
            // 1. Focus / DND (existing)
            // ---------------------------------------------------------------
            lower.containsAny(
                "nicht stor", "fokus", "ruhe", "do not disturb", "dnd",
                "focus", "stille", "stumm",
            ) -> {
                val disable = lower.containsAny("aus", "deaktiv", "off", "stopp", "beende")
                val until = extractTime(lower)
                ParsedIntent(
                    toolId = "system.settings.set_focus_mode",
                    parameters = buildMap {
                        put("enabled", !disable)
                        if (until != null) put("until", until)
                    },
                    confidence = 0.9f,
                    explanation = if (disable) "Nicht storen deaktivieren"
                    else "Nicht storen aktivieren" + if (until != null) " bis $until" else "",
                )
            }

            // ---------------------------------------------------------------
            // 2. Brightness (existing)
            // ---------------------------------------------------------------
            lower.containsAny("helligkeit", "brightness", "bildschirm hell", "display hell", "hell", "dunkel") -> {
                val level = extractNumber(lower) ?: if (lower.contains("dunkel")) 20 else 50
                ParsedIntent(
                    toolId = "system.settings.control_brightness",
                    parameters = mapOf("level" to level),
                    confidence = 0.85f,
                    explanation = "Helligkeit auf ${level}% setzen",
                )
            }

            // ---------------------------------------------------------------
            // 4. WiFi
            // ---------------------------------------------------------------
            lower.containsAny("wifi", "wlan", "w-lan", "wi-fi") ||
                (lower.contains("internet") && lower.containsAny("an", "aus", "ein", "aktiv")) -> {
                val enable = !lower.containsAny("aus", "deaktiv", "off", "abschalt")
                ParsedIntent(
                    toolId = "system.settings.set_wifi",
                    parameters = mapOf("enabled" to enable),
                    confidence = 0.9f,
                    explanation = if (enable) "WLAN aktivieren" else "WLAN deaktivieren",
                )
            }

            // ---------------------------------------------------------------
            // 5. Bluetooth
            // ---------------------------------------------------------------
            lower.containsAny("bluetooth", " bt ") || lower == "bt" ||
                lower.startsWith("bt ") || lower.endsWith(" bt") -> {
                val enable = !lower.containsAny("aus", "deaktiv", "off", "abschalt")
                ParsedIntent(
                    toolId = "system.settings.set_bluetooth",
                    parameters = mapOf("enabled" to enable),
                    confidence = 0.9f,
                    explanation = if (enable) "Bluetooth aktivieren" else "Bluetooth deaktivieren",
                )
            }

            // ---------------------------------------------------------------
            // 6. Airplane Mode
            // ---------------------------------------------------------------
            lower.containsAny("flugmodus", "flugzeugmodus", "airplane") -> {
                val enable = !lower.containsAny("aus", "deaktiv", "off")
                ParsedIntent(
                    toolId = "system.settings.set_airplane_mode",
                    parameters = mapOf("enabled" to enable),
                    confidence = 0.9f,
                    explanation = if (enable) "Flugmodus aktivieren" else "Flugmodus deaktivieren",
                )
            }

            // ---------------------------------------------------------------
            // 26. Rotation
            // ---------------------------------------------------------------
            lower.containsAny("rotation", "querformat", "hochformat", "landscape", "portrait") ||
                (lower.containsAny("drehen", "dreh") && lower.containsAny("bildschirm", "screen", "display")) -> {
                val mode = when {
                    lower.containsAny("querformat", "landscape") -> "landscape"
                    lower.containsAny("hochformat", "portrait") -> "portrait"
                    lower.containsAny("auto") -> "auto"
                    else -> "auto"
                }
                ParsedIntent(
                    toolId = "system.settings.set_rotation",
                    parameters = mapOf("mode" to mode),
                    confidence = 0.85f,
                    explanation = "Bildschirm-Rotation auf '${mode}' setzen",
                )
            }

            // ---------------------------------------------------------------
            // 27. Location / GPS
            // ---------------------------------------------------------------
            lower.containsAny("standort", "gps", "location", "ortung") -> {
                val enable = !lower.containsAny("aus", "deaktiv", "off", "abschalt")
                ParsedIntent(
                    toolId = "system.settings.set_location",
                    parameters = mapOf("enabled" to enable),
                    confidence = 0.85f,
                    explanation = if (enable) "Standortdienste aktivieren" else "Standortdienste deaktivieren",
                )
            }

            // ---------------------------------------------------------------
            // 7. Battery
            // ---------------------------------------------------------------
            lower.containsAny("akku", "batterie", "battery", "ladestand") ||
                (lower.contains("laden") && !lower.containsAny("einlad", "herunter", "runter")) -> {
                ParsedIntent(
                    toolId = "system.settings.get_battery",
                    parameters = emptyMap(),
                    confidence = 0.85f,
                    explanation = "Akkustand abfragen",
                )
            }

            // ---------------------------------------------------------------
            // 19. Device Info
            // ---------------------------------------------------------------
            lower.containsAny("gerat info", "device info", "system info", "welches gerat", "android version") ||
                (lower.containsAny("speicher") && lower.containsAny("info", "wie viel", "frei")) -> {
                ParsedIntent(
                    toolId = "system.device.get_info",
                    parameters = emptyMap(),
                    confidence = 0.8f,
                    explanation = "Gerateinformationen abfragen",
                )
            }

            // ---------------------------------------------------------------
            // 20. Storage
            // ---------------------------------------------------------------
            lower.containsAny("speicherplatz", "storage", "wie viel platz") -> {
                ParsedIntent(
                    toolId = "system.device.get_storage",
                    parameters = emptyMap(),
                    confidence = 0.85f,
                    explanation = "Speicherplatz abfragen",
                )
            }

            // ---------------------------------------------------------------
            // 21. Network Info
            // ---------------------------------------------------------------
            lower.containsAny("netzwerk", "network", "verbindung", "signal") -> {
                ParsedIntent(
                    toolId = "system.device.get_network_info",
                    parameters = emptyMap(),
                    confidence = 0.8f,
                    explanation = "Netzwerkinformationen abfragen",
                )
            }

            // ---------------------------------------------------------------
            // 9. App List (before Open App to avoid "zeig apps" matching "open")
            // ---------------------------------------------------------------
            lower.containsAny("welche apps", "installierte apps", "zeig apps", "app liste", "list apps") -> {
                ParsedIntent(
                    toolId = "system.apps.list_installed",
                    parameters = emptyMap(),
                    confidence = 0.85f,
                    explanation = "Installierte Apps auflisten",
                )
            }

            // ---------------------------------------------------------------
            // 8. Open App
            // ---------------------------------------------------------------
            lower.containsAny("offne", "starte", "start ", "open ", "launch") -> {
                val appName = extractAppName(lower)
                ParsedIntent(
                    toolId = "system.apps.open",
                    parameters = mapOf("app_name" to appName),
                    confidence = if (appName != "unbekannt") 0.9f else 0.65f,
                    explanation = "App '${appName}' offnen",
                )
            }

            // ---------------------------------------------------------------
            // 10. Phone Call
            // ---------------------------------------------------------------
            lower.containsAny("ruf an", "anrufen", "call ", "telefonier", "ruf ", "anruf") -> {
                val contact = extractAfter(lower, listOf(
                    "ruf ", "anrufen ", "call ", "telefonier mit ", "telefonier ",
                )) ?: extractAfter(lower, listOf("bei ")) ?: "Unbekannt"
                // Strip trailing "an" (from "ruf ... an")
                val cleanContact = contact.removeSuffix(" an").trim()
                ParsedIntent(
                    toolId = "system.phone.call",
                    parameters = mapOf("contact" to cleanContact),
                    confidence = 0.85f,
                    explanation = "Anruf an '${cleanContact}' starten",
                )
            }

            // ---------------------------------------------------------------
            // 11. SMS / Message
            // ---------------------------------------------------------------
            lower.containsAny("nachricht", "sms", "schreib", "schick", "sende", "message", "text ") -> {
                val recipient = extractAfter(lower, listOf("an ", "fur ", "fuer ", "to ")) ?: "Unbekannt"
                val body = extractAfter(lower, listOf("dass ", ": ", "nachricht ")) ?: input
                ParsedIntent(
                    toolId = "system.phone.send_sms",
                    parameters = mapOf("recipient" to recipient, "body" to body),
                    confidence = 0.75f,
                    explanation = "SMS an '${recipient}' senden",
                )
            }

            // ---------------------------------------------------------------
            // 12. Contacts
            // ---------------------------------------------------------------
            lower.containsAny("kontakt", "contact") -> {
                val isAdd = lower.containsAny("neuer kontakt", "neuen kontakt", "kontakt hinzufug",
                    "kontakt erstell", "add contact", "new contact", "kontakt anlegen")
                if (isAdd) {
                    val name = extractAfter(lower, listOf(
                        "kontakt ", "contact ",
                    )) ?: "Unbekannt"
                    ParsedIntent(
                        toolId = "system.contacts.add",
                        parameters = mapOf("name" to name),
                        confidence = 0.8f,
                        explanation = "Neuen Kontakt '${name}' hinzufugen",
                    )
                } else {
                    val query = extractAfter(lower, listOf(
                        "kontakt ", "contact ", "suche ", "finde ",
                    )) ?: ""
                    ParsedIntent(
                        toolId = "system.contacts.search",
                        parameters = mapOf("query" to query),
                        confidence = 0.8f,
                        explanation = if (query.isNotBlank()) "Kontakt '${query}' suchen"
                        else "Kontakte durchsuchen",
                    )
                }
            }

            // ---------------------------------------------------------------
            // 15. Timer (before Alarm so "timer" is not missed)
            // ---------------------------------------------------------------
            lower.containsAny("timer", "stoppuhr", "countdown") -> {
                val duration = extractDuration(lower)
                ParsedIntent(
                    toolId = "system.alarm.set_timer",
                    parameters = buildMap {
                        put("duration_seconds", duration)
                    },
                    confidence = if (duration > 0) 0.9f else 0.7f,
                    explanation = if (duration > 0) "Timer auf ${duration} Sekunden setzen"
                    else "Timer starten",
                )
            }

            // ---------------------------------------------------------------
            // 14. Alarm
            // ---------------------------------------------------------------
            lower.containsAny("wecker", "alarm", "weck mich") -> {
                val time = extractTime(lower)
                ParsedIntent(
                    toolId = "system.alarm.set",
                    parameters = buildMap {
                        if (time != null) put("time", time)
                    },
                    confidence = if (time != null) 0.9f else 0.7f,
                    explanation = if (time != null) "Wecker auf ${time} gestellt"
                    else "Wecker stellen",
                )
            }

            // ---------------------------------------------------------------
            // 13. Calendar
            // ---------------------------------------------------------------
            lower.containsAny("termin", "kalender", "calendar", "meeting", "event", "erinnerung") -> {
                val isList = lower.containsAny(
                    "zeig termin", "welche termin", "nachster termin", "alle termin",
                    "list event", "show event", "show calendar", "kalender zeig",
                    "was steht an", "habe ich termin",
                )
                if (isList) {
                    ParsedIntent(
                        toolId = "system.calendar.list_events",
                        parameters = emptyMap(),
                        confidence = 0.85f,
                        explanation = "Termine anzeigen",
                    )
                } else {
                    val title = extractAfter(lower, listOf(
                        "termin ", "meeting ", "event ", "erinnerung ",
                    )) ?: "Neuer Termin"
                    val time = extractTime(lower)
                    ParsedIntent(
                        toolId = "system.calendar.create_event",
                        parameters = buildMap {
                            put("title", title)
                            if (time != null) {
                                put("start_time", time)
                            } else {
                                put("start_time", "2026-03-16T10:00:00")
                            }
                        },
                        confidence = 0.7f,
                        explanation = "Termin '${title}' erstellen",
                    )
                }
            }

            // ---------------------------------------------------------------
            // 16. Media Control
            // ---------------------------------------------------------------
            lower.containsAny(
                "musik", "play", "pause", "stopp", "abspielen", "weiter",
                "nachster", "vorheriger", "zuruck", "skip", "song",
            ) -> {
                val toolId: String
                val explanation: String
                when {
                    lower.containsAny("nachster", "skip", "weiter", "nachster song", "next") -> {
                        toolId = "system.media.next_track"
                        explanation = "Nachster Titel"
                    }
                    lower.containsAny("vorheriger", "zuruck", "previous", "letzter song") -> {
                        toolId = "system.media.prev_track"
                        explanation = "Vorheriger Titel"
                    }
                    else -> {
                        toolId = "system.media.play_pause"
                        explanation = "Wiedergabe umschalten"
                    }
                }
                ParsedIntent(
                    toolId = toolId,
                    parameters = emptyMap(),
                    confidence = 0.85f,
                    explanation = explanation,
                )
            }

            // ---------------------------------------------------------------
            // 17. Photo / Camera
            // ---------------------------------------------------------------
            lower.containsAny("foto", "photo", "kamera", "camera", "bild machen", "bild aufnehm") -> {
                ParsedIntent(
                    toolId = "system.media.take_photo",
                    parameters = emptyMap(),
                    confidence = 0.9f,
                    explanation = "Foto aufnehmen",
                )
            }

            // ---------------------------------------------------------------
            // 22. Clipboard
            // ---------------------------------------------------------------
            lower.containsAny("zwischenablage", "clipboard") ||
                (lower.containsAny("kopier", "copy") && !lower.containsAny("datei", "file")) ||
                lower.containsAny("einfug", "paste") -> {
                val isPaste = lower.containsAny("einfug", "paste")
                if (isPaste) {
                    ParsedIntent(
                        toolId = "system.clipboard.paste",
                        parameters = emptyMap(),
                        confidence = 0.85f,
                        explanation = "Aus Zwischenablage einfugen",
                    )
                } else {
                    val text = extractAfter(lower, listOf("kopier ", "copy ")) ?: ""
                    ParsedIntent(
                        toolId = "system.clipboard.copy",
                        parameters = mapOf("text" to text),
                        confidence = 0.8f,
                        explanation = "In Zwischenablage kopieren",
                    )
                }
            }

            // ---------------------------------------------------------------
            // 23. Notifications
            // ---------------------------------------------------------------
            lower.containsAny("benachrichtigung", "notification", "mitteilung") -> {
                val dismiss = lower.containsAny("losch", "entfern", "alle weg", "dismiss", "clear")
                if (dismiss) {
                    ParsedIntent(
                        toolId = "system.notifications.dismiss_all",
                        parameters = emptyMap(),
                        confidence = 0.85f,
                        explanation = "Alle Benachrichtigungen entfernen",
                    )
                } else {
                    ParsedIntent(
                        toolId = "system.notifications.list",
                        parameters = emptyMap(),
                        confidence = 0.85f,
                        explanation = "Benachrichtigungen anzeigen",
                    )
                }
            }

            // ---------------------------------------------------------------
            // 25. Shell Command (before File Operations so "befehl" is caught)
            // ---------------------------------------------------------------
            lower.containsAny("befehl", "command", "terminal", "shell", "ausfuhr") -> {
                val cmd = extractAfter(lower, listOf(
                    "befehl ", "command ", "ausfuhr ", "shell ",
                )) ?: ""
                ParsedIntent(
                    toolId = "system.shell.execute",
                    parameters = mapOf("command" to cmd),
                    confidence = if (cmd.isNotBlank()) 0.85f else 0.6f,
                    explanation = if (cmd.isNotBlank()) "Shell-Befehl '${cmd}' ausfuhren"
                    else "Shell-Befehl ausfuhren",
                )
            }

            // ---------------------------------------------------------------
            // 24. File Operations
            // ---------------------------------------------------------------
            lower.containsAny("datei", "file", "dokument", "ordner", "verzeichnis") -> {
                val toolId: String
                val params: Map<String, Any>
                val explanation: String
                val confidence: Float

                when {
                    lower.containsAny("ordner erstell", "verzeichnis erstell", "mkdir",
                        "ordner anlegen", "neuen ordner", "neues verzeichnis") -> {
                        val dirName = extractAfter(lower, listOf(
                            "ordner ", "verzeichnis ",
                        )) ?: "new_folder"
                        toolId = "system.files.create_dir"
                        params = mapOf("path" to dirName, "scope" to "documents")
                        explanation = "Ordner '${dirName}' erstellen"
                        confidence = 0.8f
                    }
                    lower.containsAny("losche datei", "datei losch", "file delet",
                        "entferne datei", "datei entfern") -> {
                        val path = extractAfter(lower, listOf(
                            "datei ", "file ",
                        )) ?: "unknown"
                        toolId = "system.files.delete"
                        params = mapOf("path" to path, "scope" to "documents")
                        explanation = "Datei '${path}' loschen"
                        confidence = 0.8f
                    }
                    lower.containsAny("suche datei", "finde datei", "datei such",
                        "datei find", "search file", "find file") -> {
                        val query = extractAfter(lower, listOf(
                            "datei ", "file ", "suche ", "finde ",
                        )) ?: ""
                        toolId = "system.files.search"
                        params = mapOf("query" to query, "scope" to "documents")
                        explanation = "Datei suchen: '${query}'"
                        confidence = 0.75f
                    }
                    lower.containsAny("schreib datei", "datei schreib", "erstelle datei",
                        "datei erstell", "write file", "create file") -> {
                        val path = extractAfter(lower, listOf(
                            "datei ", "file ",
                        )) ?: "unknown"
                        toolId = "system.files.write"
                        params = mapOf("path" to path, "content" to "", "scope" to "documents")
                        explanation = "Datei '${path}' schreiben"
                        confidence = 0.75f
                    }
                    lower.containsAny("zeig datei", "liste datei", "datei zeig",
                        "datei list", "list file", "show file") -> {
                        val path = extractAfter(lower, listOf(
                            "datei ", "file ", "ordner ", "verzeichnis ",
                        )) ?: "."
                        toolId = "system.files.list"
                        params = mapOf("path" to path, "scope" to "documents")
                        explanation = "Dateien in '${path}' auflisten"
                        confidence = 0.75f
                    }
                    else -> {
                        // Default: read
                        val path = extractAfter(lower, listOf(
                            "datei ", "file ", "dokument ",
                        )) ?: "unknown"
                        toolId = "system.files.read"
                        params = mapOf("path" to path, "scope" to "documents")
                        explanation = "Datei '${path}' lesen"
                        confidence = 0.7f
                    }
                }
                ParsedIntent(
                    toolId = toolId,
                    parameters = params,
                    confidence = confidence,
                    explanation = explanation,
                )
            }

            // ---------------------------------------------------------------
            // No match
            // ---------------------------------------------------------------
            else -> null
        }
    }

    // -------------------------------------------------------------------
    // Helper: Normalize input -- lowercase + flatten umlauts
    // -------------------------------------------------------------------
    private fun normalize(input: String): String {
        return input.lowercase()
            .replace("ae", "a").replace("oe", "o").replace("ue", "u")
            .replace("\u00e4", "a")   // ae
            .replace("\u00f6", "o")   // oe
            .replace("\u00fc", "u")   // ue
            .replace("\u00df", "ss")  // ss
            .trim()
    }

    // -------------------------------------------------------------------
    // Helper: Check if string contains any of the given keywords
    // -------------------------------------------------------------------
    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    // -------------------------------------------------------------------
    // Helper: Extract a time pattern (HH:MM) and return ISO timestamp
    // -------------------------------------------------------------------
    private fun extractTime(input: String): String? {
        val timePattern = Regex("""(\d{1,2})(?::|\.| uhr )(\d{2})""")
        val match = timePattern.find(input)
        return match?.let {
            val hour = it.groupValues[1].padStart(2, '0')
            val minute = it.groupValues[2].padStart(2, '0')
            "2026-03-15T${hour}:${minute}:00"
        }
    }

    // -------------------------------------------------------------------
    // Helper: Extract the first integer from input (optionally with %)
    // -------------------------------------------------------------------
    private fun extractNumber(input: String): Int? {
        val numberPattern = Regex("""(\d+)\s*%?""")
        return numberPattern.find(input)?.groupValues?.get(1)?.toIntOrNull()
    }

    // -------------------------------------------------------------------
    // Helper: Extract text that appears after one of the given keywords
    // -------------------------------------------------------------------
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

    // -------------------------------------------------------------------
    // Helper: Extract duration in seconds from input
    //   Supports patterns like "5 minuten", "30 sekunden", "1 stunde",
    //   "5 min", "90s", "2h", "5 minutes", "30 seconds"
    // -------------------------------------------------------------------
    private fun extractDuration(input: String): Int {
        // Try "X stunde(n)" / "X hour(s)" / "Xh"
        val hourPattern = Regex("""(\d+)\s*(?:stunde|hour|h)\b""")
        val hourMatch = hourPattern.find(input)
        if (hourMatch != null) {
            return (hourMatch.groupValues[1].toIntOrNull() ?: 0) * 3600
        }

        // Try "X minute(n)" / "X min" / "Xm"
        val minPattern = Regex("""(\d+)\s*(?:minute|min|m)\b""")
        val minMatch = minPattern.find(input)
        if (minMatch != null) {
            return (minMatch.groupValues[1].toIntOrNull() ?: 0) * 60
        }

        // Try "X sekunde(n)" / "X second(s)" / "Xs" / "X sek"
        val secPattern = Regex("""(\d+)\s*(?:sekunde|second|sek|s)\b""")
        val secMatch = secPattern.find(input)
        if (secMatch != null) {
            return secMatch.groupValues[1].toIntOrNull() ?: 0
        }

        // Bare number -- assume minutes
        val bareNumber = extractNumber(input)
        if (bareNumber != null) {
            return bareNumber * 60
        }

        return 0
    }

    // -------------------------------------------------------------------
    // Helper: Extract app name from command
    //   Strips the action keyword and returns the remainder.
    // -------------------------------------------------------------------
    private fun extractAppName(input: String): String {
        val name = extractAfter(input, listOf(
            "offne ", "starte ", "start ", "open ", "launch ",
        ))
        // Clean up common filler words
        return name
            ?.removePrefix("die app ")
            ?.removePrefix("app ")
            ?.removePrefix("the app ")
            ?.removePrefix("die ")
            ?.removePrefix("the ")
            ?.trim()
            ?: "unbekannt"
    }
}
