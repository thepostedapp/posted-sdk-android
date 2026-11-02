package com.postedapp.posted.example

import android.app.Application
import com.postedapp.posted.Posted
import com.postedapp.posted.PostedConfig

class PostedExampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Report the install on every launch. The SDK sends it once per install and retries on
        // later launches until the backend accepts it. Returns immediately.
        Posted.reportInstall(
            this,
            PostedConfig(
                apiKey = BuildConfig.POSTED_API_KEY,
                appId = BuildConfig.POSTED_APP_ID,
            ),
        )
    }
}
