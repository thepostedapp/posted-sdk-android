package com.postedapp.posted.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/**
 * Pins the whole-file sha256 of the vendored backend-contract fixture
 * (`fixtures/posted-backend-contract.yaml`) to the canonical copy in
 * `posted-sdk-admin/contract/posted-backend-contract.yaml`.
 *
 * The sibling [MatchLinkFixtureDriftGuardTest] guards *structural* drift of specific schema
 * branches; this test additionally guards against ANY byte-level divergence — a stale re-vendor,
 * a whitespace edit, or an out-of-band change to the canonical contract that was not mirrored here.
 *
 * When the canonical contract legitimately changes, re-vendor and re-pin:
 *   cp ../posted-sdk-admin/contract/posted-backend-contract.yaml \
 *      posted-sdk/src/test/resources/fixtures/posted-backend-contract.yaml
 *   shasum -a 256 posted-sdk/src/test/resources/fixtures/posted-backend-contract.yaml
 * then update [EXPECTED_SHA256] below to the printed lower-case hex digest.
 */
class ContractFixtureChecksumTest {
    @Test
    fun `vendored backend-contract fixture matches pinned canonical sha256`() {
        val bytes =
            javaClass.classLoader!!
                .getResourceAsStream(FIXTURE_RESOURCE)
                ?.readBytes()
                ?: error("Missing test fixture: $FIXTURE_RESOURCE")

        val actual =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it) }

        assertEquals(
            """
            |Vendored backend-contract fixture has drifted from the canonical contract.
            |  expected sha256: $EXPECTED_SHA256
            |  actual   sha256: $actual
            |
            |If the canonical contract changed intentionally, re-vendor and re-pin:
            |  cp ../posted-sdk-admin/contract/posted-backend-contract.yaml \
            |     posted-sdk/src/test/resources/fixtures/posted-backend-contract.yaml
            |  shasum -a 256 posted-sdk/src/test/resources/fixtures/posted-backend-contract.yaml
            |then update EXPECTED_SHA256 in ${ContractFixtureChecksumTest::class.simpleName} to the new digest.
            """.trimMargin(),
            EXPECTED_SHA256,
            actual,
        )
    }

    private companion object {
        const val FIXTURE_RESOURCE = "fixtures/posted-backend-contract.yaml"

        // Canonical posted-sdk-admin/contract/posted-backend-contract.yaml (contract v1.1.0).
        const val EXPECTED_SHA256 =
            "9a6d4ce0013d4b9de4f2199ce603855890c39e93d605c7195dd8b8275a088bde"
    }
}
