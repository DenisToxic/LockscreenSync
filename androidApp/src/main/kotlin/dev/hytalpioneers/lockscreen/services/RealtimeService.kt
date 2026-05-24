package dev.hytalpioneers.lockscreen.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.hytalpioneers.lockscreen.R
import dev.hytalpioneers.lockscreen.data.RealtimeManager
import dev.hytalpioneers.lockscreen.ui.LockScreenActivity

class RealtimeService : Service() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Pre-start the lockscreen activity so it's ready when user wakes up
                val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(lockIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, "realtime_channel")
            .setContentTitle("Lockscreen Active")
            .setContentText("Couples drawing sync is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        startForeground(1, notification)

        // Sync with saved room ID if available
        val prefs = getSharedPreferences("lockscreen_prefs", Context.MODE_PRIVATE)
        val roomId = prefs.getString("room_id", "test-room") ?: "test-room"
        RealtimeManager.updateRoomId(roomId)
        RealtimeManager.startListening()

        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "realtime_channel",
                "Lockscreen Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
