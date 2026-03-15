// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ai

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Status-Informationen ueber den KI-Manager.
 */
data class AIStatus(
    val activeBackend: BackendType,
    val localModelLoaded: Boolean,
    val localModelName: String,
    val apiConfigured: Boolean,
    val apiType: APIType?,
)

/**
 * Information ueber ein verfuegbares Backend.
 */
data class BackendInfo(
    val type: BackendType,
    val name: String,
    val available: Boolean,
)

/**
 * Zentraler Manager fuer alle KI-Backends.
 *
 * Verwaltet die verschiedenen LLM-Backends (lokal, Remote-Server, externe API)
 * und routet Anfragen an das beste verfuegbare Backend.
 *
 * Prioritaet: EXTERNAL_API > LOCAL > REMOTE_SERVER
 */
class AIManager(private val context: Context) {

    companion object {
        private const val TAG = "AIManager"
    }

    val config = AIConfig(context)
    private val llamaEngine = LlamaEngine(context)
    private val remoteServerEngine = RemoteServerEngine()
    private val externalAPIEngine = ExternalAPIEngine()

    private var preferredBackend: BackendType = config.backendType
    private var initialized = false

    /**
     * Initialisiert alle Backends basierend auf der gespeicherten Konfiguration.
     */
    fun initialize() {
        if (initialized) return
        Log.i(TAG, "Initialisiere KI-Backends...")

        // Konfiguration laden
        preferredBackend = config.backendType

        // Lokales Modell konfigurieren
        val localModel = config.localModelFilename
        if (localModel.isNotBlank()) {
            llamaEngine.setModelFilename(localModel)
        }
        llamaEngine.setGenerationParams(
            contextSize = config.contextSize,
            temp = config.temperature,
        )

        // Remote-Server konfigurieren
        remoteServerEngine.setServerUrl(config.remoteServerUrl)

        // Externe API konfigurieren
        val apiKey = config.apiKey
        if (apiKey.isNotBlank()) {
            externalAPIEngine.configure(
                type = config.apiType,
                key = apiKey,
                model = config.apiModel,
                url = config.customUrl.ifBlank { null },
            )
        }

        // Backends initialisieren (in separaten Threads, um den UI-Thread nicht zu blockieren)
        initialized = true
        Log.i(TAG, "KI-Manager initialisiert. Bevorzugtes Backend: $preferredBackend")
    }

    /**
     * Sendet eine Nachricht an das beste verfuegbare Backend und gibt eine strukturierte Antwort zurueck.
     *
     * @param message Die Nutzernachricht.
     * @param history Bisherige Chat-Nachrichten fuer Kontext.
     * @return AIResponse mit Tool-Schritten oder Konversationsantwort.
     */
    fun chat(message: String, history: List<Map<String, Any>> = emptyList()): AIResponse {
        val start = System.currentTimeMillis()

        // Backend nach Praeferenz oder Verfuegbarkeit waehlen
        val backend = resolveBackend()

        if (backend == null) {
            return keywordFallback(message, start)
        }

        return try {
            // Prompt mit Verlauf aufbauen
            val prompt = buildPromptWithHistory(message, history)

            // Generierung ausfuehren
            val rawResponse = backend.generate(prompt, config.maxTokens)

            // Antwort parsen (JSON-Tool-Aufrufe extrahieren)
            parseResponse(rawResponse, start, backend is RemoteServerEngine)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei KI-Anfrage ueber ${backend.name}: ${e.message}", e)
            fallbackResponse(message, start, "Fehler: ${e.message}")
        }
    }

    /**
     * Gibt eine Liste aller verfuegbaren Backends zurueck.
     */
    fun getAvailableBackends(): List<BackendInfo> {
        return listOf(
            BackendInfo(BackendType.EXTERNAL_API, externalAPIEngine.name, externalAPIEngine.isAvailable),
            BackendInfo(BackendType.LOCAL, llamaEngine.name, llamaEngine.isAvailable),
            BackendInfo(BackendType.REMOTE_SERVER, remoteServerEngine.name, remoteServerEngine.isAvailable),
        )
    }

