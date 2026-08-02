package com.postedapp.posted.api

import android.content.Context
import com.google.gson.JsonParser
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import com.postedapp.posted.BuildConfig
import com.postedapp.posted.FlutterSdkHeaderResolver
import com.postedapp.posted.Posted
import com.postedapp.posted.PostedConfig
import com.postedapp.posted.fingerprint.FingerprintCollector
import com.postedapp.posted.models.DeterministicFingerprint
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.referrer.InstallReferrerHelper
import com.postedapp.posted.storage.AttributionCache
import com.postedapp.posted.storage.FirstLaunchDetector
import com.postedapp.posted.test.InMemoryStorage
import kotlinx.coroutines.runBlocking
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

/**
 * JUnit4 + Robolectric conformance suite verifying the Android SDK emits
 * contract-conformant requests to all 5 backend endpoints.
 *
 * Requests are captured via MockWebServer. Bodies are validated against
 * JSON Schema excerpts pinned from posted-backend-contract.yaml.
 */
@RunWith(RobolectricTestRunner::class)
class ContractConformanceTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var config: PostedConfig
    private lateinit var apiClient: PostedApiClient
    private lateinit var analyticsClient: AnalyticsApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = RuntimeEnvironment.getApplication()
        config = PostedConfig(apiKey = TEST_API_KEY, appId = TEST_APP_ID)
        val base = server.url("/").toString().trimEnd('/')
        apiClient = PostedApiClient(config, context, base)
        analyticsClient = AnalyticsApiClient(config, base)
    }

    @After
    fun tearDown() {
        server.shutdown()
        resetPostedSingleton()
    }

    // ── match-link (deterministic) ───────────────────────────────────────────

    @Test
    fun `match-link deterministic branch sends only clickId and passes oneOf selection`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app"}"""))

        runBlocking { apiClient.matchLink(DeterministicFingerprint(clickId = "abc-click-123")) }

        val req = server.takeRequest()
        assertEquals("/api/link/match-link", req.path)
        assertCrossHeaders(req)

        val body = req.body.readUtf8()
        assertSchema(body, "schema-match-link-deterministic")

        val json = JsonParser.parseString(body).asJsonObject
        assertTrue("body must have clickId", json.has("clickId"))
        assertEquals("abc-click-123", json.get("clickId").asString)
        assertFalse("deterministic body must not carry platform", json.has("platform"))
        assertFalse("deterministic body must not carry model", json.has("model"))
    }

    // ── match-link via public resolveAppLaunch one-shot (POS-438) ─────────────
    //
    // Drives the PUBLIC Posted.resolveAppLaunch(...) end-to-end (fire-once attribution),
    // rather than the low-level PostedApiClient.matchLink used above, and validates the
    // captured match-link body against the same contract oneOf branches.

    @Test
    fun `resolveAppLaunch deferred branch with install-referrer click_id emits contract-valid deterministic match-link`() {
        val postedStorage = InMemoryStorage()
        initPostedSingleton(postedStorage)
        injectInstallReferrer(clickId = "abc-click-123")
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/deterministic"}"""))

        val result = runBlocking { Posted.resolveAppLaunch(null) }

        assertTrue(result is LinkResult.Success)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/link/match-link", req.path)
        assertCrossHeaders(req)

        val body = req.body.readUtf8()
        assertSchema(body, "schema-match-link-deterministic")

        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("abc-click-123", json.get("clickId").asString)
        assertFalse("deterministic body must not carry platform", json.has("platform"))
    }

    @Test
    fun `resolveAppLaunch deferred branch with no install-referrer emits contract-valid probabilistic fingerprint`() {
        val postedStorage = InMemoryStorage()
        initPostedSingleton(postedStorage)
        injectInstallReferrer(clickId = null)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/probabilistic"}"""))

        val result = runBlocking { Posted.resolveAppLaunch(null) }

        assertTrue(result is LinkResult.Success)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/link/match-link", req.path)
        assertCrossHeaders(req)

        val body = req.body.readUtf8()
        assertSchema(body, "schema-match-link-probabilistic")

        val json = JsonParser.parseString(body).asJsonObject
        assertFalse("probabilistic body must not carry clickId", json.has("clickId"))
        assertEquals("android", json.get("platform").asString)
    }

    // ── resolve-short ────────────────────────────────────────────────────────

    @Test
    fun `resolve-short sends url field conforming to contract`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"link":"https://example.com/app/product"}"""))
        val shortUrl = "https://godetour.dev/x9Az"

        runBlocking { apiClient.resolveShortLink(shortUrl) }

        val req = server.takeRequest()
        assertEquals("/api/link/resolve-short", req.path)
        assertCrossHeaders(req)

        val body = req.body.readUtf8()
        assertSchema(body, "schema-resolve-short")

        val json = JsonParser.parseString(body).asJsonObject
        assertEquals(shortUrl, json.get("url").asString)
    }

    // ── universal-link-click ─────────────────────────────────────────────────

    @Test
    fun `universal-link-click minimal android shape omits params and conforms to contract`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"allowed":true,"clickId":"clk_001"}"""))
        val url = "https://godetour.dev/u/launch"

        runBlocking { apiClient.sendUniversalLinkClick(url) }

        val req = server.takeRequest()
        assertEquals("/api/link/universal-link-click", req.path)
        assertCrossHeaders(req)

        val body = req.body.readUtf8()
        assertSchema(body, "schema-universal-link-click")

        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("android", json.get("platform").asString)
        assertTrue("timestamp must be positive epoch-ms", json.get("timestamp").asLong > 0L)
        assertFalse("minimal shape must not include params when empty", json.has("params"))
    }

    // ── analytics/event ──────────────────────────────────────────────────────

    @Test
    fun `analytics-event sends ISO-8601 UTC ms timestamp and conforms to contract`() {
        server.enqueue(MockResponse().setResponseCode(200))

        runBlocking {
            analyticsClient.sendEvent("purchase", mapOf("order_id" to "ord_001"), DEVICE_ID)
        }

        val req = server.takeRequest()
        assertEquals("/api/analytics/event", req.path)
        assertCrossHeaders(req)

        val body = req.body.readUtf8()
        assertSchema(body, "schema-analytics-event")

        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("android", json.get("platform").asString)
        assertEquals(DEVICE_ID, json.get("device_id").asString)
        val ts = json.get("timestamp").asString
        assertTrue("timestamp must be ISO-8601 UTC ms: $ts", ts.matches(ISO8601_MS_UTC))
    }

    // ── analytics/retention ──────────────────────────────────────────────────

    @Test
    fun `analytics-retention never carries data field and conforms to contract`() {
        server.enqueue(MockResponse().setResponseCode(200))

        runBlocking { analyticsClient.sendRetentionEvent("app_open", DEVICE_ID) }

        val req = server.takeRequest()
        assertEquals("/api/analytics/retention", req.path)
        assertCrossHeaders(req)

        val body = req.body.readUtf8()
        assertSchema(body, "schema-analytics-retention")

        val json = JsonParser.parseString(body).asJsonObject
        assertFalse("retention body must not carry data field", json.has("data"))
        assertEquals("android", json.get("platform").asString)
        val ts = json.get("timestamp").asString
        assertTrue("timestamp must be ISO-8601 UTC ms: $ts", ts.matches(ISO8601_MS_UTC))
    }

    // ── X-SDK header ─────────────────────────────────────────────────────────

    @Test
    fun `X-SDK header uses android platform via FlutterSdkHeaderResolver without Flutter marker`() {
        val sdkHeader = FlutterSdkHeaderResolver.sdkHeaderValue
        // Without the Flutter marker class on the classpath, resolver falls back to BuildConfig.
        assertEquals(BuildConfig.SDK_HEADER_VALUE, sdkHeader)
        assertTrue("X-SDK must start with android/", sdkHeader.startsWith("android/"))
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun assertCrossHeaders(req: RecordedRequest) {
        assertEquals("Bearer $TEST_API_KEY", req.getHeader("Authorization"))
        assertEquals(TEST_APP_ID, req.getHeader("X-App-ID"))
        assertEquals(FlutterSdkHeaderResolver.sdkHeaderValue, req.getHeader("X-SDK"))
    }

    private fun assertSchema(
        json: String,
        schemaFileName: String,
    ) {
        val errors: Set<ValidationMessage> =
            loadSchema(schemaFileName).validate(
                OBJECT_MAPPER.readTree(json),
            )
        assertTrue("Schema '$schemaFileName' violations for body $json — errors: $errors", errors.isEmpty())
    }

    private fun loadSchema(name: String) =
        SCHEMA_FACTORY.getSchema(
            javaClass.classLoader!!.getResourceAsStream("fixtures/$name.json")
                ?: error("Missing test fixture: fixtures/$name.json"),
        )

    /**
     * Wires the [Posted] singleton (via reflection, same pattern as ResolveAppLaunchTest) at this
     * test's MockWebServer, with attribution storage empty so the fire-once gate is armed and
     * resolveAppLaunch performs a fresh network resolve.
     */
    private fun initPostedSingleton(postedStorage: InMemoryStorage) {
        val base = server.url("/").toString().trimEnd('/')
        val postedConfig = PostedConfig(apiKey = TEST_API_KEY, appId = TEST_APP_ID)
        val postedApiClient = PostedApiClient(postedConfig, context, base)

        val configField = Posted::class.java.getDeclaredField("config")
        val apiClientField = Posted::class.java.getDeclaredField("apiClient")
        val storageField = Posted::class.java.getDeclaredField("storage")
        val attributionCacheField = Posted::class.java.getDeclaredField("attributionCache")
        val isInitField = Posted::class.java.getDeclaredField("isInitialized")
        val fingerprintField = Posted::class.java.getDeclaredField("fingerprintCollector")
        val referrerField = Posted::class.java.getDeclaredField("installReferrerHelper")
        val firstLaunchField = Posted::class.java.getDeclaredField("firstLaunchDetector")
        val fields =
            listOf(
                configField,
                apiClientField,
                storageField,
                attributionCacheField,
                isInitField,
                fingerprintField,
                referrerField,
                firstLaunchField,
            )
        for (f in fields) {
            f.isAccessible = true
        }

        configField.set(Posted, postedConfig)
        apiClientField.set(Posted, postedApiClient)
        storageField.set(Posted, postedStorage)
        attributionCacheField.set(Posted, AttributionCache(postedStorage))
        fingerprintField.set(Posted, FingerprintCollector(context))
        referrerField.set(Posted, InstallReferrerHelper(context))
        firstLaunchField.set(Posted, FirstLaunchDetector(postedStorage))
        isInitField.set(Posted, true)
    }

    /** Replaces the injected [InstallReferrerHelper] with a fake returning a fixed clickId. */
    private fun injectInstallReferrer(clickId: String?) {
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
    }

    companion object {
        private const val TEST_API_KEY = "test-bearer-key"
        private const val TEST_APP_ID = "test.app.id"
        private const val DEVICE_ID = "8f2b1c9e-7d3a-4e21-9f10-2c5b6a1d4e88"
        private val ISO8601_MS_UTC = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$""")
        private val SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        private val OBJECT_MAPPER = com.fasterxml.jackson.databind.ObjectMapper()
    }
}
