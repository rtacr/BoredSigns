package com.zacharee1.boredsigns.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.zacharee1.boredsigns.util.Utils
import com.zacharee1.boredsigns.widgets.NavBarWidget

class NavBarAccessibility : AccessibilityService() {
    companion object {
        const val BASE = "com.zacharee1.boredsigns.action."
        const val HOME = BASE + "HOME"
        const val BACK = BASE + "BACK"
        const val RECENTS = BASE + "RECENTS"
        const val NOTIFS = BASE + "NOTIFS"
        const val SPLIT = BASE + "SPLIT"
        const val QS = BASE + "QS"
        const val POWER = BASE + "POWER"
        const val ASSIST = BASE + "ASSIST"
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("WrongConstant", "PrivateApi")
        override fun onReceive(p0: Context, p1: Intent?) {
            val statusBarManager = p0.getSystemService("statusbar")
            val collapsePanels = Class.forName("android.app.StatusBarManager").getMethod("collapsePanels")

            when (p1?.action) {
                RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)

                HOME -> performGlobalAction(GLOBAL_ACTION_HOME)

                BACK -> performGlobalAction(GLOBAL_ACTION_BACK)

                NOTIFS -> {
                    collapsePanels.invoke(statusBarManager)
                    performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                }

                SPLIT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                }

                QS -> {
                    collapsePanels.invoke(statusBarManager)
                    performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                }

                POWER -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)

                ASSIST -> {
                    val searchMan = getSystemService(Context.SEARCH_SERVICE) as SearchManager
                    searchMan.launchAssist(null)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        serviceInfo.eventTypes = 0

        val filter = IntentFilter()
        filter.addAction(HOME)
        filter.addAction(RECENTS)
        filter.addAction(BACK)
        filter.addAction(NOTIFS)
        filter.addAction(SPLIT)
        filter.addAction(QS)
        filter.addAction(POWER)
        filter.addAction(ASSIST)

        registerReceiver(receiver, filter)

        Utils.sendWidgetUpdate(this, NavBarWidget::class.java, null)
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {

    }

    override fun onInterrupt() {

    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }
}

private fun SearchManager.launchAssist(nothing: Nothing?) {

}
