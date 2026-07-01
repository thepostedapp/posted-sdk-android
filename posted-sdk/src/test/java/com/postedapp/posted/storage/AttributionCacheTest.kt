package com.postedapp.posted.storage

import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType
import com.postedapp.posted.test.InMemoryStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
