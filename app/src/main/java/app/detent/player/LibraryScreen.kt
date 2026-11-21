package app.detent.player

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Sort(val label: String, val comparator: Comparator<Track>) {
    Title("Title A–Z", compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }),
    Artist("Artist A–Z", compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }),
    Album("Album A–Z", compareBy(String.CASE_INSENSITIVE_ORDER) { it.album }),
    Duration("Longest first", compareByDescending { it.durationMs }),
}

private data class PlaylistEditor(val list: SavedPlaylist? = null)

private enum class LibraryTab(val label: String) {
    Songs("Songs"), Playlists("Playlists"), Favorites("Favorites")
}

@Composable
fun LibraryScreen(
    player: PlayerViewModel,
    connected: Boolean,
    requestPermission: () -> Unit,
    showPlayer: () -> Unit,
) {
    val library by player.library.collectAsStateWithLifecycle()
    val lists by player.playlists.collectAsStateWithLifecycle()
    val favorites by player.favorites.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.Title) }
    var activePlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<PlaylistEditor?>(null) }
    var addTrack by remember { mutableStateOf<Track?>(null) }
    var deletePlaylist by remember { mutableStateOf<SavedPlaylist?>(null) }
    val activePlaylist = lists.firstOrNull { it.id == activePlaylistId }
    val trackById = remember(library.tracks, player.demos) { (library.tracks + player.demos).associateBy(Track::id) }
    val visible = remember(library.tracks, query, sort) {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        library.tracks.filter { track ->
            terms.all { term -> listOf(track.title, track.artist, track.album).any { it.contains(term, ignoreCase = true) } }
        }.sortedWith(sort.comparator)
    }
    BackHandler(activePlaylist != null) { activePlaylistId = null }

    fun play(tracks: List<Track>, source: QueueSource, index: Int = 0) {
        player.playTracks(tracks, source, index)
        showPlayer()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).imePadding()) {
        if (activePlaylist != null) {
            val source = QueueSource(activePlaylist.name, activePlaylist.id)
            val available = remember(activePlaylist, trackById) { activePlaylist.trackIds.mapNotNull(trackById::get) }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Key(Glyph.Back, "Back to playlists") { activePlaylistId = null }
                Text(
                    activePlaylist.name, Modifier.weight(1f), color = Ink, fontSize = 23.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                TrackMenu("Playlist actions", listOf(
                    "Rename playlist" to { editor = PlaylistEditor(list = activePlaylist) },
                    "Delete playlist" to { deletePlaylist = activePlaylist }
                ))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlainButton("Play all", enabled = connected && available.isNotEmpty()) { play(available, source) }
                PlainButton("Shuffle", enabled = connected && available.size > 1) { play(available.shuffled(), source) }
            }
            val missing = activePlaylist.trackIds.size - available.size
            if (missing > 0) {
                Text("${if (missing == 1) "1 track is" else "$missing tracks are"} unavailable.", color = Muted,
                    fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            if (activePlaylist.trackIds.isEmpty()) {
                EmptyScreen("Empty playlist", "Add tracks from the menu next to each song in the library.") {
                    PlainButton("Browse songs") {
                        activePlaylistId = null
                        tab = LibraryTab.Songs
                    }
                }
            } else {
                LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(available, key = { _, track -> track.id }) { index, track ->
                        TrackRow(track, onClick = { if (connected) play(available, source, index) }) {
                            LibraryTrackMenu(track, connected, player, { addTrack = track }) {
                                player.removeFromPlaylist(activePlaylist.id, track)
                            }
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 11.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (entry in LibraryTab.entries) {
                    val selected = entry == tab
                    val shape = RoundedCornerShape(9.dp)
                    Box(
                        Modifier.weight(1f).clip(shape).background(if (selected) Raised else Case)
                            .border(1.dp, if (selected) Dim else Line, shape)
                            .selectable(selected, role = Role.Tab) {
                                if (!selected) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                tab = entry
                            }.padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(entry.label, color = if (selected) Ink else Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (tab == LibraryTab.Playlists) {
                if (lists.isEmpty()) {
                    EmptyScreen("No playlists", "Save a group of songs to play again in your own order.") {
                        PlainButton("New playlist") { editor = PlaylistEditor() }
                    }
                } else {
                    PlainButton("New playlist", Modifier.align(Alignment.End)) { editor = PlaylistEditor() }
                    LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(lists, key = SavedPlaylist::id) { list ->
                            val shape = RoundedCornerShape(8.dp)
                            Row(
                                Modifier.fillMaxWidth().clip(shape).background(Panel)
                                    .clickable { activePlaylistId = list.id }.padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Symbol(Glyph.Queue, 21.dp, Muted)
                                Text(list.name, Modifier.weight(1f), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                DotText("${list.trackIds.size}", size = 12)
                            }
                        }
                    }
                }
            } else if (tab == LibraryTab.Favorites) {
                val available = remember(favorites, trackById, sort) {
                    favorites.mapNotNull(trackById::get).sortedWith(sort.comparator)
                }
                when {
                    favorites.isEmpty() -> EmptyScreen("No favorites yet", "Tap the heart on the Player screen.") {}
                    available.isEmpty() && !library.allowed ->
                        EmptyScreen("Find your favorites", "Allow audio access to load your saved tracks.") {
                            PlainButton("Allow access", onClick = requestPermission)
                        }
                    available.isEmpty() -> EmptyScreen("Favorites unavailable", "Your saved tracks are missing from the library.") {}
                    else -> {
                        PlainButton("Play all", enabled = connected) { play(available, QueueSource.Favorites) }
                        LazyColumn(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            itemsIndexed(available, key = { _, track -> track.id }) { index, track ->
                                TrackRow(track, onClick = { if (connected) play(available, QueueSource.Favorites, index) }) {
                                    LibraryTrackMenu(track, connected, player, { addTrack = track }, favorite = true)
                                }
                            }
                        }
                    }
                }
            } else if (!library.allowed) {
                EmptyScreen("Find music on this device", "Allow access to audio files to scan your library.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlainButton("Allow access", onClick = requestPermission)
                        PlainButton("App settings") {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(9.dp),
                    placeholder = { Text("Search tracks, artists, albums") },
                    leadingIcon = { Symbol(Glyph.Search, 20.dp, Muted) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    DotText(if (library.loading) "SCANNING" else "${visible.size} TRACKS")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        var sortMenu by remember { mutableStateOf(false) }
                        Box {
                            PlainButton(sort.label) { sortMenu = true }
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                for (option in Sort.entries) {
                                    DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                        sort = option
                                        sortMenu = false
                                    })
                                }
                            }
                        }
                        Key(Glyph.Refresh, "Rescan library", enabled = !library.loading) { player.reloadLibrary() }
                    }
                }
                when {
                    library.error -> EmptyScreen("Couldn’t read the library", "Check that audio access is enabled.") {
                        PlainButton("Try again") { player.reloadLibrary() }
                    }
                    visible.isEmpty() && !library.loading -> {
                        val title = if (query.isBlank()) "No music found" else "No matching tracks"
                        val detail = if (query.isBlank()) "Copy music into the Music folder and rescan."
                        else "Try another track, artist, or album."
                        EmptyScreen(title, detail) {}
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        itemsIndexed(visible, key = { _, track -> track.id }) { index, track ->
                            val source = if (query.isBlank()) QueueSource.AllTracks else QueueSource.Search
                            TrackRow(track, onClick = { if (connected) play(visible, source, index) }) {
                                LibraryTrackMenu(track, connected, player, { addTrack = track })
                            }
                        }
                    }
                }
            }
        }
    }

    addTrack?.let { track ->
        AddToPlaylistDialog(player, track) { addTrack = null }
    }

    editor?.let { current ->
        PlaylistNameDialog(
            existingName = current.list?.name,
            onDismiss = { editor = null },
            onSave = { name ->
                if (current.list == null) player.createPlaylist(name)
                else player.renamePlaylist(current.list.id, name)
                editor = null
            }
        )
    }

    deletePlaylist?.let { list ->
        AlertDialog(
            onDismissRequest = { deletePlaylist = null },
            title = { Text("Delete playlist?") },
            text = { Text(list.name) },
            confirmButton = {
                TextButton(onClick = {
                    player.deletePlaylist(list.id)
                    deletePlaylist = null
                    activePlaylistId = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ deletePlaylist = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LibraryTrackMenu(
    track: Track,
    connected: Boolean,
    player: PlayerViewModel,
    addToPlaylist: () -> Unit,
    favorite: Boolean = false,
    remove: (() -> Unit)? = null,
) {
    TrackMenu("Actions for ${track.title}", buildList {
        if (connected) {
            add("Play next" to { player.playNext(track) })
            add("Add to queue" to { player.addToQueue(track) })
        }
        add("Add to playlist" to addToPlaylist)
        if (favorite) add("Remove from favorites" to { player.toggleFavorite(track) })
        if (remove != null) add("Remove from playlist" to remove)
    })
}

@Composable
fun TrackRow(
    track: Track,
    selected: Boolean = false,
    leading: String? = null,
    onClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val view = LocalView.current
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 58.dp).clip(shape)
            .background(if (selected) Raised else Panel)
            .border(1.dp, if (selected) Signal else Panel, shape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }.padding(start = 11.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leading != null) {
            DotText(leading, Modifier.width(32.dp), color = if (leading == "NOW") Signal else Muted, size = 11)
        }
        Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Text(track.title, color = Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (leading == null && track.format.isNotBlank()) DotText(track.format.take(5), size = 10)
        actions()
    }
}

@Composable
fun TrackMenu(label: String, actions: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Key(Glyph.More, label) { open = true }
        DropdownMenu(open, { open = false }) {
            for ((title, action) in actions) {
                DropdownMenuItem(text = { Text(title) }, onClick = {
                    open = false
                    action()
                })
            }
        }
    }
}
