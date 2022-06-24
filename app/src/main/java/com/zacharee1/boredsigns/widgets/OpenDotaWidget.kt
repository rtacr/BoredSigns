package com.zacharee1.boredsigns.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.zacharee1.boredsigns.R
import com.zacharee1.boredsigns.services.OpenDotaService
import com.zacharee1.boredsigns.util.Utils

class OpenDotaWidget : AppWidgetProvider() {
    private var enabled = false;

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray?
    ) {
        ContextCompat.startForegroundService(context, Intent(context, OpenDotaService::class.java))

        val views = RemoteViews(context.packageName, R.layout.dota_widget)
        val gameInfo = Utils.parseDotaInfo()

        val url : String = "https://liquipedia.net/commons/images/e/ea/Ogre_magi_underlords.png";
        views.setTextViewText(R.id.tvHeroName, gameInfo.hero);
        views.setTextViewText(R.id.tvKDA, "KDA: " + "${gameInfo.k}/${gameInfo.d}/${gameInfo.a}");
        views.setTextViewText(R.id.tvStatus, "win");
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }

}