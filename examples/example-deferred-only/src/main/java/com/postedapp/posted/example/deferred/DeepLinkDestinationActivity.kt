package com.postedapp.posted.example.deferred

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.postedapp.posted.example.deferred.databinding.ActivityDeepLinkDestinationBinding

/**
 * Simulated deep link destination.
 *
 * In a real app this would be replaced by your actual screen
 * (e.g. ProductScreen, PromoScreen, etc.) driven by the route and params.
 */
class DeepLinkDestinationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeepLinkDestinationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeepLinkDestinationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val route = intent.getStringExtra(EXTRA_ROUTE) ?: "/"

        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val params: Map<String, String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_PARAMS, HashMap::class.java) as? HashMap<String, String>
        } else {
            intent.getSerializableExtra(EXTRA_PARAMS) as? HashMap<String, String>
        } ?: emptyMap()

        binding.routeText.text = "Route: $route"

        if (params.isNotEmpty()) {
            binding.paramsText.text = buildString {
                append("Query params:\n")
                params.forEach { (key, value) -> append("$key: $value\n") }
            }.trimEnd()
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_PARAMS = "params"
    }
}
