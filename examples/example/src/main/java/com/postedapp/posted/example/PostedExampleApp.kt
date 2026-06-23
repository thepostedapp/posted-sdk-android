package com.postedapp.posted.example

import android.app.Application

/**
 * Application class for posted-sdk example.
 *
 * Note: SDK initialization is handled in MainActivity using PostedDelegate.
 */
class PostedExampleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Posted SDK is initialized in MainActivity with PostedDelegate
    }
}
