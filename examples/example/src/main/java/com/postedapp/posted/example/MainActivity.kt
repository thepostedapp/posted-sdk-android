package com.postedapp.posted.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.postedapp.posted.example.databinding.ActivityMainBinding
import com.postedapp.posted.example.storage.EncryptedStorageProvider
import com.postedapp.posted.Posted
import com.postedapp.posted.PostedConfig
import com.postedapp.posted.PostedDelegate
import com.postedapp.posted.analytics.PostedAnalytics
import com.postedapp.posted.analytics.PostedEventNames
import com.postedapp.posted.models.LinkProcessingMode
import com.postedapp.posted.models.LinkResult
import com.postedapp.posted.models.LinkType
import com.postedapp.posted.example.BuildConfig

/**
 * Full example: PostedDelegate + LinkProcessingMode.ALL.
 *
 * Handles Universal Links (https), custom scheme links (posted-example-app://),
 * and deferred links automatically. This is the recommended setup for most apps.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val postedDelegate by lazy {
        PostedDelegate(
            lifecycleOwner = this,
            config = PostedConfig(
                apiKey = BuildConfig.POSTED_API_KEY,
                appId = BuildConfig.POSTED_APP_ID,
                // LinkProcessingMode controls which link sources the SDK handles:
                //   ALL           – Universal Links + custom schemes + deferred (default)
                //   WEB_ONLY      – Universal Links + deferred, ignores custom schemes
                //   DEFERRED_ONLY – only deferred links; use when your nav framework
                //                   (e.g. Jetpack Navigation) already handles intents
                linkProcessingMode = LinkProcessingMode.ALL,
                storage = EncryptedStorageProvider(this)
            ),
            onLinkResult = { result -> handleLinkResult(result) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Posted.initialize(this, postedDelegate.config)
        setupButtons()

        // Process links (Universal + Scheme + Deferred)
        postedDelegate.onCreate(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        postedDelegate.onNewIntent(intent)
    }

    private fun handleLinkResult(result: LinkResult) {
        when (result) {
            is LinkResult.Success -> {
                binding.statusText.text = buildString {
                    append("Link processed!\n\n")
                    append("Type: ${result.type}\n")
                    append("Link: ${result.url}\n")
                    append("Route: ${result.route}\n")
                    append("Pathname: ${result.pathname}\n")
                    if (result.params.isNotEmpty()) {
                        append("Query params:\n")
                        result.params.forEach { (key, value) -> append("$key: $value\n") }
                    }
                }

                Log.d(TAG, "Link: ${result.url}")
                Log.d(TAG, "Route: ${result.route}")
                Log.d(TAG, "Pathname: ${result.pathname}")
                Log.d(TAG, "Params: ${result.params}")
                Log.d(TAG, "Type: ${result.type}")

                // --- Analytics: log a ReEngage event for every link-driven app open ---
                // Pass the link type as "source" so you can segment by channel in the Dashboard.
                PostedAnalytics.logEvent(
                    PostedEventNames.ReEngage,
                    mapOf("source" to result.type.name.lowercase(), "route" to result.route)
                )

                when (result.type) {
                    LinkType.DEFERRED -> Log.d(TAG, "Deferred link click detected")
                    LinkType.VERIFIED -> Log.d(TAG, "Verified link click detected")
                    LinkType.SCHEME -> Log.d(TAG, "Scheme link click detected")
                }

                navigateToRoute(result.route, result.params)
            }

            is LinkResult.NotFirstLaunch -> {
                binding.statusText.text =
                    "Not first launch\n\nDeferred links only work on first app install."
                Log.d(TAG, "Not first launch")
            }

            is LinkResult.NoLink -> {
                binding.statusText.text = "No deep link detected"
                Log.d(TAG, "Normal app launch")
            }

            is LinkResult.Error -> {
                binding.statusText.text = "Error: ${result.exception.message}"
                Log.e(TAG, "Error processing link", result.exception)
                Toast.makeText(this, "Error processing link", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun navigateToRoute(route: String, params: Map<String, String> = emptyMap()) {
        when {
            route.startsWith("/products/") -> {
                val productId = route.removePrefix("/products/").split("?").first().trim('/')
                if (productId.isNotEmpty()) {
                    startActivity(Intent(this, ProductActivity::class.java).apply {
                        putExtra("productId", productId)
                        putExtra("params", HashMap(params))
                    })
                }
            }

            route.startsWith("/promo") -> {
                startActivity(Intent(this, PromoActivity::class.java))
            }

            else -> {
                Toast.makeText(this, "Unknown route: $route", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        binding.btnProduct.setOnClickListener {
            startActivity(Intent(this, ProductActivity::class.java))
        }

        binding.btnPromo.setOnClickListener {
            startActivity(Intent(this, PromoActivity::class.java))
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
