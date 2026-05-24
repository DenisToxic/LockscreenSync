package dev.hytalpioneers.lockscreen.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class LockScreenNotificationListenerService : NotificationListenerService() {

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "dev.hytalpioneers.lockscreen.DISMISS_NOTIFICATION") {
                val key = intent.getStringExtra("key")
                if (key != null) {
                    cancelNotification(key)
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationListener"
        var notifications = mutableListOf<StatusBarNotification>()
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("dev.hytalpioneers.lockscreen.DISMISS_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(dismissReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(dismissReceiver)
    }

    override fun onListenerConnected() {
        Log.d(TAG, "Notification Listener Connected")
        updateNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        updateNotifications()
    }

    private fun updateNotifications() {
        try {
            val active = activeNotifications
            if (active != null) {
                notifications.clear()
                notifications.addAll(active.filter { !it.isOngoing && it.packageName != packageName })
                notifications.sortByDescending { it.postTime }

                val intent = Intent("dev.hytalpioneers.lockscreen.NOTIFICATION_UPDATE")
                intent.setPackage(packageName)
                sendBroadcast(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching active notifications", e)
        }
    }
}
