package com.zacharee1.boredsigns.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zacharee1.boredsigns.R
import com.zacharee1.boredsigns.util.DotaGame
import com.zacharee1.boredsigns.util.Utils
import com.zacharee1.boredsigns.widgets.Dev2Widget
import com.zacharee1.boredsigns.widgets.OpenDotaWidget

class OpenDotaService : Service() {
    private var isRunning = false;

    override fun onBind(p0: Intent?): IBinder? {
        return null;
    }

    override fun onCreate() {
        isRunning = true;
        fetchData();

        Utils.sendWidgetUpdate(this, OpenDotaWidget::class.java, null);

        super.onCreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        isRunning = false
    }
    private fun startForeground() {

    }
    private fun fetchData() : DotaGame {
        val game = DotaGame();

        game.hero = "Ogre Magi"
        game.k = 5;
        game.d = 10;
        game.a = 15;
        game.gpm = 400;

        return game;
    }
}