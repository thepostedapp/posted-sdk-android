package com.postedapp.posted

import android.content.Intent
import android.net.Uri
import com.google.gson.JsonParser
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType
import com.postedapp.posted.referrer.InstallReferrerHelper
import com.postedapp.posted.storage.AttributionCache
import com.postedapp.posted.storage.PostedStorage
import com.postedapp.posted.storage.StorageKeys
import com.postedapp.posted.test.InMemoryStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    // ── Concurrent first-launch single-flight de-dup (POS-156) ────────────────

    @Test
    fun `concurrent first-launch resolveAppLaunch makes exactly one network call - deferred branch`() =
        runTest {
            // Only ONE match-link response is enqueued: a second concurrent resolve would throw
            // (no response) and, before the single-flight fix, two coroutines both hit the network.
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/single"}"""))

            val n = 20
            val results =
                (1..n).map {
                    async { Posted.resolveAppLaunch(null) }
                }.awaitAll()

            // Exactly one resolve reached the network; every caller observed the same result.
            assertEquals(1, server.requestCount)
            assertTrue(results.first() is LinkResult.Success)
            results.forEach { assertEquals(results.first(), it) }
        }

    @Test
    fun `concurrent first-launch resolveAppLaunch makes exactly one network call - link-present branch`() =
        runTest {
            // Non-short-link host → only the universal-link click hits the network (probe skipped).
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"allowed":true}"""))

            val intent = linkIntent("https://example.com/promo/summer?code=XYZ")
            val n = 20
            val results =
                (1..n).map {
                    async { Posted.resolveAppLaunch(intent) }
                }.awaitAll()

            assertEquals(1, server.requestCount)
            assertTrue(results.first() is LinkResult.Success)
            results.forEach { assertEquals(results.first(), it) }
        }

    // ── Storage-write-failure isolation (POS-501) ─────────────────────────────

    @Test
    fun `throwing storage on persist does not convert the resolved result to Error and leaves fire-once unarmed`() =
        runTest {
            val throwingStorage = ThrowingSetItemsStorage(storage)
            val storageField = Posted::class.java.getDeclaredField("storage")
            val attributionCacheField = Posted::class.java.getDeclaredField("attributionCache")
            storageField.isAccessible = true
            attributionCacheField.isAccessible = true
            storageField.set(Posted, throwingStorage)
            attributionCacheField.set(Posted, AttributionCache(throwingStorage))

            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/first"}"""))
            val first = Posted.resolveAppLaunch(null)

            assertTrue(first is LinkResult.Success)
            assertEquals("https://example.com/app/first", (first as LinkResult.Success).url)
            assertFalse(
                "persist failed, so the fire-once flag must not be armed",
                storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED) == "true",
            )

            // Fire-once flag not armed -> the next call re-resolves instead of serving a stale cache.
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/second"}"""))
            val second = Posted.resolveAppLaunch(null)
            assertTrue(second is LinkResult.Success)
            assertEquals("https://example.com/app/second", (second as LinkResult.Success).url)
            assertEquals(2, server.requestCount)
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

            // resolveAppLaunch takes the resolveMutex, clears the cache (honoring the pending
            // reset), then sees isResolved()==false and makes a fresh network call.
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/new"}"""))
            val rearmed = Posted.resolveAppLaunch(null)
            assertTrue(rearmed is LinkResult.Success)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `resetSession does not block the calling coroutine - no runBlocking on caller dispatcher`() =
        runTest {
            // Resolve once so there's something to clear.
            server.enqueue(MockResponse().setResponseCode(404))
            Posted.resolveAppLaunch(null)
            assertTrue(Posted.resolveAppLaunch(null) == LinkResult.NoLink)

            // resetSession() must return promptly without blocking this coroutine. It only sets
            // a volatile flag (no runBlocking, no storage I/O), so it can never deadlock or ANR.
            Posted.resetSession()

            // Enqueue the re-arm response AFTER resetSession() returns — the next resolveAppLaunch
            // clears the cache under the resolveMutex, then re-resolves exactly once.
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/rearm"}"""))
            val result = Posted.resolveAppLaunch(null)
            assertTrue(result is LinkResult.Success)
            assertEquals(2, server.requestCount)
        }

    // ── Re-arm — real-dispatcher & mid-resolve regressions (POS-182) ──────────

    /**
     * Regression guard for the flake that motivated [POS-182]: the original re-arm coverage ran
     * under `runTest` virtual time, so the clear/re-resolve raced the real IO dispatcher. This test
     * uses [runBlocking] — real threads, real time — so `resolveScope` (Dispatchers.Default) and the
     * deferred clear execute on genuine dispatchers with no virtual-time scheduling to paper over a
     * missed clear. It fails if the fire-once flag / cache blob is not cleared before re-arm (the
     * second resolve would short-circuit on the stale cache and `requestCount` would stay at 1).
     */
    @Test
    fun `resetSession re-arm on the real dispatcher makes a fresh network call`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(404))
            assertEquals(LinkResult.NoLink, Posted.resolveAppLaunch(null))
            // Second call is served from cache — proves the fire-once gate is armed.
            assertEquals(LinkResult.NoLink, Posted.resolveAppLaunch(null))
            assertEquals(1, server.requestCount)
            assertTrue(storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED) == "true")

            Posted.resetSession()

            // The clear is deferred to the next resolve (under resolveMutex). Enqueue the re-arm
            // response only now, so a fresh network call is the only way to satisfy it.
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/new"}"""))
            val rearmed = Posted.resolveAppLaunch(null)

            assertTrue(rearmed is LinkResult.Success)
            // A brand-new result replaced the cached NoLink → the stale blob was really cleared.
            assertEquals("https://example.com/new", (rearmed as LinkResult.Success).url)
            assertEquals(2, server.requestCount)
        }

    /**
     * Edge case from [POS-182]: [Posted.resetSession] invoked *while a resolve is in flight*.
     *
     * The in-flight resolve is allowed to complete and cache its result — resetSession only sets the
     * pending-clear flag, it does not cancel work in progress. The contract being asserted is that
     * the reset is honored on the *next* resolve: it clears the (now stale) cache under the resolve
     * lock and re-resolves, making a fresh network call rather than serving the just-cached value.
     *
     * Determinism comes from a gated MockWebServer dispatcher (CountDownLatch handshake), not
     * `delay()` sleeps: the reset is guaranteed to land after the first request has hit the network
     * but before its response is released.
     */
    @Test
    fun `resetSession during an in-flight resolve re-arms the next resolve`() =
        runBlocking {
            val firstRequestArrived = CountDownLatch(1)
            val releaseFirstResponse = CountDownLatch(1)
            val requests = AtomicInteger(0)

            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (requests.getAndIncrement() == 0) {
                            // First (in-flight) resolve: signal arrival, then block until the test
                            // has issued resetSession() so the reset provably overlaps the resolve.
                            firstRequestArrived.countDown()
                            releaseFirstResponse.await()
                            MockResponse().setResponseCode(404)
                        } else {
                            // Re-armed resolve.
                            MockResponse().setResponseCode(200)
                                .setBody("""{"link":"https://example.com/rearm"}""")
                        }
                }

            // Kick off the first resolve on a real dispatcher; it will park in the dispatcher above.
            val first = async(Dispatchers.Default) { Posted.resolveAppLaunch(null) }
            assertTrue(
                "first resolve should reach the network",
                firstRequestArrived.await(5, TimeUnit.SECONDS),
            )

            // Reset while the first resolve is still parked mid-flight.
            Posted.resetSession()

            // Let the in-flight resolve finish and cache its (now stale) NoLink result.
            releaseFirstResponse.countDown()
            assertEquals(LinkResult.NoLink, first.await())

            // The pending reset must force the next resolve to clear + re-resolve, not serve NoLink.
            val rearmed = Posted.resolveAppLaunch(null)
            assertTrue(rearmed is LinkResult.Success)
            assertEquals("https://example.com/rearm", (rearmed as LinkResult.Success).url)
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
        val inFlightField = Posted::class.java.getDeclaredField("inFlight")
        val clearPendingField = Posted::class.java.getDeclaredField("clearPending")
        for (f in listOf(isInitField, inFlightField, clearPendingField)) f.isAccessible = true
        isInitField.set(Posted, false)
        inFlightField.set(Posted, null)
        clearPendingField.setBoolean(Posted, false)

        storage.clear()
    }
}

/**
 * Delegates reads to [delegate] but simulates a custom [PostedStorage] provider whose atomic
 * batch write throws (disk full, crypto-key error, IOException, etc.) — see POS-501.
 */
private class ThrowingSetItemsStorage(private val delegate: PostedStorage) : PostedStorage {
    override suspend fun getItem(key: String): String? = delegate.getItem(key)

    override suspend fun setItem(
        key: String,
        value: String,
    ) = delegate.setItem(key, value)

    override suspend fun setItems(entries: Map<String, String>): Nothing = throw IllegalStateException("simulated storage write failure")

    override suspend fun removeItem(key: String) = delegate.removeItem(key)
}
