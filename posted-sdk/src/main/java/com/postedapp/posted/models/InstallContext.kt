package com.postedapp.posted.models

import com.google.gson.annotations.SerializedName

/**
 * The device signals the backend matches an install to the ad click that led to it.
 * Field names are the wire format shared with the other platform SDKs: never rename them.
 */
internal data class InstallContext(
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
)

internal data class LocaleInfo(
    @SerializedName("languageTag")
    val languageTag: String,
)
