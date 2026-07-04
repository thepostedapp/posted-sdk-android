package com.postedapp.posted

import android.content.Intent
import android.net.Uri
import com.google.gson.JsonParser
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType
import com.postedapp.posted.referrer.InstallReferrerHelper
import com.postedapp.posted.storage.AttributionCache
import com.postedapp.posted.storage.StorageKeys
import com.postedapp.posted.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Integration tests for [Posted.resolveAppLaunch] covering:
 * - LINK-PRESENT branch (http/https intent)
 * - DEFERRED branch (no-link intent)
 * - Cache short-circuit (second call — zero network)
 * - Re-arm via resetSession()
 */
@RunWith(RobolectricTestRunner::class)
class ResolveAppLaunchTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: InMemoryStorage

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        storage = InMemoryStorage()
        initPosted()
    }

    @After
    fun tearDown() {
        server.shutdown()
        resetPostedSingleton()
    }

    // ── LINK-PRESENT branch ──────────────────────────────────────────────────

    @Test
    fun `link-present branch - universal link click + short-link resolved`() =
        runTest {
            // click fires first, then resolve-short
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"allowed":true}"""))
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/product"}"""))

            // Use the mock server's own host so the short-link domain gate passes
            val shortLinkUrl = server.url("/x9Az").toString()
            val intent = linkIntent(shortLinkUrl)
            val result = Posted.resolveAppLaunch(intent)

            assertTrue(result is LinkResult.Success)
            assertEquals(LinkType.VERIFIED, (result as LinkResult.Success).type)
            assertEquals("https://example.com/product", result.url)
            assertEquals(2, server.requestCount)
            assertTrue(storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED) == "true")
        }

    @Test
    fun `link-present branch - single-segment non-short-link host skips resolveShortLink`() =
        runTest {
            // Only one response needed: the click. The domain gate must prevent the probe.
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"allowed":true}"""))

            val intent = linkIntent("https://example.com/abc")
            val result = Posted.resolveAppLaunch(intent)

            val success = result as LinkResult.Success
            assertEquals(1, server.requestCount)
            assertEquals("https://example.com/abc", success.url)
        }

    @Test
    fun `link-present branch - plain universal link (no short-link resolution)`() =
        runTest {
            // universal-link-click
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"allowed":true}"""))

            val intent = linkIntent("https://example.com/promo/summer?code=XYZ")
            val result = Posted.resolveAppLaunch(intent)

            assertTrue(result is LinkResult.Success)
            assertEquals(LinkType.VERIFIED, (result as LinkResult.Success).type)
            assertEquals("XYZ", result.params["code"])
        }

    // ── DEFERRED branch ──────────────────────────────────────────────────────

    @Test
    fun `deferred branch - match-link returns a link`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/product"}"""))

            val result = Posted.resolveAppLaunch(null)

            assertTrue(result is LinkResult.Success)
            assertEquals(LinkType.DEFERRED, (result as LinkResult.Success).type)
            assertEquals("https://example.com/app/product", result.url)
            assertTrue(storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED) == "true")
        }

    @Test
    fun `deferred branch - match-link returns null yields NoLink and is cached`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))

            val result = Posted.resolveAppLaunch(null)

            assertEquals(LinkResult.NoLink, result)
            assertTrue(storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED) == "true")
        }

    // ── DEFERRED branch — deterministic handoff (POS-228 / E1) ────────────────

    @Test
    fun `deferred branch - install-referrer click_id sends deterministic match-link and bypasses probabilistic scoring`() =
        runTest {
            // A click-through carried click_id in the Play install-referrer.
            injectReferrerHelper(clickId = "abc-click-123")
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/deterministic"}"""))

            val result = Posted.resolveAppLaunch(null)

            assertTrue(result is LinkResult.Success)
            assertEquals(LinkType.DEFERRED, (result as LinkResult.Success).type)
            assertEquals("https://example.com/app/deterministic", result.url)

            // Body must be the deterministic fingerprint: only clickId, no probabilistic signals.
            val req = server.takeRequest()
            assertEquals("/api/link/match-link", req.path)
            val json = JsonParser.parseString(req.body.readUtf8()).asJsonObject
            assertEquals("abc-click-123", json.get("clickId").asString)
            assertFalse("deterministic handoff must not carry platform", json.has("platform"))
            assertFalse("deterministic handoff must not carry screenWidth", json.has("screenWidth"))
        }

    @Test
    fun `deferred branch - absent install-referrer falls back to probabilistic fingerprint`() =
        runTest {
            // No click-through: install-referrer carries no click_id.
            injectReferrerHelper(clickId = null)
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/probabilistic"}"""))

            val result = Posted.resolveAppLaunch(null)

            assertTrue(result is LinkResult.Success)

            val req = server.takeRequest()
            assertEquals("/api/link/match-link", req.path)
            val json = JsonParser.parseString(req.body.readUtf8()).asJsonObject
            assertFalse("probabilistic fallback must not carry clickId", json.has("clickId"))
            assertTrue("probabilistic fingerprint must carry platform", json.has("platform"))
        }

    // ── Cache short-circuit ──────────────────────────────────────────────────

    @Test
    fun `second call returns cached result with zero network requests`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/deal"}"""))

            val first = Posted.resolveAppLaunch(null)
            assertTrue(first is LinkResult.Success)

            // No more server responses enqueued — any network call would throw
            val second = Posted.resolveAppLaunch(null)
            assertEquals(first, second)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `cached NoLink is returned on subsequent calls without network`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))

            val first = Posted.resolveAppLaunch(null)
            assertEquals(LinkResult.NoLink, first)

            val second = Posted.resolveAppLaunch(null)
            assertEquals(LinkResult.NoLink, second)
            assertEquals(1, server.requestCount)
        }

    // ── Re-arm via resetSession ───────────────────────────────────────────────

    @Test
    fun `resetSession clears cache so next resolveAppLaunch makes a fresh network call`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))
            Posted.resolveAppLaunch(null)
            assertEquals(LinkResult.NoLink, Posted.resolveAppLaunch(null))
            assertEquals(1, server.requestCount)

            Posted.resetSession()

            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/new"}"""))
            val rearmed = Posted.resolveAppLaunch(null)
            assertTrue(rearmed is LinkResult.Success)
            assertEquals(2, server.requestCount)
        }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun linkIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    private fun initPosted() {
        val context = RuntimeEnvironment.getApplication()
        val base = server.url("/").toString().trimEnd('/')

        // Use reflection to inject test config pointing at MockWebServer
        val configField = Posted::class.java.getDeclaredField("config")
        val apiClientField = Posted::class.java.getDeclaredField("apiClient")
        val storageField = Posted::class.java.getDeclaredField("storage")
        val attributionCacheField = Posted::class.java.getDeclaredField("attributionCache")
        val isInitField = Posted::class.java.getDeclaredField("isInitialized")

        for (f in listOf(configField, apiClientField, storageField, attributionCacheField, isInitField)) {
            f.isAccessible = true
        }

        val config = PostedConfig(apiKey = "test-key", appId = "test.app")
        val apiClient = com.postedapp.posted.api.PostedApiClient(config, context, base)

        configField.set(Posted, config)
        apiClientField.set(Posted, apiClient)
        storageField.set(Posted, storage)
        attributionCacheField.set(Posted, AttributionCache(storage))
        isInitField.set(Posted, true)

        // Wire up remaining components that resolveAppLaunch needs
        val fingerprintField = Posted::class.java.getDeclaredField("fingerprintCollector")
        val referrerField = Posted::class.java.getDeclaredField("installReferrerHelper")
        val firstLaunchField = Posted::class.java.getDeclaredField("firstLaunchDetector")
        for (f in listOf(fingerprintField, referrerField, firstLaunchField)) f.isAccessible = true
        fingerprintField.set(Posted, com.postedapp.posted.fingerprint.FingerprintCollector(context))
        referrerField.set(Posted, com.postedapp.posted.referrer.InstallReferrerHelper(context))
        firstLaunchField.set(Posted, com.postedapp.posted.storage.FirstLaunchDetector(storage))
    }

    /**
     * Replace the injected [InstallReferrerHelper] with a fake returning a fixed [clickId],
     * simulating (or not) a click-through that carried `click_id` in the Play install-referrer.
     */
    private fun injectReferrerHelper(clickId: String?) {
        val context = RuntimeEnvironment.getApplication()
        val fake =
            object : InstallReferrerHelper(context) {
                override suspend fun getClickId(): String? = clickId
            }
        val referrerField = Posted::class.java.getDeclaredField("installReferrerHelper")
        referrerField.isAccessible = true
        referrerField.set(Posted, fake)
    }

    private fun resetPostedSingleton() {
        val isInitField = Posted::class.java.getDeclaredField("isInitialized")
        isInitField.isAccessible = true
        isInitField.set(Posted, false)

        storage.clear()
    }
}
