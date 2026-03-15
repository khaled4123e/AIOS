// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ai

/**
 * Backend-Typen fuer die KI-Konfiguration.
 */
enum class BackendType {
    LOCAL,
    EXTERNAL_API,
    REMOTE_SERVER,
}

/**
 * Schnittstelle fuer alle LLM-Backends.
 *
 * Jedes Backend (lokal, Remote-Server, externe API) implementiert diese Schnittstelle,
 * damit der AIManager einheitlich darauf zugreifen kann.
 */
interface LLMBackend {
    /** Anzeigename des Backends. */
    val name: String

    /** Gibt an, ob das Backend aktuell verfuegbar und einsatzbereit ist. */
    val isAvailable: Boolean

    /**
     * Initialisiert das Backend (Modell laden, Verbindung pruefen, etc.).
     * @return true wenn die Initialisierung erfolgreich war.
     */
    fun initialize(): Boolean

    /**
     * Generiert eine Antwort basierend auf dem gegebenen Prompt.
     * @param prompt Der vollstaendige Prompt inklusive System-Prompt und Kontext.
     * @param maxTokens Maximale Anzahl der zu generierenden Tokens.
     * @return Die generierte Antwort als String.
     */
    fun generate(prompt: String, maxTokens: Int = 500): String

    /**
     * Faehrt das Backend herunter und gibt Ressourcen frei.
     */
    fun shutdown()
}
