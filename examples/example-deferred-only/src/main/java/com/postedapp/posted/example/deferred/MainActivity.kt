package com.postedapp.posted.example.deferred

import android.content.Intent
import android.os.Bundle
import androidx.core.app.TaskStackBuilder
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.postedapp.posted.example.deferred.databinding.ActivityMainBinding
import com.postedapp.posted.Posted
import com.postedapp.posted.PostedConfig
import com.postedapp.posted.models.LinkProcessingMode
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.example.deferred.BuildConfig
import kotlinx.coroutines.launch

/**
 * Deferred-only example: calls [Posted.resolveAppLaunch] directly without [com.postedapp.posted.PostedDelegate].
 *
 * This is the pattern for apps where a navigation framework (Jetpack Navigation, etc.)
 * already handles Verified App Links and custom scheme links, and posted-sdk is only needed
 * for deferred deep link resolution on first install.
 *
 * Key differences from the full example:
 * - [LinkProcessingMode.DEFERRED_ONLY] — SDK skips intent processing entirely.
 * - No [com.postedapp.posted.PostedDelegate] — no [onNewIntent] wiring needed.
 * - [Posted.resolveAppLaunch] called directly instead of [Posted.getDeferredLink].
 * - No Verified App Link or custom scheme intent-filters in AndroidManifest.xml.
 *
 * Splash screen pattern:
 * The splash screen stays visible while [Posted.resolveAppLaunch] resolves.
 * On success the user is taken directly to [DeepLinkDestinationActivity] — MainActivity
 * is finished immediately so the user never sees it. On no link the splash dismisses
 * normally and the home screen is shown.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Splash screen holds while this is false
    private var isLinkResolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate()
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Keep splash on screen until getDeferredLink() returns
        splashScreen.setKeepOnScreenCondition { !isLinkResolved }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Posted.initialize(
            this,
            PostedConfig(
                apiKey = BuildConfig.POSTED_API_KEY,
                appId = BuildConfig.POSTED_APP_ID,
                linkProcessingMode = LinkProcessingMode.DEFERRED_ONLY
            )
        )

        lifecycleScope.launch {
            val result = Posted.resolveAppLaunch()
            handleResult(result)
        }

    }

    private fun handleResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                // Build a synthetic back stack: DeepLinkDestinationActivity on top of MainActivity.
                // This way pressing back from the destination lands on the home screen
                // rather than exiting the app — standard Android deep link pattern.
                Log.d(TAG, "Deferred link: ${result.url}")
                val intent = Intent(this, DeepLinkDestinationActivity::class.java).apply {
                    putExtra(DeepLinkDestinationActivity.EXTRA_ROUTE, result.route)
                    putExtra(DeepLinkDestinationActivity.EXTRA_PARAMS, HashMap(result.params))
                }
                TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(intent)
                    .startActivities()
                finish()
            }

            is LinkResult.NotFirstLaunch -> {
                // Release splash — show home screen normally
                isLinkResolved = true
                binding.statusText.text =
                    "Not first launch — deferred link already consumed on a previous session."
                Log.d(TAG, "Not first launch")
            }

            is LinkResult.NoLink -> {
                // Release splash — show home screen normally
                isLinkResolved = true
                binding.statusText.text = "No deferred link matched for this device."
                Log.d(TAG, "No deferred link")
            }

            is LinkResult.Error -> {
                // Release splash — show home screen with error
                isLinkResolved = true
                binding.statusText.text = "Error: ${result.exception.message}"
                Log.e(TAG, "Deferred link error", result.exception)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
