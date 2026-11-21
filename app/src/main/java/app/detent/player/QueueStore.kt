package app.detent.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class QueueSummary(val source: QueueSource, val count: Int, val name: String? = null) {
    val id: String = requireNotNull(source.queueId)

    fun title(playlists: List<SavedPlaylist>): String =
        name ?: playlists.firstOrNull { it.id == source.playlistId }?.name ?: source.name
}

data class QueueSnapshot(
    val source: QueueSource,
    val tracks: List<Track>,
    val index: Int,
    val positionMs: Long,
    val repeat: Int,
)

class QueueStore(context: Context) {
    private val prefs = context.getSharedPreferences("saved_queues", Context.MODE_PRIVATE)

    fun summaries(): List<QueueSummary> {
        val data = runCatching { JSONArray(prefs.getString("summaries", "[]")) }.getOrElse { JSONArray() }
        return (0 until data.length()).mapNotNull { index ->
            runCatching {
                val item = data.getJSONObject(index)
                QueueSummary(
                    QueueSource(item.getString("source"), item.optString("playlist").ifEmpty { null }, item.getString("id")),
                    item.getInt("count"),
                    item.optString("name").ifEmpty { null }
                )
            }.getOrNull()
        }
    }

    fun load(id: String): QueueSnapshot? {
        val summary = summaries().firstOrNull { it.id == id } ?: return null
        val raw = prefs.getString("queue_$id", null) ?: return null
        return runCatching {
            val data = JSONObject(raw)
            val entries = data.getJSONArray("tracks")
            val tracks = (0 until entries.length()).map { Track.fromJson(entries.getJSONObject(it)) }
            if (tracks.isEmpty()) return null
            QueueSnapshot(
                summary.source, tracks, data.getInt("index").coerceIn(tracks.indices),
                data.getLong("position").coerceAtLeast(0L), data.getInt("repeat")
            )
        }.getOrNull()
    }

    fun save(snapshot: QueueSnapshot) {
        val id = requireNotNull(snapshot.source.queueId)
        val summaries = summaries()
        val name = summaries.firstOrNull { it.id == id }?.name
        val summary = QueueSummary(snapshot.source, snapshot.tracks.size, name)
        val data = JSONObject().put("tracks", JSONArray(snapshot.tracks.map(Track::toJson)))
            .put("index", snapshot.index).put("position", snapshot.positionMs).put("repeat", snapshot.repeat)
        prefs.edit()
            .putString("summaries", encode(listOf(summary) + summaries.filterNot { it.id == id }))
            .putString("queue_$id", data.toString())
            .apply()
    }

    fun rename(id: String, name: String) {
        val updated = summaries().map { if (it.id == id) it.copy(name = name) else it }
        prefs.edit().putString("summaries", encode(updated)).apply()
    }

    fun remove(id: String) {
        prefs.edit()
            .putString("summaries", encode(summaries().filterNot { it.id == id }))
            .remove("queue_$id")
            .apply()
    }

    private fun encode(summaries: List<QueueSummary>): String = JSONArray(summaries.map {
        JSONObject().put("id", it.id).put("source", it.source.name).put("count", it.count)
            .put("playlist", it.source.playlistId.orEmpty()).put("name", it.name.orEmpty())
    }).toString()
}
