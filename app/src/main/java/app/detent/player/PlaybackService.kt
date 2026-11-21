package app.detent.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONArray

class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private val prefs by lazy { getSharedPreferences("playback", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    private val checkpoint = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                savePosition()
                handler.postDelayed(this, 5_000)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderers).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
        }
        restore()
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) saveQueue()
                if (events.containsAny(
                        Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_REPEAT_MODE_CHANGED
                    )
                ) {
                    savePosition()
                    handler.removeCallbacks(checkpoint)
                    if (player.isPlaying) handler.postDelayed(checkpoint, 5_000)
                }
            }
        })
        val activity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        handler.removeCallbacks(checkpoint)
        savePosition()
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun saveQueue() {
        val queue = JSONArray()
        for (index in 0 until player.mediaItemCount) {
            queue.put(Track.fromMediaItem(player.getMediaItemAt(index)).toJson())
        }
        val source = if (player.mediaItemCount == 0) null
        else QueueSource.fromMediaItem(player.getMediaItemAt(0))
        prefs.edit().putString("queue", queue.toString())
            .putString("sourceName", source?.name)
            .putString("sourcePlaylistId", source?.playlistId)
            .putInt("index", player.currentMediaItemIndex)
            .putLong("position", player.currentPosition)
            .apply()
    }

    private fun savePosition() {
        prefs.edit()
            .putInt("index", player.currentMediaItemIndex)
            .putLong("position", player.currentPosition)
            .putInt("repeat", player.repeatMode)
            .apply()
    }

    private fun restore() {
        val queue = runCatching {
            val data = JSONArray(prefs.getString("queue", "[]"))
            (0 until data.length()).map { Track.fromJson(data.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        if (queue.isNotEmpty()) {
            val index = prefs.getInt("index", 0).coerceIn(queue.indices)
            val source = prefs.getString("sourceName", null)?.let { QueueSource(it, prefs.getString("sourcePlaylistId", null)) }
                ?: if (queue.all { it.uri.scheme == "android.resource" }) QueueSource.Demo else QueueSource.Queue
            player.setMediaItems(
                queue.map { it.toMediaItem(source) }, index, prefs.getLong("position", 0).coerceAtLeast(0)
            )
        }
        player.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
            .coerceIn(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL)
    }

    private inner class SessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val connection = super.onConnect(session, controller)
            if (controller.packageName == packageName || !connection.isAccepted) return connection
            if (!controller.isTrusted) return MediaSession.ConnectionResult.reject()
            return MediaSession.ConnectionResult.accept(
                connection.availableSessionCommands,
                connection.availablePlayerCommands.buildUpon()
                    .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                    .remove(Player.COMMAND_SET_MEDIA_ITEM)
                    .build()
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val localItems = mediaItems.mapNotNull { item ->
                val uri = Uri.parse(item.mediaId)
                if (uri.scheme == "content" && uri.authority == "media" ||
                    uri.scheme == "android.resource" && uri.authority == packageName
                ) {
                    item.buildUpon().setUri(uri).build()
                } else {
                    null
                }
            }
            return Futures.immediateFuture(localItems)
        }
    }
}
