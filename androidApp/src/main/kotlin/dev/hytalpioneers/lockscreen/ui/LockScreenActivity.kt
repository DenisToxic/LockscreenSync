package dev.hytalpioneers.lockscreen.ui

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.hytalpioneers.lockscreen.R
import dev.hytalpioneers.lockscreen.data.RealtimeManager
import dev.hytalpioneers.lockscreen.services.LockScreenNotificationListenerService
import kotlinx.coroutines.launch
import kotlin.math.abs

class LockScreenActivity : ComponentActivity() {

    private var isAmbient = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    enterAmbientMode()
                }
                Intent.ACTION_SCREEN_ON -> {
                    exitAmbientMode()
                }
                "dev.hytalpioneers.lockscreen.NOTIFICATION_UPDATE" -> {
                    runOnUiThread { updateNotificationUI() }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_lock_screen)

        val prefs = getSharedPreferences("lockscreen_prefs", Context.MODE_PRIVATE)
        val roomId = prefs.getString("room_id", "test-room") ?: "test-room"
        RealtimeManager.updateRoomId(roomId)

        setupBackground()
        setupDrawing()
        setupSelectionPanels()

        findViewById<Button>(R.id.unlockButton).setOnClickListener {
            unlockAndFinish()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            })

        RealtimeManager.startListening()

        lifecycleScope.launch {
            RealtimeManager.drawEvents.collect { event ->
                if (!isAmbient) {
                    val drawView = findViewById<DrawView>(R.id.drawView)
                    drawView.remoteDraw(
                        event.x,
                        event.y,
                        event.action,
                        event.color,
                        event.sender,
                        event.size
                    )
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction("dev.hytalpioneers.lockscreen.NOTIFICATION_UPDATE")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            enterAmbientMode()
        }

        updateNotificationUI()
    }

    private fun unlockAndFinish() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    finish()
                }

                override fun onDismissCancelled() {
                }
            })
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }
        RealtimeManager.stopListening()
    }

    private fun enterAmbientMode() {
        isAmbient = true
        RealtimeManager.stopListening()

        findViewById<ImageView>(R.id.backgroundImage).visibility = View.GONE
        findViewById<DrawView>(R.id.drawView).visibility = View.GONE
        findViewById<View>(R.id.controlsLayout).visibility = View.GONE
        findViewById<View>(R.id.notificationsScrollView).visibility = View.GONE
        findViewById<View>(R.id.colorPickerPanel).visibility = View.GONE
        findViewById<View>(R.id.sizePickerPanel).visibility = View.GONE

        val clock = findViewById<TextView>(R.id.clock)
        clock.setTextColor(Color.GRAY)
        clock.textSize = 64f

        findViewById<View>(R.id.rootLayout).setBackgroundColor(Color.BLACK)
    }

    private fun exitAmbientMode() {
        isAmbient = false
        RealtimeManager.startListening()

        findViewById<ImageView>(R.id.backgroundImage).visibility = View.VISIBLE
        findViewById<DrawView>(R.id.drawView).visibility = View.VISIBLE
        findViewById<View>(R.id.controlsLayout).visibility = View.VISIBLE
        findViewById<View>(R.id.notificationsScrollView).visibility = View.VISIBLE

        val clock = findViewById<TextView>(R.id.clock)
        clock.setTextColor(Color.WHITE)
        clock.textSize = 84f

        findViewById<View>(R.id.rootLayout).setBackgroundColor(Color.TRANSPARENT)
        setupBackground()
        updateNotificationUI()
    }

    private fun updateNotificationUI() {
        if (isAmbient) return

        val container = findViewById<LinearLayout>(R.id.notificationsContainer)
        container.removeAllViews()

        val activeNotifications = LockScreenNotificationListenerService.notifications
        val inflater = LayoutInflater.from(this)

        activeNotifications.take(5).forEach { sbn ->
            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)

            if (title != null || text != null) {
                val itemView = inflater.inflate(R.layout.item_notification, container, false)
                itemView.findViewById<TextView>(R.id.notificationTitle).text = title
                itemView.findViewById<TextView>(R.id.notificationText).text = text

                val iconView = itemView.findViewById<ImageView>(R.id.notificationIcon)
                try {
                    val iconDrawable = notification.smallIcon.loadDrawable(this)
                    iconView.setImageDrawable(iconDrawable)
                } catch (e: Exception) {
                    iconView.setImageResource(android.R.drawable.ic_dialog_info)
                }

                itemView.setOnTouchListener(object : View.OnTouchListener {
                    private var startX = 0f
                    private val swipeThreshold = 250f
                    private var isSwiping = false

                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                startX = event.rawX
                                isSwiping = false
                                return true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val deltaX = event.rawX - startX
                                if (abs(deltaX) > 20) isSwiping = true
                                if (isSwiping) {
                                    v.translationX = deltaX
                                    v.alpha = 1f - (abs(deltaX) / 1000f)
                                }
                                return true
                            }

                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                val deltaX = event.rawX - startX
                                if (isSwiping && abs(deltaX) > swipeThreshold) {
                                    v.animate()
                                        .translationX(if (deltaX > 0) 1000f else -1000f)
                                        .alpha(0f)
                                        .setDuration(200)
                                        .withEndAction {
                                            try {
                                                val intent =
                                                    Intent("dev.hytalpioneers.lockscreen.DISMISS_NOTIFICATION")
                                                intent.putExtra("key", sbn.key)
                                                intent.setPackage(packageName)
                                                sendBroadcast(intent)
                                            } catch (e: Exception) {
                                            }
                                            container.removeView(v)
                                        }
                                        .start()
                                } else if (!isSwiping && event.action == MotionEvent.ACTION_UP) {
                                    try {
                                        notification.contentIntent?.let { pendingIntent ->
                                            val km =
                                                getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                                            km.requestDismissKeyguard(
                                                this@LockScreenActivity,
                                                object : KeyguardManager.KeyguardDismissCallback() {
                                                    override fun onDismissSucceeded() {
                                                        try {
                                                            pendingIntent.send()
                                                            finish()
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                })
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    v.animate().translationX(0f).alpha(1f).setDuration(200).start()
                                }
                                return true
                            }
                        }
                        return false
                    }
                })

                container.addView(itemView)
            }
        }
    }

    private fun setupBackground() {
        val bgImage = findViewById<ImageView>(R.id.backgroundImage)
        val prefs = getSharedPreferences("lockscreen_prefs", Context.MODE_PRIVATE)
        val uriString = prefs.getString("bg_uri", null)

        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                }

                bgImage.visibility = View.VISIBLE
                bgImage.setImageURI(uri)
            } catch (e: Exception) {
                bgImage.visibility = View.GONE
            }
        } else {
            bgImage.visibility = View.GONE
        }
    }

    private fun setupDrawing() {
        val drawView = findViewById<DrawView>(R.id.drawView)
        drawView.setOnDrawDataListener { x, y, action, color, size ->
            RealtimeManager.sendDraw(x, y, action, color, size)
        }

        findViewById<Button>(R.id.clearButton).setOnClickListener {
            drawView.clear()
            RealtimeManager.sendDraw(0f, 0f, "clear", 0, 0f)
        }
    }

    private fun setupSelectionPanels() {
        val drawView = findViewById<DrawView>(R.id.drawView)
        val colorPanel = findViewById<LinearLayout>(R.id.colorPickerPanel)
        val sizePanel = findViewById<LinearLayout>(R.id.sizePickerPanel)
        val sizeSeekBar = findViewById<SeekBar>(R.id.sizeSeekBar)

        findViewById<Button>(R.id.btnColorSelect).setOnClickListener {
            sizePanel.visibility = View.GONE
            colorPanel.visibility =
                if (colorPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<Button>(R.id.btnSizeSelect).setOnClickListener {
            colorPanel.visibility = View.GONE
            sizePanel.visibility =
                if (sizePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        findViewById<View>(R.id.colorWhite).setOnClickListener {
            drawView.setDrawingColor(Color.WHITE)
            colorPanel.visibility = View.GONE
        }
        findViewById<View>(R.id.colorRed).setOnClickListener {
            drawView.setDrawingColor(Color.RED)
            colorPanel.visibility = View.GONE
        }
        findViewById<View>(R.id.colorGreen).setOnClickListener {
            drawView.setDrawingColor(Color.GREEN)
            colorPanel.visibility = View.GONE
        }
        findViewById<View>(R.id.colorBlue).setOnClickListener {
            drawView.setDrawingColor(Color.BLUE)
            colorPanel.visibility = View.GONE
        }
        findViewById<View>(R.id.colorYellow).setOnClickListener {
            drawView.setDrawingColor(Color.YELLOW)
            colorPanel.visibility = View.GONE
        }

        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = if (progress < 2) 2f else progress.toFloat()
                drawView.setStrokeWidth(size)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
