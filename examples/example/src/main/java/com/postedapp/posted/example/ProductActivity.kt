package com.postedapp.posted.example

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.postedapp.posted.example.databinding.ActivityProductBinding
import com.postedapp.posted.analytics.PostedAnalytics
import com.postedapp.posted.analytics.PostedEventNames

class ProductActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val productId = intent.getStringExtra("productId") ?: "unknown"

        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        val params: Map<String, String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("params", HashMap::class.java) as? HashMap<String, String>
        } else {
            intent.getSerializableExtra("params") as? HashMap<String, String>
        } ?: emptyMap()

        binding.productText.text = buildString {
            append("Product ID: $productId")
            if (params.isNotEmpty()) {
                append("\n\nQuery params:\n")
                params.forEach { (key, value) -> append("$key: $value\n") }
            }
        }.trimEnd()

        // --- Analytics: log a ViewItem event ---
        PostedAnalytics.logEvent(
            PostedEventNames.ViewItem,
            mapOf("item_id" to productId)
        )

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
