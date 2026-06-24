package com.swmansion.detour

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.swmansion.detour.analytics.DetourAnalytics
import com.swmansion.detour.api.DetourApiClient
import com.swmansion.detour.fingerprint.FingerprintCollector
import com.swmansion.detour.models.DeterministicFingerprint
import com.swmansion.detour.models.LinkProcessingMode
import com.swmansion.detour.models.LinkResult
import com.swmansion.detour.models.LinkType
import com.swmansion.detour.models.ShortLinkResponse
import com.swmansion.detour.referrer.InstallReferrerHelper
import com.swmansion.detour.storage.DefaultStorageProvider
import com.swmansion.detour.storage.DetourStorage
import com.swmansion.detour.storage.FirstLaunchDetector
import com.swmansion.detour.utils.UrlHelpers
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main entry point for Detour SDK.
 *
 * Usage:
 * ```
 * // Initialize in Application.onCreate() or Activity.onCreate()
 * val config = DetourConfig(
 *     appId = "your-app-id",
 *     apiKey = "your-api-key"
 * )
 * Detour.initialize(context, config)
 *
 * // Process any link (Verified, Scheme, or Deferred) in onCreate()
 * lifecycleScope.launch {
 *     when (val result = Detour.processLink(intent)) {
 *         is LinkResult.Success -> {
 *             // Navigate to result.route
 *             // Access result.params, result.pathname, result.type
 *         }
 *         is LinkResult.NoLink -> { /* Normal app launch */ }
 *         is LinkResult.NotFirstLaunch -> { /* Already processed deferred link */ }
 *         is LinkResult.Error -> { /* Handle error */ }
 *     }
 * }
 * ```
 */
object Detour {
    private const val TAG = "Detour"

    @Volatile
    private var isInitialized = false

    private val sessionHandled = AtomicBoolean(false)

    private lateinit var applicationContext: Context
    private lateinit var config: DetourConfig
    private lateinit var storage: DetourStorage
    private lateinit var apiClient: DetourApiClient
    private lateinit var fingerprintCollector: FingerprintCollector
    private lateinit var installReferrerHelper: InstallReferrerHelper
    private lateinit var firstLaunchDetector: FirstLaunchDetector

    /**
     * Initialize Detour SDK.
     * Call this once in Application.onCreate() or before using processLink() or getDeferredLink().
     *
     * @param context Application or Activity context
     * @param config Detour configuration
     */
    @JvmStatic
    @Synchronized
    fun initialize(
        context: Context,
        config: DetourConfig,
    ) {
        if (isInitialized) {
            Log.w(TAG, "SDK already initialized")
            return
        }

        if (config.apiKey.isBlank() || config.appId.isBlank()) {
            Log.e(
                TAG,
                "[Detour] apiKey or appId is missing — SDK will not process links. " +
                    "Set your credentials in DetourConfig.",
            )
            return
        }

        this.applicationContext = context.applicationContext
        this.config = config
        this.storage = config.storage ?: DefaultStorageProvider(context.applicationContext)
        this.apiClient = DetourApiClient(config, context.applicationContext)
        this.fingerprintCollector = FingerprintCollector(context.applicationContext)
        this.installReferrerHelper = InstallReferrerHelper(context.applicationContext)
        this.firstLaunchDetector = FirstLaunchDetector(storage)

        // Initialize analytics subsystem
        DetourAnalytics.initialize(config, storage)
        DetourAnalytics.logAppOpenIfNeeded()

        this.isInitialized = true
        Log.d(TAG, "SDK initialized")
    }

