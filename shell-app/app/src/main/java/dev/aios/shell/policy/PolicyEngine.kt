// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.policy

import dev.aios.shell.broker.RiskClass

/// The Policy Engine — like a security middleware.
/// Every tool call passes through here before execution.
/// Principle: deny-by-default.

enum class Decision {
    ALLOW,
    ALLOW_WITH_LOG,
    REQUIRE_CONFIRMATION,
    DENY,
    QUARANTINE,
}

data class PolicyContext(
    val toolId: String,
    val riskClass: RiskClass,
    val capabilities: List<String>,
    val sideEffects: List<String>,
    val deviceLocked: Boolean = false,
    val isRoaming: Boolean = false,
)

data class PolicyResult(
    val decision: Decision,
    val consentRequired: String? = null,
    val reason: String,
    val matchedRule: String? = null,
)

class PolicyEngine {

    fun evaluate(context: PolicyContext): PolicyResult {
        // Device locked → only low risk
        if (context.deviceLocked && context.riskClass != RiskClass.LOW) {
            return PolicyResult(
                decision = Decision.DENY,
                reason = "Gerät gesperrt — nur risikoarme Aktionen erlaubt",
                matchedRule = "locked-device-restrictions",
            )
        }

        // Roaming → block network
        if (context.isRoaming && context.capabilities.any { it.startsWith("network.") }) {
            return PolicyResult(
                decision = Decision.DENY,
                reason = "Netzwerkaktionen bei Roaming blockiert",
                matchedRule = "roaming-restrictions",
            )
        }

        // Risk-based evaluation
        return when (context.riskClass) {
            RiskClass.LOW -> {
                if (context.sideEffects.isEmpty()) {
                    PolicyResult(
                        decision = Decision.ALLOW,
                        reason = "Niedriges Risiko, keine Seiteneffekte",
                        matchedRule = "allow-low-risk-local",
                    )
                } else {
                    PolicyResult(
                        decision = Decision.ALLOW_WITH_LOG,
                        reason = "Niedriges Risiko mit Seiteneffekten — wird protokolliert",
                        matchedRule = "allow-low-risk-with-effects",
                    )
                }
            }
            RiskClass.MEDIUM -> PolicyResult(
                decision = Decision.REQUIRE_CONFIRMATION,
                consentRequired = "once",
                reason = "Mittleres Risiko — Bestätigung erforderlich",
                matchedRule = "confirm-medium-risk",
            )
            RiskClass.HIGH -> PolicyResult(
                decision = Decision.REQUIRE_CONFIRMATION,
                consentRequired = "strong",
                reason = "Hohes Risiko — starke Bestätigung erforderlich",
                matchedRule = "confirm-high-risk",
            )
            RiskClass.CRITICAL -> PolicyResult(
                decision = Decision.REQUIRE_CONFIRMATION,
                consentRequired = "biometric",
                reason = "Kritische Aktion — biometrische Bestätigung erforderlich",
                matchedRule = "confirm-critical",
            )
        }
    }
}
