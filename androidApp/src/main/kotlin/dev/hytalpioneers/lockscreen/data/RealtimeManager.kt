package dev.hytalpioneers.lockscreen.data

import android.util.Log
import dev.hytalpioneers.lockscreen.data.SupabaseManager
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

object RealtimeManager {

    private const val TAG = "RealtimeManager"

    data class DrawData(
        val x: Float,
        val y: Float,
        val action: String,
        val color: Int,
        val sender: String,
        val size: Float
    )

    val drawEvents = MutableSharedFlow<DrawData>(extraBufferCapacity = 100)

    private val scope = CoroutineScope(Dispatchers.IO)
    private val deviceId = UUID.randomUUID().toString()

    private var currentRoomId: String = "test-room"
    private var started = false
    private var listeningJob: Job? = null
    private var channel: RealtimeChannel? = null

    fun updateRoomId(roomId: String) {
        if (currentRoomId != roomId) {
            Log.d(TAG, "Updating Room ID from $currentRoomId to $roomId")
            val wasStarted = started
            stopListening()
            currentRoomId = roomId
            if (wasStarted) {
                startListening()
            }
        }
    }

    fun startListening() {
        if (started) return

        val room = currentRoomId
        Log.d(TAG, "startListening called for room: $room")

        started = true
        channel = SupabaseManager.client.channel(room)

        listeningJob = scope.launch {
            Log.d(TAG, "Listening job started for $room, deviceId=$deviceId")

            val flow = channel?.broadcastFlow<JsonObject>(
                event = "draw"
            )

            launch {
                flow?.collect { json ->
                    try {
                        val sender = json["sender"]?.jsonPrimitive?.content ?: ""
                        if (sender == deviceId) return@collect

                        val x = json["x"]?.jsonPrimitive?.float ?: 0f
                        val y = json["y"]?.jsonPrimitive?.float ?: 0f
                        val action = json["action"]?.jsonPrimitive?.content ?: return@collect
                        val color = json["color"]?.jsonPrimitive?.int ?: -1
                        val size = json["size"]?.jsonPrimitive?.float ?: 8f

                        Log.d(TAG, "Remote draw received in room $room: $action at $x,$y")
                        drawEvents.emit(DrawData(x, y, action, color, sender, size))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing broadcast", e)
                    }
                }
            }

            try {
                channel?.subscribe()
                Log.d(TAG, "Subscribed successfully to channel: $room")
            } catch (e: Exception) {
                Log.e(TAG, "Subscription failed for $room", e)
                started = false
            }
        }
    }

    fun stopListening() {
        Log.d(TAG, "stopListening called for room: $currentRoomId")
        started = false
        listeningJob?.cancel()
        listeningJob = null
        val oldChannel = channel
        channel = null
        scope.launch {
            try {
                oldChannel?.unsubscribe()
                Log.d(TAG, "Unsubscribed from Supabase channel")
            } catch (e: Exception) {
                Log.e(TAG, "Error unsubscribing", e)
            }
        }
    }

    fun sendDraw(
        x: Float,
        y: Float,
        action: String,
        color: Int,
        size: Float
    ) {
        if (!started) {
            Log.w(TAG, "Cannot send draw: RealtimeManager not started")
            return
        }

        scope.launch {
            try {
                channel?.broadcast(
                    event = "draw",
                    message = buildJsonObject {
                        put("x", x)
                        put("y", y)
                        put("action", action)
                        put("color", color)
                        put("size", size)
                        put("sender", deviceId)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Broadcast failed in room $currentRoomId", e)
            }
        }
    }
}
