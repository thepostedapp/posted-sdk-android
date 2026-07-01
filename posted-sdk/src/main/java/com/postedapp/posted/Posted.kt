package com.postedapp.posted

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.postedapp.posted.analytics.PostedAnalytics
import com.postedapp.posted.api.PostedApiClient
import com.postedapp.posted.fingerprint.FingerprintCollector
import com.postedapp.posted.models.DeterministicFingerprint
import com.postedapp.posted.models.LinkProcessingMode
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType
import com.postedapp.posted.models.ShortLinkResponse
import com.postedapp.posted.referrer.InstallReferrerHelper
import com.postedapp.posted.storage.AttributionCache
import com.postedapp.posted.storage.DefaultStorageProvider
import com.postedapp.posted.storage.FirstLaunchDetector
import com.postedapp.posted.storage.PostedStorage
import com.postedapp.posted.utils.UrlHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Main entry point for the posted-sdk SDK.
 *
 * Usage:
 * ```
 * // Initialize in Application.onCreate() or Activity.onCreate()
 * val config = PostedConfig(
 *     appId = "your-app-id",
 *     apiKey = "your-api-key"
 * )
 * Posted.initialize(context, config)
 *
 * // Resolve attribution for this launch (fire-once, cached across installs)
 * lifecycleScope.launch {
 *     when (val result = Posted.resolveAppLaunch(intent)) {
 *         is LinkResult.Success -> {
 *             // Navigate to result.route
 *             // Access result.params, result.pathname, result.type
 *         }
 *         is LinkResult.NoLink -> { /* Normal app launch */ }
 *         is LinkResult.Error -> { /* Handle error */ }
 *     }
 * }
 * ```
 */
object Posted {
    private const val TAG = "Posted"

    @Volatile
    private var isInitialized = false

    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var applicationContext: Context
    private lateinit var config: PostedConfig
    private lateinit var storage: PostedStorage
    private lateinit var apiClient: PostedApiClient
    private lateinit var fingerprintCollector: FingerprintCollector
    private lateinit var installReferrerHelper: InstallReferrerHelper
    private lateinit var firstLaunchDetector: FirstLaunchDetector
    private lateinit var attributionCache: AttributionCache

    /**
     * Initialize the posted-sdk SDK.
     * Call this once in Application.onCreate() or before using resolveAppLaunch().
     *
     * @param context Application or Activity context
     * @param config posted-sdk configuration
     */
    @JvmStatic
    @Synchronized
    fun initialize(
        context: Context,
        config: PostedConfig,
    ) {
        if (isInitialized) {
            Log.w(TAG, "SDK already initialized")
            return
        }

        if (config.apiKey.isBlank() || config.appId.isBlank()) {
            Log.e(
                TAG,
                "[Posted] apiKey or appId is missing — SDK will not process links. " +
                    "Set your credentials in PostedConfig.",
            )
            return
        }

        this.applicationContext = context.applicationContext
        this.config = config
        this.storage = config.storage ?: DefaultStorageProvider(context.applicationContext)
        this.apiClient = PostedApiClient(config, context.applicationContext)
        this.fingerprintCollector = FingerprintCollector(context.applicationContext)
        this.installReferrerHelper = InstallReferrerHelper(context.applicationContext)
        this.firstLaunchDetector = FirstLaunchDetector(this.storage)
        this.attributionCache = AttributionCache(this.storage)

        // One-time migration of SharedPreferences keys from Detour_ prefix to Posted_ prefix
        migrationScope.launch { migrateStorageKeysIfNeeded() }

        // Initialize analytics subsystem
        PostedAnalytics.initialize(config, this.storage)
        PostedAnalytics.logAppOpenIfNeeded()

        this.isInitialized = true
        Log.d(TAG, "SDK initialized")
    }

    /**
     * Resolve attribution for this app launch. Fire-once per install: the first call makes one
     * network request and caches the result; all subsequent calls (including across app restarts
     * and updates) return the cached [LinkResult] without any network call.
     *
     * Two-branch dispatch:
     * - LINK-PRESENT: [intent] carries ACTION_VIEW with an http/https URL →
     *   resolve-short / universal-link-click path (same as [processLink]).
     * - DEFERRED: null [intent] or no link URL → match-link with device fingerprint.
     *
     * Cache re-arms on app reinstall (OS clears storage) or explicit [resetSession].
     *
     * @param intent Activity intent ([Activity.getIntent] or [Activity.onNewIntent]).
     *               Pass null for deferred-only (background / cold-start without a link).
     * @return [LinkResult] — same shape as today; never throws.
     */
    @JvmStatic
    suspend fun resolveAppLaunch(intent: Intent? = null): LinkResult {
        if (!isInitialized) {
            return LinkResult.Error(
                IllegalStateException("posted-sdk not initialized. Call Posted.initialize() first."),
            )
        }

        return try {
            if (attributionCache.isResolved()) {
                return attributionCache.loadResult() ?: resolveAndCache(intent)
            }
            resolveAndCache(intent)
        } catch (e: Exception) {
            Log.e(TAG, "[Posted:RUNTIME_ERROR] Error in resolveAppLaunch", e)
            LinkResult.Error(e)
        }
    }

    /**
     * Re-arm the fire-once gate so the next [resolveAppLaunch] call performs a fresh network
     * resolution.
     *
     * Typical use: test harnesses, QA re-installs, or user account reset flows.
     */
    @JvmStatic
    fun resetSession() {
        if (!isInitialized) {
            Log.w(TAG, "SDK not initialized. Call Posted.initialize() first.")
            return
        }
        runBlocking { attributionCache.clear() }
    }