    /**
     * Setzt das bevorzugte Backend.
     */
    fun setPreferredBackend(type: BackendType) {
        preferredBackend = type
        config.backendType = type
        Log.i(TAG, "Bevorzugtes Backend geaendert: $type")
    }

    /**
     * Konfiguriert die externe API.
     */
    fun configureExternalAPI(type: APIType, apiKey: String, model: String, url: String? = null) {
        config.apiType = type
        config.apiKey = apiKey
        config.apiModel = model
        url?.let { config.customUrl = it }

        externalAPIEngine.configure(type, apiKey, model, url)
        externalAPIEngine.initialize()
        Log.i(TAG, "Externe API konfiguriert: $type ($model)")
    }

    /**
     * Konfiguriert das lokale Modell.
     */
    fun configureLocalModel(modelPath: String) {
        config.localModelFilename = modelPath
        llamaEngine.setModelFilename(modelPath)
        Log.i(TAG, "Lokales Modell konfiguriert: $modelPath")
    }

    /**
     * Gibt den aktuellen Status des KI-Managers zurueck.
     */
    fun getStatus(): AIStatus {
        return AIStatus(
            activeBackend = preferredBackend,
            localModelLoaded = llamaEngine.isAvailable,
            localModelName = if (llamaEngine.isAvailable) llamaEngine.name else "",
            apiConfigured = externalAPIEngine.isAvailable,
            apiType = if (externalAPIEngine.isAvailable) config.apiType else null,
        )
    }

    /**
     * Faehrt alle Backends herunter.
     */
    fun shutdown() {
        llamaEngine.shutdown()
        remoteServerEngine.shutdown()
        externalAPIEngine.shutdown()
        initialized = false
        Log.i(TAG, "Alle Backends heruntergefahren")
    }

    // --- Interne Hilfsmethoden ---

    /**
     * Waehlt das beste verfuegbare Backend basierend auf Praeferenz und Verfuegbarkeit.
     */
    private fun resolveBackend(): LLMBackend? {
        // Zuerst bevorzugtes Backend versuchen
        val preferred = getBackendByType(preferredBackend)
        if (preferred != null && preferred.isAvailable) {
            return preferred
        }

        // Lazy-Initialisierung des bevorzugten Backends
        if (preferred != null && !preferred.isAvailable) {
            if (preferred.initialize()) {
                return preferred
            }
        }

        // Fallback-Reihenfolge: EXTERNAL_API > LOCAL > REMOTE_SERVER
        val fallbackOrder = listOf(BackendType.EXTERNAL_API, BackendType.LOCAL, BackendType.REMOTE_SERVER)
        for (type in fallbackOrder) {
            if (type == preferredBackend) continue
            val backend = getBackendByType(type) ?: continue
            if (backend.isAvailable) return backend
            if (backend.initialize()) return backend
        }

        return null
    }

    private fun getBackendByType(type: BackendType): LLMBackend? {
        return when (type) {
            BackendType.LOCAL -> llamaEngine
            BackendType.EXTERNAL_API -> externalAPIEngine
            BackendType.REMOTE_SERVER -> remoteServerEngine
        }
    }

    /**
     * Baut den Prompt mit Chatverlauf auf.
     */
    private fun buildPromptWithHistory(message: String, history: List<Map<String, Any>>): String {
        val sb = StringBuilder()
        for (msg in history.takeLast(8)) {
            val role = if (msg["isUser"] == true) "Nutzer" else "AIOS"
            sb.append("$role: ${msg["text"] ?: ""}\n")
        }
        sb.append("Nutzer: $message")
        return sb.toString()
    }

