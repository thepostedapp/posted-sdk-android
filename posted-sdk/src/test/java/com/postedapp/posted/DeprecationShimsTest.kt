package com.postedapp.posted

import android.content.Intent
import android.net.Uri
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType
import com.postedapp.posted.storage.AttributionCache
import com.postedapp.posted.storage.StorageKeys
import com.postedapp.posted.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Verifies the 3.0.0 deprecation shims for:
 *  - resolveInitialLink(Intent) / resolveInitialLink()
 *  - processLink(Intent)
 *  - getDeferredLink()
 *  - resolveShortLink(String)
 *
 * Each shim must:
 *  1. Delegate to the new resolveAppLaunch / internal path (fire-once + cache semantics).
 *  2. Return the same result shape as a direct resolveAppLaunch call.
 */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class DeprecationShimsTest {
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

    // ── resolveInitialLink(Intent) ───────────────────────────────────────────

    @Test
    fun `resolveInitialLink(intent) routes to link-present branch and caches result`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"allowed":true}"""))

            val result = Posted.resolveInitialLink(linkIntent("https://example.com/promo"))

            assertTrue(result is LinkResult.Success)
            assertEquals(LinkType.VERIFIED, (result as LinkResult.Success).type)
            assertEquals("true", storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED))
        }

    // ── resolveInitialLink() no-arg ──────────────────────────────────────────

    @Test
    fun `resolveInitialLink() routes to deferred branch and caches result`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app"}"""))

            val result = Posted.resolveInitialLink()

            assertTrue(result is LinkResult.Success)
            assertEquals(LinkType.DEFERRED, (result as LinkResult.Success).type)
            assertEquals("true", storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED))
        }

    // ── processLink(Intent) ──────────────────────────────────────────────────

    @Test
    fun `processLink(intent) delegates to resolveAppLaunch — link-present branch`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"allowed":true}"""))

            val result = Posted.processLink(linkIntent("https://example.com/summer"))

            assertTrue(result is LinkResult.Success)
            assertEquals(LinkType.VERIFIED, (result as LinkResult.Success).type)
        }

    @Test
    fun `processLink second call returns cached result without extra network`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"allowed":true}"""))

            Posted.processLink(linkIntent("https://example.com/a"))
            val second = Posted.processLink(linkIntent("https://example.com/b"))

            // Fire-once: second call returns cached result, no new request
            assertEquals(1, server.requestCount)
            assertTrue(second is LinkResult.Success)
        }

    // ── getDeferredLink() ────────────────────────────────────────────────────

    @Test
    fun `getDeferredLink delegates to resolveAppLaunch — deferred branch`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/deal"}"""))

            val result = Posted.getDeferredLink()

            assertTrue(result is LinkResult.Success)
            assertEquals(LinkType.DEFERRED, (result as LinkResult.Success).type)
            assertEquals("https://example.com/deal", (result as LinkResult.Success).url)
        }

    @Test
    fun `getDeferredLink second call returns cached result without extra network`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/deal"}"""))

            val first = Posted.getDeferredLink()
            val second = Posted.getDeferredLink()

            assertEquals(1, server.requestCount)
            assertEquals(first, second)
        }

    // ── resolveShortLink(String) ─────────────────────────────────────────────

    @Test
    fun `resolveShortLink returns resolved link from API`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/full-url"}"""))

            val result = Posted.resolveShortLink("https://godetour.dev/xAb")

            assertEquals("https://example.com/full-url", result?.link)
        }

    @Test
    fun `resolveShortLink returns null on 404`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(404))

            val result = Posted.resolveShortLink("https://godetour.dev/invalid")

            assertNull(result)
        }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun linkIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    private fun initPosted() {
        val context = RuntimeEnvironment.getApplication()
        val base = server.url("/").toString().trimEnd('/')

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

        val fingerprintField = Posted::class.java.getDeclaredField("fingerprintCollector")
        val referrerField = Posted::class.java.getDeclaredField("installReferrerHelper")
        val firstLaunchField = Posted::class.java.getDeclaredField("firstLaunchDetector")
        for (f in listOf(fingerprintField, referrerField, firstLaunchField)) f.isAccessible = true
        fingerprintField.set(Posted, com.postedapp.posted.fingerprint.FingerprintCollector(context))
        referrerField.set(Posted, com.postedapp.posted.referrer.InstallReferrerHelper(context))
        firstLaunchField.set(Posted, com.postedapp.posted.storage.FirstLaunchDetector(storage))
    }

    private fun resetPostedSingleton() {
        val isInitField = Posted::class.java.getDeclaredField("isInitialized")
        isInitField.isAccessible = true
        isInitField.set(Posted, false)
        storage.clear()
    }
}
