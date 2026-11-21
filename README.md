# Detent

Detent is an offline Android music player with a hardware-inspired jog wheel. It plays audio from the device library through Media3 and includes three original FLAC samples so you can try it before granting audio access.

## Try the player

- Install the debug APK on Android 8.0 (API 26) or later. Open Detent and choose **Try demo**, or open **Library → Songs → Allow access** to load your own music.
- Turn the **Player** wheel to preview a seek in five-second steps. Release to jump to the previewed time. The progress bar continues to show the actual playback position. The red center plays or pauses.
- The **Player** header names the current source. Use the buttons above the wheel to shuffle upcoming tracks, change repeat mode, skip tracks, add the current song to a playlist, or toggle its favorite status.
- Turn the **Queue** wheel to highlight another track. Tap the wheel’s center or a row to play the selected track. The queue menu moves or removes tracks, and the shuffle button reorders upcoming tracks. The repeat button cycles through off, all, and one.
- Search by track, artist, or album in **Songs**. The menu on a track can queue it next, append it to the queue, or add it to a saved playlist.
- Use **Library → Playlists** to manage playlists and **Library → Favorites** to play your saved favorites. Playlists, favorites, and the last queue are stored on the device.

Haptic feedback follows Android’s touch feedback setting and the phone’s hardware. The wheel also exposes forward and backward accessibility actions.

## Build from source

Open this directory in Android Studio with Android SDK 35 and JDK 17 or later, or build from the command line.

```sh
./gradlew :app:assembleDebug
```

Gradle downloads dependencies during the first build. The installed app uses no internet permission. The debug APK builds to `app/build/outputs/apk/debug/app-debug.apk`.

The bundled DotGothic16 font is distributed under the [SIL Open Font License](app/src/main/assets/licenses/DotGothic16.txt).
