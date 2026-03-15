// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ai

import android.content.Context
import android.util.Log
import java.io.File

/**
 * On-Device LLM-Backend basierend auf llama.cpp.
 *
 * Laedt ein GGUF-Modell aus dem externen Dateienverzeichnis der App
 * und fuehrt Inference lokal auf dem Geraet aus.
 */
class LlamaEngine(
    private val context: Context,
    private var modelFilename: String? = null,
    private var nThreads: Int = 4,
    private var nCtx: Int = 2048,
    private var nGpuLayers: Int = 0,
    private var temperature: Float = 0.7f,
) : LLMBackend {

    companion object {
        private const val TAG = "LlamaEngine"

        init {
            try {
                System.loadLibrary("aios_llm")
                Log.i(TAG, "Native Bibliothek aios_llm geladen")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Konnte native Bibliothek aios_llm nicht laden: ${e.message}")
            }
        }
    }

    // JNI-Methoden fuer die llama.cpp Bridge
    private external fun nativeLoadModel(path: String, nThreads: Int, nCtx: Int, nGpuLayers: Int): Long
    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temperature: Float): String
    private external fun nativeFreeModel(handle: Long)
    private external fun nativeGetModelInfo(handle: Long): String

    private var modelHandle: Long = 0L
    private var _isAvailable: Boolean = false
    private var loadedModelName: String = ""

    override val name: String
        get() = if (loadedModelName.isNotEmpty()) "Lokal: $loadedModelName" else "Lokales LLM (llama.cpp)"

    override val isAvailable: Boolean
        get() = _isAvailable && modelHandle != 0L

    /**
     * Initialisiert das Backend: sucht nach GGUF-Modellen und laedt das erste gefundene
     * oder das explizit konfigurierte Modell.
     */
    override fun initialize(): Boolean {
        val modelsDir = File(context.getExternalFilesDir(null), "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
            Log.w(TAG, "Modellverzeichnis erstellt: ${modelsDir.absolutePath} — bitte GGUF-Modell ablegen.")
            _isAvailable = false
            return false
        }

        val modelFile = if (modelFilename != null) {
            val specific = File(modelsDir, modelFilename!!)
            if (specific.exists()) specific else null
        } else {
            // Erstes .gguf-Modell im Verzeichnis suchen
            modelsDir.listFiles { file -> file.extension.equals("gguf", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?.firstOrNull()
        }

        if (modelFile == null || !modelFile.exists()) {
            Log.w(TAG, "Kein GGUF-Modell gefunden in: ${modelsDir.absolutePath}")
            _isAvailable = false
            return false
        }

        return try {
            Log.i(TAG, "Lade Modell: ${modelFile.name} (${modelFile.length() / 1024 / 1024} MB)")
            modelHandle = nativeLoadModel(modelFile.absolutePath, nThreads, nCtx, nGpuLayers)

            if (modelHandle != 0L) {
                loadedModelName = modelFile.nameWithoutExtension
                _isAvailable = true
                Log.i(TAG, "Modell erfolgreich geladen: $loadedModelName")
                true
            } else {
                Log.e(TAG, "Modell konnte nicht geladen werden (Handle = 0)")
                _isAvailable = false
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des Modells: ${e.message}", e)
            _isAvailable = false
            false
        }
    }

    /**
     * Generiert eine Antwort mit dem lokalen Modell.
     * Der AIOS-System-Prompt wird automatisch vorangestellt.
     */
    override fun generate(prompt: String, maxTokens: Int): String {
        if (modelHandle == 0L) {
            return """{"steps":[],"message":"Lokales Modell nicht geladen.","confidence":0.0}"""
        }

        val fullPrompt = SystemPrompt.AIOS_SYSTEM_PROMPT + "\n\n" + prompt + "\nAIOS (JSON):"

        return try {
            nativeGenerate(modelHandle, fullPrompt, maxTokens, temperature)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Textgenerierung: ${e.message}", e)
            """{"steps":[],"message":"Fehler bei lokaler Generierung: ${e.message}","confidence":0.0}"""
        }
    }

    /**
     * Gibt das Modell frei und setzt das Backend zurueck.
     */
    override fun shutdown() {
        if (modelHandle != 0L) {
            try {
                nativeFreeModel(modelHandle)
                Log.i(TAG, "Modell freigegeben: $loadedModelName")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Freigeben des Modells: ${e.message}", e)
            }
            modelHandle = 0L
            _isAvailable = false
            loadedModelName = ""
        }
    }

    /**
     * Gibt Informationen ueber das geladene Modell zurueck.
     */
    fun getModelInfo(): String {
        if (modelHandle == 0L) return "Kein Modell geladen"
        return try {
            nativeGetModelInfo(modelHandle)
        } catch (e: Exception) {
            "Modellinfo nicht verfuegbar: ${e.message}"
        }
    }

    /**
     * Konfiguriert den Modellpfad fuer das naechste Laden.
     */
    fun setModelFilename(filename: String) {
        modelFilename = filename
    }

    /**
     * Setzt die Generierungsparameter.
     */
    fun setGenerationParams(threads: Int? = null, contextSize: Int? = null, gpuLayers: Int? = null, temp: Float? = null) {
        threads?.let { nThreads = it }
        contextSize?.let { nCtx = it }
        gpuLayers?.let { nGpuLayers = it }
        temp?.let { temperature = it }
    }
}
