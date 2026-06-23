package com.swmansion.detour.models

import com.google.gson.annotations.SerializedName

/**
 * Base interface for device fingerprints used in link matching.
 */
sealed interface DeviceFingerprint

/**
 * Probabilistic device fingerprint for link matching.
 */
data class ProbabilisticFingerprint(
    @SerializedName("platform")
    val platform: String,
    @SerializedName("model")
    val model: String,
    @SerializedName("manufacturer")
    val manufacturer: String,
    @SerializedName("systemVersion")
    val systemVersion: String,
    @SerializedName("screenWidth")
    val screenWidth: Int,
    @SerializedName("screenHeight")
    val screenHeight: Int,
    @SerializedName("scale")
    val scale: Float,
    @SerializedName("locale")
    val locale: List<LocaleInfo>,
    @SerializedName("timezone")
    val timezone: String?,
    @SerializedName("userAgent")
    val userAgent: String?,
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("pastedLink")
    val pastedLink: String? = null,
) : DeviceFingerprint

/**
 * Deterministic fingerprint using click ID from install referrer.
 */
data class DeterministicFingerprint(
    @SerializedName("clickId")
    val clickId: String,
) : DeviceFingerprint

/**
 * Locale information.
 */
data class LocaleInfo(
    @SerializedName("languageTag")
    val languageTag: String,
)

/**
 * API response from link matching endpoint.
 */
data class LinkMatchResponse(
    @SerializedName("link")
    val link: String?,
)
