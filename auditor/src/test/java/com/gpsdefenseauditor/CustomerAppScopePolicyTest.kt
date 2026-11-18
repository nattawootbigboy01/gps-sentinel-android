package com.gpsdefenseauditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerAppScopePolicyTest {
    private val scope = CustomerAppScope(
        scopeId = "scope-001",
        customerId = "customer-001",
        packageName = "com.example.customerapp",
        signingCertificateSha256 = "AA:BB:CC:DD",
        expiresAtMs = 10_000L,
        allowedOperations = setOf("READ_ONLY_AUDIT"),
        authorizedBy = "customer-admin"
    )
    private val identity = AppIdentity("com.example.customerapp", "aa-bb-cc-dd")

    @Test fun allowsMatchingAuthorizedTarget() {
        val result = CustomerAppScopePolicy.evaluate(scope, "customer-001", identity, "READ_ONLY_AUDIT", 1_000L)
        assertTrue(result.allowed)
        assertEquals("AUTHORIZED_WITHIN_CUSTOMER_SCOPE", result.reason)
    }

    @Test fun rejectsDifferentPackage() {
        val result = CustomerAppScopePolicy.evaluate(scope, "customer-001", identity.copy(packageName = "com.other.app"), "READ_ONLY_AUDIT", 1_000L)
        assertFalse(result.allowed)
        assertEquals("PACKAGE_MISMATCH", result.reason)
    }

    @Test fun rejectsDifferentSigningCertificate() {
        val result = CustomerAppScopePolicy.evaluate(scope, "customer-001", identity.copy(signingCertificateSha256 = "11:22"), "READ_ONLY_AUDIT", 1_000L)
        assertFalse(result.allowed)
        assertEquals("SIGNING_CERTIFICATE_MISMATCH", result.reason)
    }

    @Test fun rejectsDifferentCustomer() {
        val result = CustomerAppScopePolicy.evaluate(scope, "another-customer", identity, "READ_ONLY_AUDIT", 1_000L)
        assertFalse(result.allowed)
        assertEquals("CUSTOMER_MISMATCH", result.reason)
    }

    @Test fun rejectsExpiredAuthorization() {
        val result = CustomerAppScopePolicy.evaluate(scope, "customer-001", identity, "READ_ONLY_AUDIT", 10_000L)
        assertFalse(result.allowed)
        assertEquals("AUTHORIZATION_EXPIRED", result.reason)
    }

    @Test fun rejectsOperationOutsideScope() {
        val result = CustomerAppScopePolicy.evaluate(scope, "customer-001", identity, "ACTIVE_PENETRATION_TEST", 1_000L)
        assertFalse(result.allowed)
        assertEquals("OPERATION_OUT_OF_SCOPE", result.reason)
    }

    @Test fun rejectsMissingAuthorization() {
        val result = CustomerAppScopePolicy.evaluate(null, "customer-001", identity, "READ_ONLY_AUDIT", 1_000L)
        assertFalse(result.allowed)
        assertEquals("NO_AUTHORIZATION", result.reason)
    }
}
