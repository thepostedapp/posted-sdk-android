package com.postedapp.posted

// Tests for the RevenueCat user link (POST /api/revenueCatUser):
//   1. POST /api/revenueCatUser with SDK auth, the install's device_id and rc_user_id, then the flag is armed
//   2. The install is reported first; nothing is sent while it stays unreported
//   3. Sent once: nothing is sent once the flag is armed
//   4. The flag is armed only on a 2xx, so failures retry on the next call
//   5. Concurrent calls share one request
//   6. An empty id sends nothing

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SetRevenueCatUserIdTest {
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

    private suspend fun setUserId(id: String = "rc-user-1") =
        Posted.setRevenueCatUserIdAndAwait(
            context,
            config,
            id,
            PostedApiClient(config, server.url("/").toString().trimEnd('/')),
            object : InstallReferrerHelper(context) {
                override suspend fun getClickId(): String? = null
            },
        )

    private fun body(request: RecordedRequest) = JsonParser.parseString(request.body.readUtf8()).asJsonObject

    private suspend fun isReported() = storage.getItem(StorageKeys.REVENUECAT_USER_REPORTED) == "true"

    @Test
    fun `reports the install then posts device_id and rc_user_id with SDK auth and arms the flag`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            setUserId()

            val install = server.takeRequest()
            assertEquals("/api/install", install.path)

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/revenueCatUser", request.path)
            assertEquals("Bearer test-api-key", request.getHeader("Authorization"))
            assertEquals("com.test.app", request.getHeader("X-App-ID"))
            assertEquals(install.getHeader("X-SDK"), request.getHeader("X-SDK"))
            assertNotNull(request.getHeader("X-SDK"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))

            val json = body(request)
            assertEquals(setOf("device_id", "rc_user_id"), json.keySet())
            assertEquals("rc-user-1", json["rc_user_id"].asString)
            assertEquals(body(install)["device_id"].asString, json["device_id"].asString)
            assertTrue(isReported())
        }

    @Test
    fun `skips the install report when already reported`() =
        runBlocking {
            storage.setItem(StorageKeys.INSTALL_REPORTED, "true")
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            setUserId()

            assertEquals(1, server.requestCount)
            assertEquals("/api/revenueCatUser", server.takeRequest().path)
            assertTrue(isReported())
        }

    @Test
    fun `sends nothing while the install is unreported`() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))

            setUserId()

            assertEquals(1, server.requestCount)
            assertEquals("/api/install", server.takeRequest().path)
            assertFalse(isReported())
        }

    @Test
    fun `sends nothing once reported`() =
        runBlocking {
            storage.setItem(StorageKeys.REVENUECAT_USER_REPORTED, "true")

            setUserId()

            assertEquals(0, server.requestCount)
        }

    @Test
    fun `non-2xx leaves the flag unarmed so the next call retries`() =
        runBlocking {
            storage.setItem(StorageKeys.INSTALL_REPORTED, "true")
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"install_not_found"}"""))
            setUserId()
            assertFalse(isReported())

            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            setUserId()

            assertEquals(2, server.requestCount)
            assertTrue(isReported())
        }

    @Test
    fun `concurrent calls share one request`() =
        runBlocking {
            storage.setItem(StorageKeys.INSTALL_REPORTED, "true")
            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

            listOf(async { setUserId() }, async { setUserId() }, async { setUserId() }).awaitAll()

            assertEquals(1, server.requestCount)
            assertTrue(isReported())
        }

    @Test
    fun `empty id sends nothing`() =
        runBlocking {
            setUserId(id = "")

            assertEquals(0, server.requestCount)
            assertFalse(isReported())
        }
}
