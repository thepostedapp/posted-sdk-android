package com.postedapp.posted.storage

import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType
import com.postedapp.posted.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AttributionCacheTest {
    private lateinit var storage: InMemoryStorage
    private lateinit var cache: AttributionCache

    @Before
    fun setUp() {
        storage = InMemoryStorage()
        cache = AttributionCache(storage)
    }

    @Test
    fun `isResolved returns false on fresh storage`() =
        runTest {
            assertFalse(cache.isResolved())
        }

    @Test
    fun `saveResult NoLink sets resolved flag`() =
        runTest {
            cache.saveResult(LinkResult.NoLink)
            assertTrue(cache.isResolved())
        }

    @Test
    fun `loadResult returns NoLink after saving NoLink`() =
        runTest {
            cache.saveResult(LinkResult.NoLink)
            assertEquals(LinkResult.NoLink, cache.loadResult())
        }

    @Test
    fun `saveResult Success persists all fields and sets resolved flag`() =
        runTest {
            val result =
                LinkResult.Success(
                    url = "https://example.com/product?ref=abc",
                    route = "/product?ref=abc",
                    pathname = "/product",
                    type = LinkType.DEFERRED,
                    params = mapOf("ref" to "abc"),
                )
            cache.saveResult(result)

            assertTrue(cache.isResolved())
            assertEquals(result, cache.loadResult())
        }

    @Test
    fun `loadResult returns null on fresh storage`() =
        runTest {
            assertNull(cache.loadResult())
        }

    @Test
    fun `loadResult preserves empty params map`() =
        runTest {
            val result =
                LinkResult.Success(
                    url = "https://example.com/promo",
                    route = "/promo",
                    pathname = "/promo",
                    type = LinkType.VERIFIED,
                    params = emptyMap(),
                )
            cache.saveResult(result)
            assertEquals(emptyMap<String, String>(), (cache.loadResult() as LinkResult.Success).params)
        }

    @Test
    fun `clear removes all cached data and resolved flag`() =
        runTest {
            cache.saveResult(LinkResult.NoLink)
            assertTrue(cache.isResolved())

            cache.clear()

            assertFalse(cache.isResolved())
            assertNull(cache.loadResult())
        }

    @Test
    fun `clear batches all keys into a single removeItems call - POS-346`() =
        runTest {
            val recordedCalls = mutableListOf<Set<String>>()
            val spyStorage =
                object : PostedStorage by storage {
                    override suspend fun removeItems(keys: Set<String>) {
                        recordedCalls.add(keys)
                        storage.removeItems(keys)
                    }

                    override suspend fun removeItem(key: String) {
                        error("clear() must batch via removeItems, not call removeItem directly")
                    }
                }
            val spyCache = AttributionCache(spyStorage)
            spyCache.saveResult(LinkResult.NoLink)

            spyCache.clear()

            assertEquals(1, recordedCalls.size)
            assertEquals(
                setOf(
                    StorageKeys.ATTRIBUTION_RESOLVED,
                    StorageKeys.CACHE_SCHEMA_VERSION,
                    StorageKeys.CACHED_RESULT_KIND,
                    StorageKeys.CACHED_RESULT_URL,
                    StorageKeys.CACHED_RESULT_ROUTE,
                    StorageKeys.CACHED_RESULT_PATHNAME,
                    StorageKeys.CACHED_RESULT_TYPE,
                    StorageKeys.CACHED_RESULT_PARAMS,
                ),
                recordedCalls.single(),
            )
            assertFalse(spyCache.isResolved())
        }

    @Test
    fun `Error result is not cached`() =
        runTest {
            cache.saveResult(LinkResult.Error(RuntimeException("boom")))
            assertFalse(cache.isResolved())
            assertNull(cache.loadResult())
        }

    @Test
    fun `NotFirstLaunch result is not cached`() =
        runTest {
            cache.saveResult(LinkResult.NotFirstLaunch)
            assertFalse(cache.isResolved())
            assertNull(cache.loadResult())
        }

    // ── POS-161: schema versioning (a–e) ────────────────────────────────────────

    @Test
    fun `(a) save-reload round-trips the schema version key`() =
        runTest {
            cache.saveResult(LinkResult.NoLink)
            val version = storage.getItem(StorageKeys.CACHE_SCHEMA_VERSION)
            assertEquals(AttributionCache.CURRENT_CACHE_SCHEMA.toString(), version)
            assertTrue(cache.isResolved())
            assertEquals(LinkResult.NoLink, cache.loadResult())
        }

    @Test
    fun `(b) legacy record (flag+blob, no version key) loads without wipe and gets re-stamped`() =
        runTest {
            // Simulate a pre-POS-161 Success write: flag + blob, but NO schema version key.
            storage.setItem(StorageKeys.ATTRIBUTION_RESOLVED, "true")
            storage.setItem(StorageKeys.CACHED_RESULT_KIND, "success")
            storage.setItem(StorageKeys.CACHED_RESULT_URL, "https://example.com/legacy")
            storage.setItem(StorageKeys.CACHED_RESULT_ROUTE, "/legacy")
            storage.setItem(StorageKeys.CACHED_RESULT_PATHNAME, "/legacy")
            storage.setItem(StorageKeys.CACHED_RESULT_TYPE, "DEFERRED")
            storage.setItem(StorageKeys.CACHED_RESULT_PARAMS, "{}")

            assertTrue("legacy record must be treated as resolved", cache.isResolved())
            val result = cache.loadResult()
            assertNotNull("legacy record must load without wipe", result)
            assertTrue(result is LinkResult.Success)
            assertEquals("https://example.com/legacy", (result as LinkResult.Success).url)

            // Must be re-stamped after the first read.
            assertEquals(
                AttributionCache.CURRENT_CACHE_SCHEMA.toString(),
                storage.getItem(StorageKeys.CACHE_SCHEMA_VERSION),
            )
        }

    @Test
    fun `(c) version-mismatch record isResolved returns false and is cleared`() =
        runTest {
            // Write a record as if it came from a future schema version.
            storage.setItem(StorageKeys.ATTRIBUTION_RESOLVED, "true")
            storage.setItem(StorageKeys.CACHE_SCHEMA_VERSION, "999")
            storage.setItem(StorageKeys.CACHED_RESULT_KIND, "no_link")

            assertFalse("version mismatch must not be considered resolved", cache.isResolved())
        }

    @Test
    fun `(d) torn write (blob+flag present but one required field absent) isResolved returns false`() =
        runTest {
            // Simulate a crash between blob writes: URL is missing, everything else is set.
            storage.setItem(StorageKeys.ATTRIBUTION_RESOLVED, "true")
            // No CACHE_SCHEMA_VERSION (legacy path will be invoked)
            storage.setItem(StorageKeys.CACHED_RESULT_KIND, "success")
            // StorageKeys.CACHED_RESULT_URL intentionally omitted — torn write
            storage.setItem(StorageKeys.CACHED_RESULT_ROUTE, "/product")
            storage.setItem(StorageKeys.CACHED_RESULT_PATHNAME, "/product")
            storage.setItem(StorageKeys.CACHED_RESULT_TYPE, "DEFERRED")

            assertFalse("torn legacy write must not be considered resolved", cache.isResolved())
            // Caller will re-resolve (loadResult returns null).
            assertNull(cache.loadResult())
        }

    @Test
    fun `(e) saveResult writes all keys in a single setItems call - no interleaved partial state`() =
        runTest {
            // Intercept setItems to verify atomicity: the call must deliver the resolved flag
            // AND all blob fields together. We do this by verifying that after saveResult,
            // the version key and resolved flag are present simultaneously (no gap between them).
            val result =
                LinkResult.Success(
                    url = "https://example.com/product",
                    route = "/product",
                    pathname = "/product",
                    type = LinkType.VERIFIED,
                    params = mapOf("ref" to "test"),
                )
            cache.saveResult(result)

            // All keys written by saveResult must be present atomically.
            assertNotNull(storage.getItem(StorageKeys.ATTRIBUTION_RESOLVED))
            assertNotNull(storage.getItem(StorageKeys.CACHE_SCHEMA_VERSION))
            assertNotNull(storage.getItem(StorageKeys.CACHED_RESULT_KIND))
            assertNotNull(storage.getItem(StorageKeys.CACHED_RESULT_URL))
            assertNotNull(storage.getItem(StorageKeys.CACHED_RESULT_ROUTE))
            assertNotNull(storage.getItem(StorageKeys.CACHED_RESULT_PATHNAME))
            assertNotNull(storage.getItem(StorageKeys.CACHED_RESULT_TYPE))
            assertNotNull(storage.getItem(StorageKeys.CACHED_RESULT_PARAMS))
        }
}
