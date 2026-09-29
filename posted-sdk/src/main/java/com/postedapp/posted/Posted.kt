package com.postedapp.posted

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

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

    // Single-flight guard for first-launch resolution. Concurrent first callers share one
    // [resolveAndCache] so exactly one network resolve happens per launch (fire-once). The
    // shared work runs on [resolveScope] so it survives an individual caller's cancellation.
    // [resolveMutex] also guards [resetSession]: the clear and inFlight-null both happen under
    // the lock, so a resolveAppLaunch issued after resetSession() returns will always re-resolve.
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val resolveMutex = Mutex()
    private var inFlight: Deferred<LinkResult>? = null

    // Epoch the current [inFlight] resolution was created under; mutated only alongside
    // [inFlight] under [resolveMutex]. Compared against [resolveEpoch] to detect a resolution
    // that started before the most recent [resetSession] (mirrors iOS POS-267 / RN POS-154).
    private var inFlightEpoch: Int = -1

    // Bumped synchronously by resetSession()/resetSessionAndAwait() before the deferred clear
    // happens. A resolution snapshots this epoch when it starts; if the epoch has since moved on
    // by the time it settles, the resolution belongs to a stale (pre-reset) generation and must
    // not clobber the fresh (post-reset) resolution's fire-once cache write or inFlight pointer —
    // otherwise a de-duped caller would join/serve a stale result or trigger a duplicate resolve.
    private val resolveEpoch = AtomicInteger(0)

    // Set synchronously by resetSession() before the async clear begins. When true, the
    // resolveAppLaunch fast path is bypassed so it waits for the lock (and thus the clear).
    @Volatile
    private var clearPending: Boolean = false

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
     * Concurrent-caller intent capture: if multiple callers race [resolveAppLaunch] before the
     * first resolution completes, they are single-flighted onto one shared resolution (see
     * [resolveMutex]/[inFlight]) and only ONE [intent] is ever dispatched — the [intent] passed by
     * whichever caller wins the race to create the in-flight resolution. Every other concurrent
     * caller's [intent] argument is silently ignored for that resolution; those callers instead
     * receive the winner's [LinkResult]. This is correct for Android first-launch (the launching
     * Activity's intent is authoritative and callers racing it are typically passing the same or
     * no intent), but it means a caller that races in with a link-carrying [intent] while another
     * caller is already in flight with a different (or null) intent will NOT have its link
     * processed by that resolution — only by a later call, once the fire-once cache/in-flight
     * guard has cleared (e.g. via [resetSession]).
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
            // Fast path: already resolved and no reset in progress → return cached.
            if (!clearPending && attributionCache.isResolved()) {
                return attributionCache.loadResult() ?: resolveAndCache(intent, resolveEpoch.get())
            }

            // Slow path: single-flight the first resolution. Under the lock we double-check the
            // cache (a peer may have resolved while we waited) and either join the in-flight
            // resolve or start it. We await OUTSIDE the lock so waiters don't serialize on the
            // network call. [deferredEpoch] is the epoch the joined/created resolution snapshotted
            // at creation — used below to detect a stale (pre-reset) settle.
            val (deferred, deferredEpoch) =
                resolveMutex.withLock {
                    // Honor a pending reset first: clear exactly once under the lock so the
                    // re-resolve below observes an empty cache. Doing the clear here (instead of
                    // in a separate coroutine launched by resetSession) removes the lock-ordering
                    // race where a resolve could win the mutex and serve the stale cache.
                    if (clearPending) {
                        attributionCache.clear()
                        inFlight = null
                        clearPending = false
                    }
                    if (attributionCache.isResolved()) {
                        null to resolveEpoch.get()
                    } else {
                        val existing = inFlight
                        if (existing != null) {
                            existing to inFlightEpoch
                        } else {
                            val epoch = resolveEpoch.get()
                            inFlightEpoch = epoch
                            resolveScope.async { resolveAndCache(intent, epoch) }.also { inFlight = it } to epoch
                        }
                    }
                }

            if (deferred == null) {
                attributionCache.loadResult() ?: resolveAndCache(intent, deferredEpoch)
            } else {
                try {
                    deferred.await()
                } finally {
                    // Re-arm for a later reset/re-resolve, but only if no fresher (post-reset)
                    // resolution has already replaced this pointer. Gated on BOTH identity and
                    // epoch so a stale resolution settling after resetSession() can never clobber
                    // the fresh resolution's inFlight pointer.
                    resolveMutex.withLock {
                        if (inFlight === deferred && resolveEpoch.get() == deferredEpoch) {
                            inFlight = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Posted:RUNTIME_ERROR] Error in resolveAppLaunch", e)
            LinkResult.Error(e)
        }
    }

    /**
     * Re-arm the fire-once gate so the next [resolveAppLaunch] call performs a fresh network
     * resolution. Non-blocking: never touches storage or `runBlocking` on the calling thread —
     * it only sets a volatile flag, so it is safe to call from the main/UI thread.
     *
     * Contract: after [resetSession] returns, the next [resolveAppLaunch] clears the cache under
     * the resolve lock and re-resolves (makes a fresh network call). The actual disk clear is
     * deferred to that next resolve; the stale on-disk record is inert until then because every
     * read path honors the pending-reset flag.
     *
     * Typical use: test harnesses, QA re-installs, or user account reset flows.
     *
     * If you need the cache physically cleared before proceeding (not just re-armed), use
     * [resetSessionAndAwait] instead.
     */
    @JvmStatic
    fun resetSession() {
        if (!isInitialized) {
            Log.w(TAG, "SDK not initialized. Call Posted.initialize() first.")
            return
        }
        // Bump the epoch synchronously, before marking the reset, so any resolution already in
        // flight is stamped stale up front: its eventual cache write and inFlight-clear will be
        // gated out by the epoch mismatch. The next resolveAppLaunch performs the clear under
        // resolveMutex, so there is no lock-ordering race and no coroutine to leak/deadlock.
        resolveEpoch.incrementAndGet()
        clearPending = true
    }

    /**
     * Suspend variant of [resetSession] that awaits the cache clear before returning.
     * Safe to call from any dispatcher — never blocks the main thread.
     */
    @JvmStatic
    suspend fun resetSessionAndAwait() {
        if (!isInitialized) {
            Log.w(TAG, "SDK not initialized. Call Posted.initialize() first.")
            return
        }
        resolveEpoch.incrementAndGet()
        clearPending = true
        resolveMutex.withLock {
            inFlight = null
            attributionCache.clear()
            clearPending = false
        }
    }

    // ── Deprecated shims (kept one cycle) ────────────────────────────────────

    // Methods that have already logged their deprecation notice this session, keyed per-shim
    // so each of the 5 shims warns independently. Thread-safe: warnOnce may be invoked
    // concurrently from different callers/dispatchers.
    private val warnedDeprecations = Collections.synchronizedSet(mutableSetOf<String>())

    private fun warnOnce(
        method: String,
        message: String,
    ) {
        if (warnedDeprecations.add(method)) {
            Log.w(TAG, message)
        }
    }

    /**
     * Re-arms all deprecation-warning de-dup so the next invocation of each shim logs again.
     * Test/QA seam only — not part of the public API surface.
     */
    @VisibleForTesting
    internal fun resetDeprecationWarnings() {
        warnedDeprecations.clear()
    }

    /**
     * @deprecated Use [resolveAppLaunch] instead.
     */
    @Deprecated(
        message = "Use resolveAppLaunch instead",
        replaceWith = ReplaceWith("resolveAppLaunch(intent)"),
    )
    @JvmStatic
    suspend fun resolveInitialLink(intent: Intent): LinkResult {
        warnOnce(
            "resolveInitialLink(Intent)",
            "[Posted] resolveInitialLink() is deprecated and will be removed in 4.0.0. " +
                "Use resolveAppLaunch() instead.",
        )
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
        warnOnce(
            "resolveInitialLink()",
            "[Posted] resolveInitialLink() is deprecated and will be removed in 4.0.0. " +
                "Use resolveAppLaunch() instead.",
        )
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
        warnOnce(
            "processLink",
            "[Posted] processLink() is deprecated and will be removed in 4.0.0. Use resolveAppLaunch() instead.",
        )
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
        warnOnce(
            "getDeferredLink",
            "[Posted] getDeferredLink() is deprecated and will be removed in 4.0.0. Use resolveAppLaunch() instead.",
        )
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
        warnOnce(
            "resolveShortLink",
            "[Posted] resolveShortLink() is deprecated and will be removed in 4.0.0. Use resolveAppLaunch() instead.",
        )
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

        // Short link resolution: single-segment path on the known short-link host
        if (UrlHelpers.isSingleSegmentPath(uri) && apiClient.isKnownShortLinkHost(uri.host.orEmpty())) {
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

    private suspend fun resolveAndCache(
        intent: Intent?,
        epoch: Int,
    ): LinkResult {
        val result = dispatchResolution(intent)
        // Only persist if no resetSession() has landed since this resolution started — otherwise
        // this is a stale (pre-reset) result and writing it would clobber the fresh post-reset
        // resolution's cache entry.
        if (result !is LinkResult.Error && resolveEpoch.get() == epoch) {
            try {
                attributionCache.saveResult(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Fire-once flag not armed; result still returned, next launch re-resolves (cost/attribution drift).
                Log.d(TAG, "[Posted:STORAGE_WRITE_FAILED] Failed to persist fire-once cache; next launch will re-resolve.", e)
            }
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
}
