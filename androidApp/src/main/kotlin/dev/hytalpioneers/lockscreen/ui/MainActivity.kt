package dev.hytalpioneers.lockscreen.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.hytalpioneers.lockscreen.R
import dev.hytalpioneers.lockscreen.data.RealtimeManager
import dev.hytalpioneers.lockscreen.services.RealtimeService

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                uri?.let {
                    contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val prefs = getSharedPreferences("lockscreen_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("bg_uri", it.toString()).apply()
                    Toast.makeText(this, "Background updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startForegroundService(Intent(this, RealtimeService::class.java))

        setContentView(R.layout.activity_main)

        checkPermissions()
        setupRoomManagement()
        setupButtons()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnPickBg).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            pickImageLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnRemoveBg).setOnClickListener {
            val prefs = getSharedPreferences("lockscreen_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove("bg_uri").apply()
            Toast.makeText(this, "Reverted to phone wallpaper", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnOpenLock).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else if (!isNotificationServiceEnabled()) {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            } else {
                startActivity(Intent(this, LockScreenActivity::class.java))
            }
        }
    }

    private fun setupRoomManagement() {
        val prefs = getSharedPreferences("lockscreen_prefs", Context.MODE_PRIVATE)
        val tvCurrentRoom = findViewById<TextView>(R.id.tvCurrentRoom)
        val etRoomId = findViewById<EditText>(R.id.etRoomId)

        val currentRoomId = prefs.getString("room_id", "test-room") ?: "test-room"
        tvCurrentRoom.text = getString(R.string.current_room_format, currentRoomId)
        RealtimeManager.updateRoomId(currentRoomId)

        findViewById<Button>(R.id.btnCreateRoom).setOnClickListener {
            val newRoomId = (100000..999999).random().toString()
            prefs.edit().putString("room_id", newRoomId).apply()
            tvCurrentRoom.text = getString(R.string.current_room_format, newRoomId)
            RealtimeManager.updateRoomId(newRoomId)
            Toast.makeText(this, "New room created: $newRoomId", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btnJoinRoom).setOnClickListener {
            val inputId = etRoomId.text.toString().trim()
            if (inputId.length >= 4) {
                prefs.edit().putString("room_id", inputId).apply()
                tvCurrentRoom.text = getString(R.string.current_room_format, inputId)
                RealtimeManager.updateRoomId(inputId)
                etRoomId.text.clear()
                Toast.makeText(this, "Joined room: $inputId", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter a valid room code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (flat != null && flat.isNotEmpty()) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == packageName) return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        val btnOpenLock = findViewById<Button>(R.id.btnOpenLock)
        if (Settings.canDrawOverlays(this) && isNotificationServiceEnabled()) {
            btnOpenLock.text = getString(R.string.preview_lockscreen)
        } else {
            btnOpenLock.text = "Enable Permissions & Preview"
        }
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        }
    }
}
