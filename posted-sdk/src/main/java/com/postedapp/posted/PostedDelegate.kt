package com.postedapp.posted

import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.postedapp.posted.models.LinkResult
import kotlinx.coroutines.launch

/**
 * Convenience wrapper for handling Universal App Links, Scheme Links,
 * and Deferred Deep Links. Delegates link processing to the [Posted] SDK
 * and invokes a callback with the result.
 *
 * Compatible with any [LifecycleOwner] — `ComponentActivity`, `AppCompatActivity`,
 * or `Fragment`.
 *
 * Usage:
 * ```
 * class MainActivity : AppCompatActivity() {
 *
 *     private val postedDelegate = PostedDelegate(
 *         lifecycleOwner = this,
 *         config = PostedConfig(
 *             appId = "your-app-id",
 *             apiKey = "your-api-key"
 *         ),
 *         onLinkResult = { result ->
 *             handleLinkResult(result)
 *         }
 *     )
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         setContentView(R.layout.activity_main)
 *
 *         Posted.initialize(this, postedDelegate.config)
 *         postedDelegate.onCreate(intent)
 *     }
 *
 *     override fun onNewIntent(intent: Intent) {
 *         super.onNewIntent(intent)
 *         setIntent(intent)
 *         postedDelegate.onNewIntent(intent)
 *     }
 * }
 * ```
 *
 * @property lifecycleOwner The lifecycle owner whose scope is used for coroutines
 * @property config posted-sdk configuration
 * @property onLinkResult Callback invoked with the link processing result
 */
class PostedDelegate(
    private val lifecycleOwner: LifecycleOwner,
    val config: PostedConfig,
    private val onLinkResult: (LinkResult) -> Unit,
) {
    /**
     * Call from Activity.onCreate() to handle Universal, Scheme, and Deferred links.
     *
     * @param intent The activity intent
     */
    fun onCreate(intent: Intent) {
        lifecycleOwner.lifecycleScope.launch {
            val result = Posted.resolveAppLaunch(intent)
            onLinkResult(result)
        }
    }

    /**
     * Call from Activity.onNewIntent() to handle links when app is running.
     *
     * @param intent The new intent
     */
    fun onNewIntent(intent: Intent) {
        lifecycleOwner.lifecycleScope.launch {
            val result = Posted.resolveAppLaunch(intent)
            onLinkResult(result)
        }
    }
}
