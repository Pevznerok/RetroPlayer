package app.detent.player

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val format: String,
    val artwork: Uri? = null,
) {
    val id: String get() = uri.toString()

    fun toMediaItem(source: QueueSource): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artwork)
                .setDurationMs(durationMs)
                .setIsPlayable(true)
                .setExtras(Bundle().apply {
                    putString("format", format)
                    putString("sourceName", source.name)
                    putString("sourcePlaylistId", source.playlistId)
                })
                .build()
        )
        .build()

    fun toJson(): JSONObject = JSONObject()
        .put("uri", id).put("title", title).put("artist", artist).put("album", album)
        .put("duration", durationMs).put("format", format).put("artwork", artwork?.toString().orEmpty())

    companion object {
        fun fromMediaItem(item: MediaItem): Track {
            val metadata = item.mediaMetadata
            return Track(
                Uri.parse(item.mediaId),
                metadata.title?.toString().orEmpty(),
                metadata.artist?.toString().orEmpty(),
                metadata.albumTitle?.toString().orEmpty(),
                metadata.durationMs ?: 0L,
                metadata.extras?.getString("format").orEmpty(),
                metadata.artworkUri
            )
        }

        fun fromJson(value: JSONObject): Track = Track(
            Uri.parse(value.getString("uri")),
            value.getString("title"),
            value.getString("artist"),
            value.getString("album"),
            value.getLong("duration"),
            value.optString("format"),
            value.optString("artwork").takeIf(String::isNotBlank)?.let(Uri::parse)
        )
    }
}

data class QueueSource(val name: String, val playlistId: String? = null) {
    companion object {
        val AllTracks = QueueSource("All tracks")
        val Search = QueueSource("Search results")
        val Favorites = QueueSource("Favorites")
        val Demo = QueueSource("Room Tone")
        val Queue = QueueSource("Queue")

        fun fromMediaItem(item: MediaItem): QueueSource? {
            val extras = item.mediaMetadata.extras
            val name = extras?.getString("sourceName") ?: return null
            return QueueSource(name, extras.getString("sourcePlaylistId"))
        }
    }
}

fun audioCollection(): Uri = if (Build.VERSION.SDK_INT >= 29) {
    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
} else {
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
}

fun readLibrary(context: Context): List<Track> {
    val collection = audioCollection()
    val projection = mutableListOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE
    )
    if (Build.VERSION.SDK_INT >= 29) projection.add(MediaStore.MediaColumns.VOLUME_NAME)
    val audio = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val music = MediaStore.Audio.Media.IS_MUSIC
    val mime = MediaStore.Audio.Media.MIME_TYPE
    val name = MediaStore.Audio.Media.DISPLAY_NAME
    val selection = "($music != 0 OR $mime IN (?, ?) OR $name LIKE ?)"
    val result = mutableListOf<Track>()
    context.contentResolver.query(
        collection, projection.toTypedArray(), selection,
        arrayOf("audio/flac", "audio/x-flac", "%.flac"), null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val filename = cursor.getString(5).orEmpty()
            val volume = if (Build.VERSION.SDK_INT >= 29) cursor.getString(7) else null
            val contentUri = if (Build.VERSION.SDK_INT >= 29 && !volume.isNullOrBlank()) {
                MediaStore.Audio.Media.getContentUri(volume)
            } else {
                audio
            }
            result += Track(
                ContentUris.withAppendedId(contentUri, cursor.getLong(0)),
                cursor.getString(1).knownTag() ?: filename.substringBeforeLast('.').ifBlank { "Untitled track" },
                cursor.getString(2).knownTag() ?: "Unknown artist",
                cursor.getString(3).knownTag().orEmpty(),
                cursor.getLong(4).coerceAtLeast(0),
                filename.substringAfterLast('.', "").uppercase()
                    .ifBlank { cursor.getString(6).orEmpty().substringAfterLast('/').uppercase() }
            )
        }
    }
    return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
}

private fun String?.knownTag(): String? = this?.trim()?.takeIf {
    it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING
}

fun readArtwork(context: Context, track: Track): Bitmap? = runCatching {
    val bytes = if (track.artwork != null) {
        context.contentResolver.openInputStream(track.artwork)?.use { it.readBytes() }
    } else {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, track.uri)
            retriever.embeddedPicture
        } finally {
            retriever.release()
        }
    } ?: return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    var sample = 1
    while (maxOf(options.outWidth, options.outHeight) / sample > 1200) sample *= 2
    options.inSampleSize = sample
    options.inJustDecodeBounds = false
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}.getOrNull()

fun demoTracks(context: Context): List<Track> {
    fun resourceUri(id: Int): Uri {
        val path = context.resources.getResourceName(id).substringAfter(':')
        return Uri.parse("android.resource://${context.packageName}/$path")
    }
    val artwork = resourceUri(R.drawable.room_tone)
    return listOf(
        Track(resourceUri(R.raw.phase), "Phase Drift", "Detent demos", "Room Tone", 32_000, "FLAC", artwork),
        Track(resourceUri(R.raw.distant), "Distant Signal", "Detent demos", "Room Tone", 38_000, "FLAC", artwork),
        Track(resourceUri(R.raw.afterimage), "Afterimage", "Detent demos", "Room Tone", 44_000, "FLAC", artwork),
    )
}

data class SavedPlaylist(val id: String, val name: String, val trackIds: List<String>)

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): List<SavedPlaylist> {
        val data = runCatching { JSONArray(prefs.getString("lists", "[]")) }.getOrElse { JSONArray() }
        return (0 until data.length()).mapNotNull { index ->
            runCatching {
                val list = data.getJSONObject(index)
                val ids = list.getJSONArray("tracks")
                SavedPlaylist(
                    list.getString("id"), list.getString("name"),
                    (0 until ids.length()).map(ids::getString)
                )
            }.getOrNull()
        }
    }

    fun save(lists: List<SavedPlaylist>) {
        val data = JSONArray()
        for (list in lists) {
            data.put(JSONObject().put("id", list.id).put("name", list.name).put("tracks", JSONArray(list.trackIds)))
        }
        prefs.edit().putString("lists", data.toString()).apply()
    }

    fun create(name: String, firstTrack: Track?): SavedPlaylist =
        SavedPlaylist(UUID.randomUUID().toString(), name, listOfNotNull(firstTrack?.id))

    fun loadFavorites(): Set<String> = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()

    fun saveFavorites(ids: Set<String>) {
        prefs.edit().putStringSet("favorites", ids.toSet()).apply()
    }
}
