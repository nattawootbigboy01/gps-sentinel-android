package com.gpsdefenseauditor

/**
 * Customer-scoped authorization decision for controlled security assessments.
 *
 * This is a policy evaluator, not a secure licensing system by itself. A production
 * deployment must obtain signed scopes from a backend and verify signatures server-side.
 */
data class CustomerAppScope(
    val scopeId: String,
    val customerId: String,
    val packageName: String,
    val signingCertificateSha256: String,
    val expiresAtMs: Long,
    val allowedOperations: Set<String>,
    val authorizedBy: String
)

data class AppIdentity(
    val packageName: String,
    val signingCertificateSha256: String
)

data class ScopeDecision(
    val allowed: Boolean,
    val reason: String
)

object CustomerAppScopePolicy {
    fun evaluate(
        scope: CustomerAppScope?,
        customerId: String,
        target: AppIdentity,
        operation: String,
        nowMs: Long = System.currentTimeMillis()
    ): ScopeDecision {
        if (scope == null) return ScopeDecision(false, "NO_AUTHORIZATION")
        if (scope.scopeId.isBlank() || scope.customerId.isBlank() || scope.authorizedBy.isBlank()) {
            return ScopeDecision(false, "INVALID_SCOPE_METADATA")
        }
        if (scope.customerId != customerId) return ScopeDecision(false, "CUSTOMER_MISMATCH")
        if (scope.packageName != target.packageName) return ScopeDecision(false, "PACKAGE_MISMATCH")
        val expectedCert = normalizeFingerprint(scope.signingCertificateSha256)
        val actualCert = normalizeFingerprint(target.signingCertificateSha256)
        if (expectedCert.isBlank() || actualCert.isBlank() || expectedCert != actualCert) {
            return ScopeDecision(false, "SIGNING_CERTIFICATE_MISMATCH")
        }
        if (nowMs >= scope.expiresAtMs) return ScopeDecision(false, "AUTHORIZATION_EXPIRED")
        if (operation !in scope.allowedOperations) return ScopeDecision(false, "OPERATION_OUT_OF_SCOPE")
        return ScopeDecision(true, "AUTHORIZED_WITHIN_CUSTOMER_SCOPE")
    }

    private fun normalizeFingerprint(value: String): String =
        value.filter { it.isLetterOrDigit() }.uppercase()
}
