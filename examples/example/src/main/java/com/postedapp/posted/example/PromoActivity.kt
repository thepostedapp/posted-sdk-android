package com.postedapp.posted.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.postedapp.posted.example.databinding.ActivityPromoBinding
import com.postedapp.posted.analytics.PostedAnalytics
import com.postedapp.posted.analytics.PostedEventNames

class PromoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPromoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Analytics: log a Purchase event (checkout flow example) ---
        PostedAnalytics.logEvent(PostedEventNames.Purchase, mapOf("promo" to "summer_sale"))

        // --- Analytics: log a retention event ---
        PostedAnalytics.logRetention("subscription_renewed")

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
