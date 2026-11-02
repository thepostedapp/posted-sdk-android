package com.postedapp.posted.fingerprint

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.postedapp.posted.models.LocaleInfo
import com.postedapp.posted.models.ProbabilisticFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

/**
 * Collects the device signals sent with the install report.
 *
 * The clipboard is never read: it shows a system toast and install matching doesn't use it.
 */
internal class FingerprintCollector(private val context: Context) {
    suspend fun collectFingerprint(): ProbabilisticFingerprint =
        withContext(Dispatchers.IO) {
            val (width, height, density) = getScreenMetrics()
            val locale = Locale.getDefault()
            val timezone = TimeZone.getDefault()

            ProbabilisticFingerprint(
                platform = "android",
                model = Build.MODEL,
                manufacturer = Build.MANUFACTURER,
                systemVersion = Build.VERSION.RELEASE,
                screenWidth = width,
                screenHeight = height,
                scale = density,
                locale = listOf(LocaleInfo(languageTag = locale.toLanguageTag())),
                timezone = timezone.id,
                userAgent = getUserAgent(),
                timestamp = System.currentTimeMillis(),
            )
        }

    private data class ScreenMetrics(val width: Int, val height: Int, val density: Float)

    private fun getScreenMetrics(): ScreenMetrics {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null) {
                val bounds = wm.currentWindowMetrics.bounds
                val density = Resources.getSystem().displayMetrics.density
                return ScreenMetrics(
                    (bounds.width() / density).toInt(),
                    (bounds.height() / density).toInt(),
                    density,
                )
            }
        }

        val dm: DisplayMetrics = Resources.getSystem().displayMetrics
        return ScreenMetrics(
            (dm.widthPixels / dm.density).toInt(),
            (dm.heightPixels / dm.density).toInt(),
            dm.density,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun getUserAgent(): String? {
        return try {
            System.getProperty("http.agent")
        } catch (_: Exception) {
            null
        }
    }
}
