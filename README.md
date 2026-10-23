# Run — Native Android v3.0.0 (`com.takidz.run`)

Serverless, real-time room-based collaboration & chat app built on public MQTT brokers (`wss://broker.emqx.io:8084/mqtt`, `wss://broker.hivemq.com:8884/mqtt`), 100% interoperable with the live Web v2 Pro client and Android v2.0.0-pro.

## What's New in v3.0.0

- **Warm Paper Editorial Workspace (#14, #15, #16, #22)**: Brand-new workspace navigation with a Floating Dark Pill Bottom Dock (`Spaces` · `Discover` · `People` · `My Saved`), adaptive Two-Pane Study Desk mode on tablets/wide screens, 60fps motion choreography, and **4 switchable themes** (`Warm Paper`, `Warm Paper Dark`, `Clean Air`, `Deep Night`) with **live interactive UI surface preview** in Settings.
- **Per-Room Identity (#17)**: Room owners customize each room's accent color and background pattern (`Warm Paper`, `Dot Grid`, `Ruled Lines`, `Graph Grid`, `Calm Waves`, `Origami`), synced via MQTT (`RoomData.meta` + retained `/identity` sub-topic) to all members and late joiners.
- **Quick Gestures & First-Use Hints (#18)**: Swipe a message to reply/quote, long-press to open the emoji reaction picker (`👍 ❤️ 😂 🔥 👏 🎯`) and quick actions, and swipe down to close the fullscreen media viewer.
- **Origami Icon & Animated Splash (#19)**: F1 Origami launcher icon (adaptive + legacy mipmaps) and a < 1.1s folding Origami splash animation with instant tap-to-skip.
- **Light Sound & Vibration Touches (#20) & Lite Mode (#21)**: Optional subtle haptic/acoustic feedback respecting OS Silent/DND modes, and automatic Lite Mode detection for low-RAM or Battery Saver devices.
- **Unified Identity, Smart Invites & Code Pairing (#23, #24, #25, #30)**:
  - SHA-256 hashed per-device serial (`ANDROID_ID`) so clearing data and renaming still counts as **one user** (#24).
  - Smart invite links (`https://takidz507.github.io/run/?room=CODE` & `runapp://room/CODE`) with direct room entry and deferred first-run clipboard bridge (#23).
  - Encrypted 6-character MQTT transfer & pairing code to move rooms/profile/settings to a new phone (#25) or link Web and Android into one unified account (#30).
- **Everyday Trust (#26, #27, #28, #29, #31)**:
  - Sent (`✓`), Delivered (`✓✓`), and Read (highlighted `✓✓`) indicators (#26).
  - Timed per-room notification mute (`1h`, `8h`, `Until tomorrow`) with auto-expiry (#27).
  - Full RTL Arabic mode with localized typography, dates, and numbers (#28).
  - Live connection quality indicator (`Connected` / `Weak signal` / `Reconnecting…`) (#29).
  - Personal Saved Messages (`My saved`) across all rooms with one-tap jump back to the source message (#31).

See `IMPLEMENTATION-NOTES.md` for the complete feature-to-code mapping.
