package com.postedapp.posted

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.postedapp.posted.api.PostedApiClient
import com.postedapp.posted.install.InstallContextCollector
import com.postedapp.posted.referrer.InstallReferrerHelper
import com.postedapp.posted.storage.DefaultStorageProvider
import com.postedapp.posted.storage.DeviceIdProvider
import com.postedapp.posted.storage.PostedStorage
import com.postedapp.posted.storage.StorageKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reports app installs to Posted, so the backend can match each install to the ad click that
 * led to it and report it back to the ad network as a conversion.
 *
 * The SDK has one entry point, [reportInstall]. Call it on every app launch; it sends the report
 * once per install.
 *
 * ```kotlin
 * // Application.onCreate()
 * Posted.reportInstall(this, PostedConfig(apiKey = "<YOUR_API_KEY>", appId = "<YOUR_APP_ID>"))
 * ```
 */
object Posted {
    private const val TAG = "Posted"

    // Reports run on their own scope so they survive the caller's lifecycle, and the single
    // in-flight report is shared so concurrent callers on first launch make one request.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var inFlight: Deferred<Unit>? = null

    /**
     * Reports this install for ad attribution.
     *
     * Safe to call on every launch: the install is sent once, and retried on later launches until
     * the backend accepts it. Never throws, never blocks the caller, and never reads the clipboard.
     * Android installs include the Play install referrer `click_id` when present.
     */
    @JvmStatic
    fun reportInstall(
        context: Context,
        config: PostedConfig,
    ) {
        val appContext = context.applicationContext
        scope.launch { reportInstallAndAwait(appContext, config) }
    }

    /**
     * Suspending variant of [reportInstall] that completes when the report has been sent (or has
     * failed for this launch). Never throws.
     */
    @JvmStatic
    suspend fun reportInstallAndAwait(
        context: Context,
        config: PostedConfig,
    ) {
        reportInstallAndAwait(
            context.applicationContext,
            config,
            PostedApiClient(config),
            InstallReferrerHelper(context.applicationContext),
        )
    }

    @VisibleForTesting
    internal suspend fun reportInstallAndAwait(
        context: Context,
        config: PostedConfig,
        apiClient: PostedApiClient,
        installReferrerHelper: InstallReferrerHelper,
    ) {
        val deferred =
            mutex.withLock {
                inFlight ?: scope.async(start = CoroutineStart.LAZY) {
                    send(context, config, apiClient, installReferrerHelper)
                }.also { created ->
                    inFlight = created
                    // Cleared by identity once the shared report settles, so a later launch (or
                    // a retry after a failure) starts a fresh request instead of joining a
                    // finished one.
                    created.invokeOnCompletion {
                        scope.launch { mutex.withLock { if (inFlight === created) inFlight = null } }
                    }
                }
            }
        deferred.await()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun send(
        context: Context,
        config: PostedConfig,
        apiClient: PostedApiClient,
        installReferrerHelper: InstallReferrerHelper,
    ) {
        try {
            if (config.apiKey.isBlank() || config.appId.isBlank()) {
                Log.e(TAG, "[Posted:INSTALL_ERROR] apiKey or appId is missing; the install is not reported")
                return
            }
            val storage: PostedStorage = config.storage ?: DefaultStorageProvider(context)
            if (storage.getItem(StorageKeys.INSTALL_REPORTED) == "true") return

            val clickId = installReferrerHelper.getClickId()
            val installContext = InstallContextCollector(context).collectInstallContext()
            val deviceId = DeviceIdProvider(storage).getDeviceId()

            if (apiClient.reportInstall(installContext, deviceId, clickId)) {
                storage.setItem(StorageKeys.INSTALL_REPORTED, "true")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Posted:INSTALL_ERROR] Error reporting install", e)
        }
    }
}
