// CONTRACT: The Flutter plugin ships marker classes detected by exact FQN.
// Dual lookup: try new PostedFlutterMarker first, fall back to legacy DetourFlutterMarker.
// Remove the legacy fallback only after the Flutter plugin new major is published and adopted.
// Counterpart: posted-sdk-flutter/android/src/main/kotlin/.../PostedFlutterMarker.kt

package com.postedapp.posted

internal object FlutterSdkHeaderResolver {
    private const val NEW_MARKER_CLASS_NAME = "com.postedapp.posted.PostedFlutterMarker"
    private const val LEGACY_MARKER_CLASS_NAME = "com.swmansion.detour.DetourFlutterMarker"
    private const val HEADER_FIELD_NAME = "SDK_HEADER_VALUE"

    val sdkHeaderValue: String by lazy {
        tryResolveMarker(NEW_MARKER_CLASS_NAME)
            ?: tryResolveMarker(LEGACY_MARKER_CLASS_NAME)
            ?: BuildConfig.SDK_HEADER_VALUE
    }

    private fun tryResolveMarker(className: String): String? {
        return try {
            val markerClass = Class.forName(className)
            val field = markerClass.getDeclaredField(HEADER_FIELD_NAME)
            field.isAccessible = true
            field.get(null) as? String
        } catch (_: Throwable) {
            null
        }
    }
}
