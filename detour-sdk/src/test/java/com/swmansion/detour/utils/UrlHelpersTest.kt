package com.swmansion.detour.utils

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlHelpersTest {
    // --- parseRoute: full URLs ---

    @Test
    fun `parseRoute - full URL strips first path segment (app hash)`() {
        val result = UrlHelpers.parseRoute("https://example.com/app-hash/product/123")
        assertEquals("/product/123", result)
    }

    @Test
    fun `parseRoute - protocol-relative URL strips first segment`() {
        val result = UrlHelpers.parseRoute("//example.com/app-hash/product/123")
        assertEquals("/product/123", result)
    }

    @Test
    fun `parseRoute - full URL with query params`() {
        val result = UrlHelpers.parseRoute("https://example.com/app-hash/product/123?color=red&size=L")
        assertEquals("/product/123?color=red&size=L", result)
    }

    @Test
    fun `parseRoute - single segment URL path returns root`() {
        val result = UrlHelpers.parseRoute("https://example.com/app-hash")
        assertEquals("/", result)
    }

    // --- parseRoute: path-only strings (also strip first segment) ---

    @Test
    fun `parseRoute - path with leading slash strips first segment`() {
        val result = UrlHelpers.parseRoute("/app-hash/product/123")
        assertEquals("/product/123", result)
    }

    @Test
    fun `parseRoute - path without leading slash strips first segment`() {
        val result = UrlHelpers.parseRoute("app-hash/product/123")
        assertEquals("/product/123", result)
    }

    @Test
    fun `parseRoute - path with query strips first segment and preserves query`() {
        val result = UrlHelpers.parseRoute("/app-hash/product/123?color=red")
        assertEquals("/product/123?color=red", result)
    }

    @Test
    fun `parseRoute - single segment path returns root`() {
        val result = UrlHelpers.parseRoute("/app-hash")
        assertEquals("/", result)
    }

    @Test
    fun `parseRoute - blank input returns null`() {
        assertNull(UrlHelpers.parseRoute(""))
        assertNull(UrlHelpers.parseRoute("   "))
        assertNull(UrlHelpers.parseRoute(null))
    }

    // --- isWebUrl ---

    @Test
    fun `isWebUrl - http scheme`() {
        assertTrue(UrlHelpers.isWebUrl("http://example.com"))
    }

    @Test
    fun `isWebUrl - https scheme`() {
        assertTrue(UrlHelpers.isWebUrl("https://example.com"))
    }

    @Test
    fun `isWebUrl - protocol relative`() {
        assertTrue(UrlHelpers.isWebUrl("//example.com/path"))
    }

    @Test
    fun `isWebUrl - custom scheme returns false`() {
        assertFalse(UrlHelpers.isWebUrl("myapp://product/123"))
    }

    @Test
    fun `isWebUrl - plain path returns false`() {
        assertFalse(UrlHelpers.isWebUrl("/product/123"))
    }

    // --- isSingleSegmentPath ---

    @Test
    fun `isSingleSegmentPath - single segment`() {
        val uri = Uri.parse("https://example.com/abc123")
        assertTrue(UrlHelpers.isSingleSegmentPath(uri))
    }

    @Test
    fun `isSingleSegmentPath - multi segment`() {
        val uri = Uri.parse("https://example.com/app-hash/product/123")
        assertFalse(UrlHelpers.isSingleSegmentPath(uri))
    }

    @Test
    fun `isSingleSegmentPath - empty path`() {
        val uri = Uri.parse("https://example.com")
        assertFalse(UrlHelpers.isSingleSegmentPath(uri))
    }

    // --- getRouteFromDeepLink ---

    @Test
    fun `getRouteFromDeepLink - extracts host and path`() {
        val uri = Uri.parse("myapp://product/123")
        assertEquals("/product/123", UrlHelpers.getRouteFromDeepLink(uri))
    }

    @Test
    fun `getRouteFromDeepLink - includes query params`() {
        val uri = Uri.parse("myapp://product/123?color=red")
        assertEquals("/product/123?color=red", UrlHelpers.getRouteFromDeepLink(uri))
    }

    @Test
    fun `getRouteFromDeepLink - host only`() {
        val uri = Uri.parse("myapp://home")
        assertEquals("/home", UrlHelpers.getRouteFromDeepLink(uri))
    }

    @Test
    fun `getRouteFromDeepLink - hostless URI keeps single leading slash`() {
        val uri = Uri.parse("myapp:///product/123?color=red")
        assertEquals("/product/123?color=red", UrlHelpers.getRouteFromDeepLink(uri))
    }

    // --- parseQueryParams ---

    @Test
    fun `parseQueryParams - extracts key-value pairs`() {
        val params = UrlHelpers.parseQueryParams("/products/123?color=red&size=L")
        assertEquals(mapOf("color" to "red", "size" to "L"), params)
    }

    @Test
    fun `parseQueryParams - returns empty map for no query`() {
        val params = UrlHelpers.parseQueryParams("/products/123")
        assertEquals(emptyMap<String, String>(), params)
    }

    @Test
    fun `parseQueryParams - handles encoded values`() {
        val params = UrlHelpers.parseQueryParams("/search?q=hello+world&lang=en")
        assertEquals("hello world", params["q"])
        assertEquals("en", params["lang"])
    }

    @Test
    fun `parseQueryParams - handles full URL`() {
        val params = UrlHelpers.parseQueryParams("https://example.com/hash/products/123?id=42")
        assertEquals(mapOf("id" to "42"), params)
    }

    // --- extractPathname ---

    @Test
    fun `extractPathname - removes query string`() {
        assertEquals("/products/123", UrlHelpers.extractPathname("/products/123?color=red"))
    }

    @Test
    fun `extractPathname - no query string returns as-is`() {
        assertEquals("/products/123", UrlHelpers.extractPathname("/products/123"))
    }

    @Test
    fun `extractPathname - blank input returns root`() {
        assertEquals("/", UrlHelpers.extractPathname(null))
        assertEquals("/", UrlHelpers.extractPathname(""))
    }
}
