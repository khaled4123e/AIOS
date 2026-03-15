// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ai

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Unterstuetzte externe API-Typen.
 */
enum class APIType {
    CLAUDE,
    OPENAI,
    CUSTOM,
}

/**
 * LLM-Backend fuer externe APIs (Claude, OpenAI, benutzerdefiniert).
 *
 * Unterstuetzt verschiedene API-Formate und sendet den AIOS-System-Prompt
 * bei jeder Anfrage mit.
 */
class ExternalAPIEngine(
    private var apiType: APIType = APIType.CLAUDE,
    private var apiKey: String = "",
    private var modelName: String = "",
    private var customUrl: String = "",
) : LLMBackend {

    companion object {
        private const val TAG = "ExternalAPIEngine"
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 60000
    }

    private var _isAvailable: Boolean = false

    override val name: String
        get() = when (apiType) {
            APIType.CLAUDE -> "Claude API ($modelName)"
            APIType.OPENAI -> "OpenAI API ($modelName)"
            APIType.CUSTOM -> "Benutzerdefinierte API"
        }

    override val isAvailable: Boolean
        get() = _isAvailable

    /**
     * Prueft, ob die API-Konfiguration gueltig ist.
     */
    override fun initialize(): Boolean {
        if (apiKey.isBlank() && apiType != APIType.CUSTOM) {
            Log.w(TAG, "Kein API-Schluessel konfiguriert fuer $apiType")
            _isAvailable = false
            return false
        }

        if (apiType == APIType.CUSTOM && customUrl.isBlank()) {
            Log.w(TAG, "Keine benutzerdefinierte URL konfiguriert")
            _isAvailable = false
            return false
        }

        if (modelName.isBlank() && apiType != APIType.CUSTOM) {
            // Standardmodelle setzen
            modelName = when (apiType) {
                APIType.CLAUDE -> "claude-sonnet-4-20250514"
                APIType.OPENAI -> "gpt-4o"
                APIType.CUSTOM -> ""
            }
        }

        _isAvailable = true
        Log.i(TAG, "Externe API initialisiert: $name")
        return true
    }

    /**
     * Sendet den Prompt an die konfigurierte externe API.
     */
    override fun generate(prompt: String, maxTokens: Int): String {
        if (!_isAvailable) {
            return """{"steps":[],"message":"Externe API nicht konfiguriert.","confidence":0.0}"""
        }

        return try {
            when (apiType) {
                APIType.CLAUDE -> generateClaude(prompt, maxTokens)
                APIType.OPENAI -> generateOpenAI(prompt, maxTokens)
                APIType.CUSTOM -> generateCustom(prompt, maxTokens)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei API-Anfrage ($apiType): ${e.message}", e)
            """{"steps":[],"message":"API-Fehler: ${e.message}","confidence":0.0}"""
        }
    }

    override fun shutdown() {
        _isAvailable = false
        Log.i(TAG, "Externe API heruntergefahren: $name")
    }

    /**
     * Konfiguriert die API-Parameter.
     */
    fun configure(type: APIType, key: String, model: String, url: String? = null) {
        apiType = type
        apiKey = key
        modelName = model
        url?.let { customUrl = it }
    }

    // --- Claude API ---

    private fun generateClaude(prompt: String, maxTokens: Int): String {
        val url = URL(CLAUDE_API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-api-key", apiKey)
        conn.setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
        conn.doOutput = true

        val body = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", maxTokens)
            put("system", SystemPrompt.AIOS_SYSTEM_PROMPT)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(body.toString())
        writer.flush()
        writer.close()

        if (conn.responseCode != 200) {
            val errorStream = conn.errorStream
            val errorBody = if (errorStream != null) {
                BufferedReader(InputStreamReader(errorStream)).readText()
            } else {
                "Unbekannter Fehler"
            }
            conn.disconnect()
            Log.e(TAG, "Claude API Fehler ${conn.responseCode}: $errorBody")
            return """{"steps":[],"message":"Claude API Fehler: ${conn.responseCode}","confidence":0.0}"""
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        conn.disconnect()

        // Antwort parsen: response.content[0].text
        val json = JSONObject(response)
        val content = json.getJSONArray("content")
        if (content.length() > 0) {
            return content.getJSONObject(0).getString("text")
        }

        return """{"steps":[],"message":"Leere Antwort von Claude API.","confidence":0.0}"""
    }

    // --- OpenAI API ---

    private fun generateOpenAI(prompt: String, maxTokens: Int): String {
        val url = URL(OPENAI_API_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SystemPrompt.AIOS_SYSTEM_PROMPT)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val body = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", maxTokens)
            put("messages", messages)
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(body.toString())
        writer.flush()
        writer.close()

        if (conn.responseCode != 200) {
            val errorStream = conn.errorStream
            val errorBody = if (errorStream != null) {
                BufferedReader(InputStreamReader(errorStream)).readText()
            } else {
                "Unbekannter Fehler"
            }
            conn.disconnect()
            Log.e(TAG, "OpenAI API Fehler ${conn.responseCode}: $errorBody")
            return """{"steps":[],"message":"OpenAI API Fehler: ${conn.responseCode}","confidence":0.0}"""
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        conn.disconnect()

        // Antwort parsen: response.choices[0].message.content
        val json = JSONObject(response)
        val choices = json.getJSONArray("choices")
        if (choices.length() > 0) {
            return choices.getJSONObject(0).getJSONObject("message").getString("content")
        }

        return """{"steps":[],"message":"Leere Antwort von OpenAI API.","confidence":0.0}"""
    }

    // --- Benutzerdefinierte API (gleiches Format wie MLX-Server) ---

    private fun generateCustom(prompt: String, maxTokens: Int): String {
        val url = URL("$customUrl/v1/chat")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        conn.doOutput = true

        val body = JSONObject().apply {
            put("message", prompt)
            put("history", JSONArray())
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(body.toString())
        writer.flush()
        writer.close()

        if (conn.responseCode != 200) {
            conn.disconnect()
            return """{"steps":[],"message":"Benutzerdefinierte API Fehler: ${conn.responseCode}","confidence":0.0}"""
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        conn.disconnect()

        return response
    }
}
