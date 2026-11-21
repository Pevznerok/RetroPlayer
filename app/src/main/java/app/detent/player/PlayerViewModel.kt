package app.detent.player

import android.app.Application
import android.content.ComponentName
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Playback(
    val connected: Boolean = false,
    val queue: List<Track> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = false,
    val duration: Long = 0L,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val canSkipNext: Boolean = false,
    val error: String? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
}

data class Library(
    val allowed: Boolean = false,
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: Boolean = false,
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val session = MediaController.Builder(
        application, SessionToken(application, ComponentName(application, PlaybackService::class.java))
    ).buildAsync()
    private var controller: MediaController? = null

    private val _playback = MutableStateFlow(Playback())
    val playback = _playback.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()
    private val _library = MutableStateFlow(Library())
    val library = _library.asStateFlow()
    private val playlistStore = PlaylistStore(application)
    private val _playlists = MutableStateFlow(playlistStore.load())
    val playlists = _playlists.asStateFlow()
    val demos = demoTracks(application)

    private var loadJob: Job? = null
    private var observerRegistered = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            reloadLibrary(debounce = true)
        }
    }

    init {
        session.addListener({
            val connection = runCatching { session.get() }.getOrNull()
            if (connection == null) {
                _playback.value = Playback(error = "Couldn’t connect to playback")
            } else {
                controller = connection
                connection.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        updatePlayback(events.contains(Player.EVENT_TIMELINE_CHANGED))
                    }
                })
                updatePlayback(queueChanged = true)
            }
        }, ContextCompat.getMainExecutor(application))

        viewModelScope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(200)
            }
        }
    }

    fun setLibraryPermission(allowed: Boolean) {
        val resolver = getApplication<Application>().contentResolver
        if (!allowed) {
            loadJob?.cancel()
            if (observerRegistered) resolver.unregisterContentObserver(observer)
            observerRegistered = false
            _library.value = Library()
            return
        }
        _library.value = _library.value.copy(allowed = true)
        if (!observerRegistered) {
            resolver.registerContentObserver(audioCollection(), true, observer)
            observerRegistered = true
        }
        reloadLibrary()
    }

    fun reloadLibrary(debounce: Boolean = false) {
        if (!_library.value.allowed) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (debounce) delay(300)
            _library.value = _library.value.copy(loading = true, error = false)
            try {
                val tracks = withContext(Dispatchers.IO) { readLibrary(getApplication()) }
                _library.value = Library(allowed = true, tracks = tracks)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _library.value = _library.value.copy(loading = false, error = true)
            }
        }
    }

    fun playTracks(tracks: List<Track>, index: Int = 0) {
        val player = controller ?: return
        if (index !in tracks.indices) return
        player.setMediaItems(tracks.map(Track::toMediaItem), index, C.TIME_UNSET)
        player.prepare()
        player.play()
    }

    fun playQueueItem(index: Int) {
        val player = controller ?: return
        if (index !in 0 until player.mediaItemCount) return
        player.seekToDefaultPosition(index)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        player.play()
    }

    fun playNext(track: Track) {
        val player = controller ?: return
        val index = if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1
        player.addMediaItem(index, track.toMediaItem())
    }

    fun addToQueue(track: Track) {
        controller?.addMediaItem(track.toMediaItem())
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = controller ?: return
        val indices = 0 until player.mediaItemCount
        if (from in indices && to in indices) player.moveMediaItem(from, to)
    }

    fun removeQueueItem(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun shuffleUpcoming() {
        val player = controller ?: return
        val first = player.currentMediaItemIndex + 1
        val end = player.mediaItemCount
        if (end - first < 2) return
        val upcoming = (first until end).map(player::getMediaItemAt)
        val shuffled = upcoming.shuffled().let {
            if (it == upcoming) upcoming.drop(1) + upcoming.first() else it
        }
        player.replaceMediaItems(first, end, shuffled)
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun togglePlayback() {
        val player = controller ?: return
        if (_playback.value.playing) {
            player.pause()
        } else if (player.mediaItemCount > 0) {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun skipPrevious() {
        controller?.seekToPrevious()
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun seek(expectedIndex: Int, expectedId: String, ms: Long) {
        val player = controller ?: return
        if (player.currentMediaItemIndex != expectedIndex || player.currentMediaItem?.mediaId != expectedId) return
        if (_playback.value.duration <= 0) return
        val target = ms.coerceIn(0, _playback.value.duration)
        player.seekTo(target)
        _position.value = target
    }

    fun createPlaylist(name: String, track: Track? = null) {
        updatePlaylists(_playlists.value + playlistStore.create(name.trim(), track))
    }

    fun renamePlaylist(id: String, name: String) {
        updatePlaylists(_playlists.value.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deletePlaylist(id: String) {
        updatePlaylists(_playlists.value.filter { it.id != id })
    }

    fun addToPlaylist(id: String, track: Track) {
        updatePlaylists(_playlists.value.map {
            if (it.id == id && track.id !in it.trackIds) it.copy(trackIds = it.trackIds + track.id) else it
        })
    }

    fun removeFromPlaylist(id: String, track: Track) {
        updatePlaylists(_playlists.value.map {
            if (it.id == id) it.copy(trackIds = it.trackIds - track.id) else it
        })
    }

    private fun updatePlaylists(lists: List<SavedPlaylist>) {
        _playlists.value = lists
        playlistStore.save(lists)
    }

    private fun updatePlayback(queueChanged: Boolean) {
        val player = controller ?: return
        val queue = if (queueChanged) {
            (0 until player.mediaItemCount).map { Track.fromMediaItem(player.getMediaItemAt(it)) }
        } else {
            _playback.value.queue
        }
        val index = player.currentMediaItemIndex
        _position.value = player.currentPosition
        _playback.value = Playback(
            connected = true,
            queue = queue,
            index = index,
            playing = player.playWhenReady &&
                (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING),
            duration = player.duration.takeIf { it > 0 } ?: queue.getOrNull(index)?.durationMs ?: 0,
            repeat = player.repeatMode,
            canSkipNext = player.hasNextMediaItem(),
            error = player.playerError?.let {
                if (it.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) "Audio decoder couldn’t start"
                else "Couldn’t play this file"
            }
        )
    }

    override fun onCleared() {
        if (observerRegistered) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        MediaController.releaseFuture(session)
        super.onCleared()
    }
}
