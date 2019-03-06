package com.mastercard.gateway.threeds2

class Warning(
        val id: String,
        val message: String,
        val severity: Severity
) {
    enum class Severity { LOW, MEDIUM, HIGH }
}