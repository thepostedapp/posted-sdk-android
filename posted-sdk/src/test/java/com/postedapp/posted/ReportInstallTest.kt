package com.postedapp.posted

// Tests for the install report (POST /api/install), parity with the RN SDK's reportInstall:
//   1. POST /api/install with SDK auth, the fingerprint and device_id, then the flag is armed
//   2. clickId is sent when the Play install referrer carries one, and omitted otherwise
//   3. Sent once: nothing is sent once the flag is armed
//   4. The flag is armed only on a 2xx, so failures retry on the next launch
//   5. Concurrent calls share one request
//   6. Blank credentials send nothing

import android.content.Context
import com.google.gson.JsonParser
import com.postedapp.posted.api.PostedApiClient
import com.postedapp.posted.referrer.InstallReferrerHelper
import com.postedapp.posted.storage.StorageKeys
import com.postedapp.posted.test.InMemoryStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ReportInstallTest {
    private lateinit var server: MockWebServer
    private lateinit var storage: InMemoryStorage
    private lateinit var context: Context
    private lateinit var config: PostedConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        context = RuntimeEnvironment.getApplication()
        storage = InMemoryStorage()
        config = PostedConfig(apiKey = "test-api-key", appId = "com.test.app", storage = storage)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun apiClient() = PostedApiClient(config, server.url("/").toString().trimEnd('/'))

    private fun referrer(clickId: String?) =
        object : InstallReferrerHelper(context) {
            override suspend fun getClickId(): String? = clickId
        }

    private suspend fun report(
        clickId: String? = null,
        cfg: PostedConfig = config,
    ) = Posted.reportInstallAndAwait(context, cfg, apiClient(), referrer(clickId))

    private fun body(request: RecordedRequest) = JsonParser.parseString(request.body.readUtf8()).asJsonObject

    private suspend fun isReported() = storage.getItem(StorageKeys.INSTALL_REPORTED) == "true"

    @Test
    fun `posts fingerprint and device_id with SDK auth then arms the flag`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            report()

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/install", request.path)
            assertEquals("Bearer test-api-key", request.getHeader("Authorization"))
            assertEquals("com.test.app", request.getHeader("X-App-ID"))
            assertNotNull(request.getHeader("X-SDK"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))

            val json = body(request)
            for (key in listOf(
                "platform", "model", "manufacturer", "systemVersion", "screenWidth", "screenHeight",
                "scale", "locale", "timezone", "timestamp",
            )) {
                assertTrue("fingerprint key '$key' missing from install body", json.has(key))
            }
            assertEquals("android", json["platform"].asString)
            assertEquals(storage.getItem(StorageKeys.DEVICE_ID), json["device_id"].asString)
            assertFalse("clickId must be omitted without an install referrer", json.has("clickId"))
            assertTrue(isReported())
        }

    @Test
    fun `sends clickId from the Play install referrer`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            report(clickId = "0192f1e2-aaaa-7bbb-8ccc-dddddddddddd")

            val json = body(server.takeRequest())
            assertEquals("0192f1e2-aaaa-7bbb-8ccc-dddddddddddd", json["clickId"].asString)
        }

    @Test
    fun `sends nothing once reported`() =
        runBlocking {
            storage.setItem(StorageKeys.INSTALL_REPORTED, "true")

            report()

            assertEquals(0, server.requestCount)
        }

    @Test
    fun `non-2xx leaves the flag unarmed so the next launch retries`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))
            report()
            assertFalse(isReported())

            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            report()

            assertEquals(2, server.requestCount)
            assertTrue(isReported())
        }

    @Test
    fun `device id is stable across a retry`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            report()
            report()

            val first = body(server.takeRequest())["device_id"].asString
            val second = body(server.takeRequest())["device_id"].asString
            assertEquals(first, second)
        }

    @Test
    fun `concurrent calls share one request`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}").setBodyDelay(200, java.util.concurrent.TimeUnit.MILLISECONDS))

            listOf(async { report() }, async { report() }, async { report() }).awaitAll()

            assertEquals(1, server.requestCount)
            assertTrue(isReported())
        }

    @Test
    fun `blank credentials send nothing`() =
        runBlocking {
            report(cfg = PostedConfig(apiKey = "", appId = "com.test.app", storage = storage))

            assertEquals(0, server.requestCount)
            assertNull(storage.getItem(StorageKeys.INSTALL_REPORTED))
        }
}
