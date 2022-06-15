package com.zacharee1.boredsigns

import android.app.Application
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessaging
import com.zacharee1.boredsigns.activities.NotSupportedActivity
import com.zacharee1.boredsigns.util.Utils
import kotlin.reflect.full.superclasses
import kotlin.reflect.jvm.jvmName


class App : Application() {
    companion object {
        const val NEWS = "news"
    }

    /**
     * THIS NEEDS TO BE REMOVED FOR THE APP TO BUILD
     */

    override fun onCreate() {
        super.onCreate()
        if (!Utils.checkCompatibility(this)) {
            val intent = Intent(this, NotSupportedActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
        FirebaseMessaging.getInstance().subscribeToTopic(NEWS)

    }
}