    /**
     * Process intent and extract deep link.
     * Automatically detects link type (Universal, Scheme, or Deferred) and returns appropriate result.
     *
     * Priority order:
     * 1. Universal App Link (if intent contains ACTION_VIEW with http/https data)
     * 2. Scheme Link (if intent contains ACTION_VIEW with custom scheme and mode is ALL)
     * 3. Deferred Deep Link (API call, only on first launch)
     *
     * In [LinkProcessingMode.DEFERRED_ONLY] mode, steps 1-2 are skipped entirely.
     *
     * @param intent Activity intent to process
     * @return LinkResult with link data or status
     */
    @JvmStatic
    suspend fun processLink(intent: Intent): LinkResult {
        if (!isInitialized) {
            return LinkResult.Error(
                IllegalStateException("Detour SDK not initialized. Call Detour.initialize() first."),
            )
        }

        return try {
            // Step 1: Try to extract link from intent (unless DEFERRED_ONLY)
            if (config.linkProcessingMode != LinkProcessingMode.DEFERRED_ONLY) {
                if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
                    val uri = intent.data!!
                    val uriString = uri.toString()

                    if (UrlHelpers.isWebUrl(uriString)) {
                        // Mark first entrance so subsequent launches don't try deferred matching
                        firstLaunchDetector.markAsLaunched()
                        return processUniversalLink(uri)
                    }

                    if (config.linkProcessingMode == LinkProcessingMode.ALL) {
                        return processSchemeLink(uri)
                    }
                }
            }

            // Step 2: Fallback to deferred link
            processDeferredLink()
        } catch (e: Exception) {
            Log.e(TAG, "[Detour:RUNTIME_ERROR] Error processing link", e)
            LinkResult.Error(e)
        }
    }

    /**
     * Get deferred link only (ignore Universal/Scheme Links).
     * Use this if you want to handle other link types separately.
     *
     * This should be called once per app session, typically in your main Activity.
     * The function is idempotent - multiple calls in the same session will return NotFirstLaunch.
     *
     * @return LinkResult.Success with DEFERRED type, or NotFirstLaunch/NoLink/Error
     */
    @JvmStatic
    suspend fun getDeferredLink(): LinkResult {
        if (!isInitialized) {
            return LinkResult.Error(
                IllegalStateException("Detour SDK not initialized. Call Detour.initialize() first."),
            )
        }

        return try {
            processDeferredLink()
        } catch (e: Exception) {
            Log.e(TAG, "[Detour:RUNTIME_ERROR] Error processing deferred link", e)
            LinkResult.Error(e)
        }
    }

    /**
     * Resolve a short link URL to its full link data.
     *
     * @param url The short link URL to resolve
     * @return ShortLinkResponse or null on 404/error
     */
    @JvmStatic
    suspend fun resolveShortLink(url: String): ShortLinkResponse? {
        if (!isInitialized) {
            Log.w(TAG, "SDK not initialized. Call Detour.initialize() first.")
            return null
        }

        return apiClient.resolveShortLink(url)
    }

    /**
     * Process Universal App Link from URI.
     * If the URI has a single path segment, attempt short-link resolution first.
     */
    private suspend fun processUniversalLink(uri: Uri): LinkResult {
        Log.d(TAG, "Processing Universal Link")

        val link = uri.toString()
        val clickResult = apiClient.sendUniversalLinkClick(link, UrlHelpers.parseQueryParams(link))
        if (!clickResult.allowed) {
            val msg =
                "[Detour:CLICK_LIMIT_ERROR] Universal/App link blocked: url=$link " +
                    "error=${clickResult.error} code=${clickResult.code} " +
                    "clicksInPeriod=${clickResult.clicksInPeriod} effectiveLimit=${clickResult.effectiveLimit}"
            Log.e(TAG, msg)
            return LinkResult.NoLink
        }

        // Short link resolution: single-segment path indicates a short link
        if (UrlHelpers.isSingleSegmentPath(uri)) {
            val shortLinkResult = apiClient.resolveShortLink(link)
            if (shortLinkResult?.link != null) {
                Log.d(TAG, "Short link resolved successfully")
                // Re-process the resolved link through parseRoute (matches RN SDK's recursive resolveLink)
                val route = UrlHelpers.parseRoute(shortLinkResult.link) ?: "/"
                return LinkResult.Success(
                    url = shortLinkResult.link,
                    route = route,
                    pathname = UrlHelpers.extractPathname(route),
                    type = LinkType.VERIFIED,
                    params = UrlHelpers.parseQueryParams(shortLinkResult.link),
                )
            }
        }

        val route = UrlHelpers.parseRoute(link) ?: "/"

        return LinkResult.Success(
            url = link,
            route = route,
            pathname = UrlHelpers.extractPathname(route),
            type = LinkType.VERIFIED,
            params = UrlHelpers.parseQueryParams(link),
        )
    }

    /**
     * Process custom-scheme deep link.
     * Extracts route as host + path + query, matching RN SDK's `getRouteFromDeepLink()`.
     */
    private fun processSchemeLink(uri: Uri): LinkResult {
        Log.d(TAG, "Processing Scheme Link")

        val link = uri.toString()
        val route = UrlHelpers.getRouteFromDeepLink(uri)

        return LinkResult.Success(
            url = link,
            route = route,
            pathname = UrlHelpers.extractPathname(route),
            type = LinkType.SCHEME,
            params = UrlHelpers.parseQueryParams(link),
        )
    }

    /**
     * Process Deferred Deep Link via API.
     */
    private suspend fun processDeferredLink(): LinkResult {
        // Prevent duplicate API calls in same session (atomic guard).
        if (!sessionHandled.compareAndSet(false, true)) {
            Log.d(TAG, "Session already handled")
            return LinkResult.NotFirstLaunch
        }

        // Check if first launch
        if (!firstLaunchDetector.isFirstLaunch()) {
            Log.d(TAG, "Not first launch")
            return LinkResult.NotFirstLaunch
        }

        // Mark as launched
        firstLaunchDetector.markAsLaunched()
        Log.d(TAG, "First launch detected, processing deferred link")

        // Try to get click ID from install referrer (deterministic matching)
        val clickId = installReferrerHelper.getClickId()

        val link: String? =
            if (clickId != null) {
                Log.d(TAG, "Using deterministic matching with click_id")
                val fingerprint = DeterministicFingerprint(clickId)
                apiClient.matchLink(fingerprint)
            } else {
                Log.d(TAG, "The click_id not found, using probabilistic matching")
                val fingerprint = fingerprintCollector.collectFingerprint()
                apiClient.matchLink(fingerprint)
            }

        if (link == null) {
            Log.d(TAG, "No deferred link matched")
            return LinkResult.NoLink
        }

        // Parse route from link (strips app hash, extracts path)
        val route = UrlHelpers.parseRoute(link) ?: "/"

        Log.d(TAG, "Deferred link matched successfully")
        return LinkResult.Success(
            url = link,
            route = route,
            pathname = UrlHelpers.extractPathname(route),
            type = LinkType.DEFERRED,
            params = UrlHelpers.parseQueryParams(link),
        )
    }
}