    // ── Deprecated shims (kept one cycle) ────────────────────────────────────

    /**
     * @deprecated Use [resolveAppLaunch] instead.
     */
    @Deprecated(
        message = "Use resolveAppLaunch instead",
        replaceWith = ReplaceWith("resolveAppLaunch(intent)"),
    )
    @JvmStatic
    suspend fun resolveInitialLink(intent: Intent): LinkResult {
        Log.w(TAG, "[Posted] resolveInitialLink(intent) is deprecated. Use resolveAppLaunch(intent) instead.")
        return resolveAppLaunch(intent)
    }

    /**
     * @deprecated Use [resolveAppLaunch] instead.
     */
    @Deprecated(
        message = "Use resolveAppLaunch instead",
        replaceWith = ReplaceWith("resolveAppLaunch()"),
    )
    @JvmStatic
    suspend fun resolveInitialLink(): LinkResult {
        Log.w(TAG, "[Posted] resolveInitialLink() is deprecated. Use resolveAppLaunch() instead.")
        return resolveAppLaunch(null)
    }

    /**
     * @deprecated Use [resolveAppLaunch] instead.
     */
    @Deprecated(
        message = "Use resolveAppLaunch instead",
        replaceWith = ReplaceWith("resolveAppLaunch(intent)"),
    )
    @JvmStatic
    suspend fun processLink(intent: Intent): LinkResult {
        Log.w(TAG, "[Posted] processLink() is deprecated. Use resolveAppLaunch(intent) instead.")
        return resolveAppLaunch(intent)
    }

    /**
     * @deprecated Use [resolveAppLaunch] instead.
     */
    @Deprecated(
        message = "Use resolveAppLaunch instead",
        replaceWith = ReplaceWith("resolveAppLaunch()"),
    )
    @JvmStatic
    suspend fun getDeferredLink(): LinkResult {
        Log.w(TAG, "[Posted] getDeferredLink() is deprecated. Use resolveAppLaunch() instead.")
        return resolveAppLaunch(null)
    }

    /**
     * @deprecated resolveShortLink is for internal use only. Use [resolveAppLaunch] for link resolution.
     */
    @Deprecated(
        message = "resolveShortLink is for internal use only. Use resolveAppLaunch for link resolution.",
        replaceWith = ReplaceWith("resolveAppLaunch(intent)"),
    )
    @JvmStatic
    suspend fun resolveShortLink(url: String): ShortLinkResponse? {
        Log.w(TAG, "[Posted] resolveShortLink() is deprecated. Use resolveAppLaunch() for link resolution.")
        if (!isInitialized) {
            Log.w(TAG, "SDK not initialized. Call Posted.initialize() first.")
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
            Log.e(
                TAG,
                "[Posted:CLICK_LIMIT_ERROR] Universal/App link blocked: url=$link " +
                    "error=${clickResult.error} code=${clickResult.code} " +
                    "clicksInPeriod=${clickResult.clicksInPeriod} " +
                    "effectiveLimit=${clickResult.effectiveLimit}",
            )
            return LinkResult.NoLink
        }

        // Short link resolution: single-segment path indicates a short link
        if (UrlHelpers.isSingleSegmentPath(uri)) {
            val shortLinkResult = apiClient.resolveShortLink(link)
            if (shortLinkResult?.link != null) {
                Log.d(TAG, "Short link resolved successfully")
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

    private suspend fun resolveAndCache(intent: Intent?): LinkResult {
        val result = dispatchResolution(intent)
        if (result !is LinkResult.Error) {
            attributionCache.saveResult(result)
        }
        return result
    }

    private suspend fun dispatchResolution(intent: Intent?): LinkResult {
        val uri =
            if (intent?.action == Intent.ACTION_VIEW) intent.data else null

        return when {
            uri != null && UrlHelpers.isWebUrl(uri.toString()) -> {
                firstLaunchDetector.markAsLaunched()
                processUniversalLink(uri)
            }
            uri != null && config.linkProcessingMode == LinkProcessingMode.ALL -> {
                firstLaunchDetector.markAsLaunched()
                processSchemeLink(uri)
            }
            else -> resolveDeferredAttribution()
        }
    }

    private suspend fun resolveDeferredAttribution(): LinkResult {
        firstLaunchDetector.markAsLaunched()
        val clickId = installReferrerHelper.getClickId()
        val link: String? =
            if (clickId != null) {
                apiClient.matchLink(DeterministicFingerprint(clickId))
            } else {
                apiClient.matchLink(fingerprintCollector.collectFingerprint())
            }

        if (link == null) return LinkResult.NoLink

        val route = UrlHelpers.parseRoute(link) ?: "/"
        return LinkResult.Success(
            url = link,
            route = route,
            pathname = UrlHelpers.extractPathname(route),
            type = LinkType.DEFERRED,
            params = UrlHelpers.parseQueryParams(link),
        )
    }

    /**
     * One-time migration of SharedPreferences keys from the old Detour_ prefix to the new Posted_ prefix.
     * Read legacy key → write new key → delete legacy key (if legacy key exists).
     */
    private suspend fun migrateStorageKeysIfNeeded() {
        val legacyFlag = storage.getItem("Detour_firstEntranceFlag")
        if (legacyFlag != null) {
            storage.setItem("Posted_firstEntranceFlag", legacyFlag)
            storage.removeItem("Detour_firstEntranceFlag")
        }

        val legacyDeviceId = storage.getItem("Detour_deviceId")
        if (legacyDeviceId != null) {
            storage.setItem("Posted_deviceId", legacyDeviceId)
            storage.removeItem("Detour_deviceId")
        }
    }
}
