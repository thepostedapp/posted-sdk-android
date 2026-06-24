package com.postedapp.posted.example.deferred

import android.app.Application

/**
 * Application class for posted-sdk deferred-only example.
 *
 * SDK initialization is handled in MainActivity with DEFERRED_ONLY mode.
 */
class PostedDeferredApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Posted SDK is initialized in MainActivity
    }
}
