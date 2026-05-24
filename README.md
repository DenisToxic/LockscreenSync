# Lockscreen - Drawing Sync

A modern Android lockscreen replacement designed for couples to share drawings and sync notifications in real-time.

## Features

- **Real-time Shared Canvas**: Draw directly on your lockscreen and have it instantly appear on your partner's phone.
- **Modern UI**: Clean, card-based setup interface with a minimal, beautiful lockscreen aesthetic.
- **AOD / Ambient Mode Support**: Automatically switches to a power-efficient black screen with a dimmed clock on OLED displays.
- **Real Notification Mirroring**: See, swipe away, or click to open your real phone notifications directly from the drawing canvas.
- **Private Rooms**: Create or join unique 6-digit rooms to ensure your drawings stay private.
- **Dynamic Backgrounds**: Uses your current system wallpaper by default, or set a custom image for both partners.
- **Customizable Brush**: Select from multiple colors and adjust your brush size with a smooth slider.

## Technical Details

- **Backend**: Powered by Supabase Realtime (WebSockets) for ultra-low latency synchronization.
- **Architecture**: Organized into clean packages (`ui`, `data`, `services`).
- **Battery Optimized**: Automatically disconnects from sync and pauses background work when the screen is off or in Ambient mode.

## Setup Instructions

1.  **Grant Permissions**: The app requires "Display over other apps" and "Notification Access" to function as a lockscreen.
2.  **Create/Join Room**: One partner taps "Create New Room" and shares the code with the other.
3.  **Draw**: Simply wake your phone and start drawing!
4.  **Unlock**: Tap the "Unlock" button to access your phone's secure lockscreen or home screen.

## Development

Built with Kotlin and modern Android APIs.

- **Sync**: `RealtimeManager` handles the WebSocket logic.
- **UI**: `DrawView` provides the multi-path, multi-color drawing engine.
- **Services**: `RealtimeService` manages the lifecycle and power button events.

---