    /**
     * Parst die LLM-Antwort und extrahiert strukturierte Tool-Aufrufe.
     * Gleiche Logik wie _parse_response in server.py.
     */
    private fun parseResponse(responseText: String, startTime: Long, isPreParsed: Boolean = false): AIResponse {
        val duration = System.currentTimeMillis() - startTime
        val text = responseText.trim()

        // Wenn die Antwort bereits vom Remote-Server geparsed wurde,
        // koennen wir sie direkt als JSON verarbeiten.
        try {
            val json = if (isPreParsed) {
                JSONObject(text)
            } else {
                // JSON aus der Antwort extrahieren (wie in server.py _parse_response)
                extractJson(text)
            }

            if (json != null) {
                // Pruefen ob "steps" und "message" vorhanden
                if (json.has("steps") && json.has("message")) {
                    return parseStructuredResponse(json, duration)
                }

                // Einzelnes Tool/Action-Format konvertieren
                if (json.has("tool") || json.has("action")) {
                    val tool = json.optString("tool", json.optString("action", ""))
                    val params = json.optJSONObject("params") ?: JSONObject()
                    val paramsMap = jsonObjectToMap(params)

                    return AIResponse(
                        steps = listOf(
                            AIStep(
                                toolId = tool,
                                params = paramsMap,
                                description = json.optString("description", "Ausfuehren: $tool"),
                            )
                        ),
                        message = json.optString("message", "Fuehre $tool aus"),
                        confidence = json.optDouble("confidence", 0.8).toFloat(),
                        durationMs = duration,
                        fromLLM = true,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON-Parsing fehlgeschlagen: ${e.message}")
        }

        // Fallback: als Konversationsantwort zurueckgeben
        val clean = text.split("<|").firstOrNull()?.trim() ?: text
        val responseMessage = clean.ifBlank {
            "Ich habe deine Anfrage erhalten, konnte sie aber nicht verarbeiten."
        }

        return AIResponse(
            steps = emptyList(),
            message = responseMessage.take(500),
            confidence = 0.5f,
            durationMs = duration,
            fromLLM = true,
        )
    }

    /**
     * Extrahiert ein JSON-Objekt aus einem Text (sucht nach { ... }).
     */
    private fun extractJson(text: String): JSONObject? {
        val startIdx = text.indexOf("{")
        if (startIdx < 0) return null

        var depth = 0
        var endIdx = startIdx
        for (i in startIdx until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        endIdx = i + 1
                        break
                    }
                }
            }
        }

        val jsonStr = text.substring(startIdx, endIdx)
        return try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parst eine strukturierte JSON-Antwort mit "steps" und "message".
     */
    private fun parseStructuredResponse(json: JSONObject, duration: Long): AIResponse {
        val stepsArray = json.optJSONArray("steps") ?: JSONArray()
        val steps = mutableListOf<AIStep>()

        for (i in 0 until stepsArray.length()) {
            val stepObj = stepsArray.getJSONObject(i)
            val toolId = stepObj.optString("tool", "")
            if (toolId.isBlank()) continue

            val paramsObj = stepObj.optJSONObject("params") ?: JSONObject()
            val params = jsonObjectToMap(paramsObj)

            steps.add(
                AIStep(
                    toolId = toolId,
                    params = params,
                    description = stepObj.optString("description", "Ausfuehren: $toolId"),
                )
            )
        }

        return AIResponse(
            steps = steps,
            message = json.optString("message", "Ausgefuehrt"),
            confidence = json.optDouble("confidence", 0.8).toFloat(),
            durationMs = duration,
            fromLLM = true,
        )
    }

    /**
     * Konvertiert ein JSONObject in eine Map<String, Any>.
     */
    private fun jsonObjectToMap(jsonObj: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        for (key in jsonObj.keys()) {
            val value = jsonObj.get(key)
            map[key] = when (value) {
                is Boolean -> value
                is Int -> value
                is Long -> value.toInt()
                is Double -> value.toInt()
                is String -> value
                else -> value.toString()
            }
        }
        return map
    }

    /**
     * Keyword-basierter Fallback wenn kein LLM verfuegbar ist.
     */
    private fun keywordFallback(message: String, startTime: Long): AIResponse {
        return AIResponse(
            steps = emptyList(),
            message = "Kein KI-Backend verfuegbar. Bitte konfiguriere ein Backend in den Einstellungen.",
            confidence = 0.0f,
            durationMs = System.currentTimeMillis() - startTime,
            fromLLM = false,
        )
    }

    private fun fallbackResponse(message: String, startTime: Long, error: String): AIResponse {
        return AIResponse(
            steps = emptyList(),
            message = "KI nicht verfuegbar ($error). Nutze Keyword-Erkennung.",
            confidence = 0.0f,
            durationMs = System.currentTimeMillis() - startTime,
            fromLLM = false,
        )
    }
}
