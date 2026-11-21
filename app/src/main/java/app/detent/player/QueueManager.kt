package app.detent.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun QueueManager(
    player: PlayerViewModel,
    state: Playback,
    saved: List<QueueSummary>,
    playlists: List<SavedPlaylist>,
    onDismiss: () -> Unit,
) {
    var rename by remember { mutableStateOf<QueueSummary?>(null) }
    var remove by remember { mutableStateOf<QueueSummary?>(null) }
    val activeId = state.source?.queueId
    val active = state.source?.takeIf { activeId != null }?.let { source ->
        QueueSummary(source, state.queue.size, saved.firstOrNull { it.id == activeId }?.name)
    }
    val queues = listOfNotNull(active) + saved.filterNot { it.id == activeId }

    val renaming = rename
    val removing = remove
    when {
        renaming != null -> NameDialog(
            title = "Rename queue",
            initialName = renaming.title(playlists),
            onDismiss = { rename = null },
            onSave = {
                player.renameQueue(renaming.id, it)
                rename = null
            }
        )
        removing != null -> AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("Remove saved queue?") },
            text = { Text(removing.title(playlists)) },
            confirmButton = {
                TextButton(onClick = {
                    player.removeSavedQueue(removing.id)
                    remove = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Queues") },
            text = {
                if (queues.isEmpty()) {
                    Text("Play tracks or save Library search results to create a queue.")
                } else {
                    LazyColumn(
                        Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(queues, key = QueueSummary::id) { queue ->
                            val current = queue.id == activeId
                            val title = queue.title(playlists)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Raised)
                                    .selectable(selected = current, enabled = !current && state.connected) {
                                        player.switchQueue(queue.id)
                                        onDismiss()
                                    }.padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val trackCount = "${queue.count} ${if (queue.count == 1) "track" else "tracks"}"
                                    DotText(if (current) "Active · $trackCount" else trackCount, size = 10)
                                }
                                TrackMenu("Actions for $title", buildList {
                                    add("Rename" to { rename = queue })
                                    if (!current) add("Remove saved queue" to { remove = queue })
                                })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
        )
    }
}
