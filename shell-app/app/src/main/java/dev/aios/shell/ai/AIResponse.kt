// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ai

/**
 * Ergebnis einer KI-Anfrage.
 */
data class AIResponse(
    val steps: List<AIStep>,
    val message: String,
    val confidence: Float,
    val durationMs: Long,
    val fromLLM: Boolean,
)

/**
 * Ein einzelner Schritt in einer KI-generierten Aktion.
 */
data class AIStep(
    val toolId: String,
    val params: Map<String, Any>,
    val description: String,
)
