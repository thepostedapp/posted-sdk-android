package com.postedapp.posted.api

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole-file drift guard for the vendored SDK-backend contract (POS-578 / POS-283).
 *
 * [MatchLinkFixtureDriftGuardTest] guards two specific schema branches. This guards the
 * whole file: the vendored copy at `fixtures/posted-backend-contract.yaml` must be
 * byte-identical to the canonical contract owned by posted-sdk-admin at
 * `contract/posted-backend-contract.yaml`.
 *
 * TO RE-VENDOR after the canonical contract changes:
 *   1. copy canonical over the fixture
 *   2. `shasum -a 256 posted-sdk/src/test/resources/fixtures/posted-backend-contract.yaml`
 *   3. update [CANONICAL_SHA256] below
 *   4. move the sibling pins in the same change (Flutter `test/contract_drift_test.dart`,
 *      React Native `__fixtures__`, iOS `ContractConformanceTests.swift`)
 *
 * KNOWN LIMITATION: a pinned checksum detects this vendored copy being edited, but NOT the
 * canonical contract drifting ahead of it. That needs cross-repo read access in CI and is
 * deliberately out of scope.
 */
class ContractFixtureChecksumTest {

    @Test
    fun `vendored contract matches the pinned canonical checksum`() {
        val bytes = readFixtureBytes()
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        assertEquals(
            "Vendored contract drifted from canonical. Re-vendor and update " +
                "CANONICAL_SHA256 (see this file's header).",
            CANONICAL_SHA256,
            actual,
        )
    }

    @Test
    fun `vendored contract declares version 1_1_0`() {
        val text = String(readFixtureBytes())
        assertTrue(
            "Vendored contract is not v1.1.0; re-vendor from canonical.",
            text.contains("version: 1.1.0"),
        )
    }

    @Test
    fun `vendored contract targets the postedapp_com API host`() {
        val text = String(readFixtureBytes())
        assertTrue(
            "Contract must declare attr.postedapp.com as the API server.",
            text.contains("https://attr.postedapp.com"),
        )
        assertFalse(
            "Contract must not reference the third-party godetour.dev host.",
            text.contains("godetour.dev"),
        )
    }

    private fun readFixtureBytes(): ByteArray =
        javaClass.classLoader!!
            .getResourceAsStream(FIXTURE_PATH)
            ?.readBytes()
            ?: error("Missing test fixture: $FIXTURE_PATH")

    private companion object {
        /** Canonical contract v1.1.0 digest. */
        const val CANONICAL_SHA256 =
            "9a6d4ce0013d4b9de4f2199ce603855890c39e93d605c7195dd8b8275a088bde"
        const val FIXTURE_PATH = "fixtures/posted-backend-contract.yaml"
    }
}
