package com.swmansion.detour.analytics

import com.swmansion.detour.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsMetadataTest {
    @Test
    fun `sdk header value uses android prefix and module version`() {
        assertEquals("android/${BuildConfig.SDK_VERSION}", BuildConfig.SDK_HEADER_VALUE)
    }

    @Test
    fun `opened via universal link event matches react native sdk`() {
        assertEquals(
            "opened_via_universal_link",
            DetourEventNames.OpenedViaUniversalLink.eventName,
        )
    }
}
