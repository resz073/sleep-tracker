package com.resz073.sleeptracker

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.resz073.sleeptracker.ui.PRIVACY_URL

/** Health Connect's link to the app's privacy policy: opens the Health Connect part of it */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$PRIVACY_URL#health-connect")))
        } catch (e: ActivityNotFoundException) {
            // no browser
        }
        finish()
    }
}
