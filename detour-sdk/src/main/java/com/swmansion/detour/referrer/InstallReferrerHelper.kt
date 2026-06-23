package com.swmansion.detour.referrer

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Helper for retrieving install referrer information.
 */
internal class InstallReferrerHelper(private val context: Context) {
    /**
     * Get click ID from install referrer if available.
     * Returns null if not available or on error.
     */
    suspend fun getClickId(): String? =
        withContext(Dispatchers.IO) {
            try {
                val referrerUrl = getInstallReferrer() ?: return@withContext null
                extractClickId(referrerUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting install referrer", e)
                null
            }
        }

    private suspend fun getInstallReferrer(): String? =
        suspendCancellableCoroutine { continuation ->
            val referrerClient = InstallReferrerClient.newBuilder(context).build()
            val completed = AtomicBoolean(false)

            fun resumeOnce(value: String?) {
                if (!completed.compareAndSet(false, true)) return

                try {
                    referrerClient.endConnection()
                } catch (e: Exception) {
                    Log.e(TAG, "Error ending connection", e)
                }

                if (continuation.isActive) {
                    continuation.resume(value)
                }
            }

            referrerClient.startConnection(
                object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                try {
                                    val response = referrerClient.installReferrer
                                    val referrerUrl = response.installReferrer
                                    resumeOnce(referrerUrl)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error reading install referrer", e)
                                    resumeOnce(null)
                                }
                            }
                            else -> {
                                Log.w(TAG, "Install referrer response code: $responseCode")
                                resumeOnce(null)
                            }
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        Log.w(TAG, "Install referrer service disconnected")
                        resumeOnce(null)
                    }
                },
            )

            continuation.invokeOnCancellation {
                resumeOnce(null)
            }
        }

    @VisibleForTesting
    internal fun extractClickId(referrerUrl: String): String? {
        return try {
            val decodedUrl = java.net.URLDecoder.decode(referrerUrl, "UTF-8")
            val regex = Regex("(?:^|&)click_id=([^&]+)")
            val matchResult = regex.find(decodedUrl)
            matchResult?.groupValues?.getOrNull(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting click_id", e)
            null
        }
    }

    companion object {
        private const val TAG = "InstallReferrerHelper"
    }
}
