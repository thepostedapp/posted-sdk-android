// CONTRACT: The Flutter plugin ships a marker class detected by exact FQN.
// Counterpart: posted-sdk-flutter/android/src/main/kotlin/.../PostedFlutterMarker.kt

package com.postedapp.posted

internal object FlutterSdkHeaderResolver {
    private const val MARKER_CLASS_NAME = "com.postedapp.posted.PostedFlutterMarker"
    private const val HEADER_FIELD_NAME = "SDK_HEADER_VALUE"

    val sdkHeaderValue: String by lazy {
        tryResolveMarker(MARKER_CLASS_NAME)
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
