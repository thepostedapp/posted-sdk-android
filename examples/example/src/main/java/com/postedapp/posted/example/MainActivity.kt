package com.postedapp.posted.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.postedapp.posted.Posted
import com.postedapp.posted.PostedConfig
import com.postedapp.posted.example.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * The install is reported from [PostedExampleApp.onCreate]. This screen only shows when the
 * report for this launch has finished; the outcome is in Logcat under the PostedApiClient tag.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.statusText.text = getString(R.string.status_reporting)
        lifecycleScope.launch {
            // Joins the report started in Application.onCreate (single-flight), so this does not
            // send a second request.
            Posted.reportInstallAndAwait(
                this@MainActivity,
                PostedConfig(apiKey = BuildConfig.POSTED_API_KEY, appId = BuildConfig.POSTED_APP_ID),
            )
            binding.statusText.text = getString(R.string.status_done)
        }
    }
}
