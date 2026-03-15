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
 * Remote-Server-Backend fuer den AIOS MLX LLM-Server.
 *
 * Verbindet sich mit dem auf dem Host laufenden MLX-Server.
 * Im Emulator ist der Host ueber 10.0.2.2 erreichbar.
 * Auf einem echten Geraet muss die Host-IP manuell konfiguriert werden.
 */
class RemoteServerEngine(
    private var serverUrl: String = "http://10.0.2.2:8085",
) : LLMBackend {

    companion object {
        private const val TAG = "RemoteServerEngine"
    }

    private var _isAvailable: Boolean = false

    override val name: String
        get() = "Remote MLX Server ($serverUrl)"

    override val isAvailable: Boolean
        get() = _isAvailable

    /**
     * Prueft ob der LLM-Server erreichbar ist. Versucht es bis zu 3 Mal.
     */
    override fun initialize(): Boolean {
        for (attempt in 1..3) {
            try {
                val url = URL("$serverUrl/v1/health")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code == 200) {
                    _isAvailable = true
                    Log.i(TAG, "Server erreichbar: $serverUrl")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Server nicht erreichbar (Versuch $attempt/3): ${e.message}")
                try {
                    Thread.sleep(2000)
                } catch (_: Exception) {
                }
            }
        }
        _isAvailable = false
        return false
    }

    /**
     * Sendet einen Prompt an den Remote-Server.
     * Der Server wendet den System-Prompt selbst an (server.py).
     */
    override fun generate(prompt: String, maxTokens: Int): String {
        val url = URL("$serverUrl/v1/chat")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 30000
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val requestBody = JSONObject().apply {
            put("message", prompt)
            put("history", JSONArray())
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(requestBody.toString())
        writer.flush()
        writer.close()

        if (conn.responseCode != 200) {
            conn.disconnect()
            return """{"steps":[],"message":"Server-Fehler: ${conn.responseCode}","confidence":0.0}"""
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        conn.disconnect()

        return response
    }

    override fun shutdown() {
        _isAvailable = false
        Log.i(TAG, "Remote-Server-Verbindung getrennt")
    }

    /**
     * Setzt die Server-URL.
     */
    fun setServerUrl(url: String) {
        serverUrl = url
        _isAvailable = false
    }

    /**
     * Sendet eine Chat-Nachricht mit Verlauf an den Server.
     * Behaelt die urspruengliche Funktionalitaet von AIEngine.chat() bei.
     */
    fun chatWithHistory(message: String, history: List<Map<String, Any>> = emptyList()): String {
        val url = URL("$serverUrl/v1/chat")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 30000
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val requestBody = JSONObject()
        requestBody.put("message", message)

        val historyArray = JSONArray()
        for (msg in history.takeLast(8)) {
            val msgObj = JSONObject()
            msgObj.put("text", msg["text"] ?: "")
            msgObj.put("isUser", msg["isUser"] ?: false)
            historyArray.put(msgObj)
        }
        requestBody.put("history", historyArray)

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(requestBody.toString())
        writer.flush()
        writer.close()

        if (conn.responseCode != 200) {
            conn.disconnect()
            throw RuntimeException("Server-Fehler: ${conn.responseCode}")
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        conn.disconnect()

        return response
    }
}
