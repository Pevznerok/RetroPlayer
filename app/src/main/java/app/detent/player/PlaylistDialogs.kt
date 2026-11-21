package app.detent.player

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AddToPlaylistDialog(player: PlayerViewModel, track: Track, onDismiss: () -> Unit) {
    val lists by player.playlists.collectAsStateWithLifecycle()
    var creating by remember(track.id) { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = "New playlist",
            onDismiss = onDismiss,
            onSave = { name ->
                player.createPlaylist(name, track)
                onDismiss()
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Add to playlist") },
            text = {
                if (lists.isEmpty()) Text("Create a playlist for ${track.title}.")
                else LazyColumn {
                    items(lists, key = SavedPlaylist::id) { list ->
                        val alreadyAdded = track.id in list.trackIds
                        TextButton(
                            onClick = {
                                player.addToPlaylist(list.id, track)
                                onDismiss()
                            },
                            enabled = !alreadyAdded
                        ) {
                            Text(if (alreadyAdded) "${list.name} · Added" else list.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { creating = true }) { Text("New playlist") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it.take(80) }, label = { Text("Name") },
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
