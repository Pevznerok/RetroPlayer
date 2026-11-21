package app.detent.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player

@Composable
fun QueueScreen(player: PlayerViewModel, state: Playback, openLibrary: () -> Unit) {
    if (state.queue.isEmpty()) {
        EmptyScreen("The queue is empty", "Choose a track or playlist to set the play order.") {
            PlainButton("Open library", onClick = openLibrary)
        }
        return
    }
    val listState = rememberLazyListState()
    var selected by remember { mutableIntStateOf(state.index) }
    var startIndex by remember { mutableIntStateOf(selected) }
    LaunchedEffect(state.queue) {
        selected = state.index.coerceIn(state.queue.indices)
    }
    LaunchedEffect(selected) {
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == selected }) {
            listState.scrollToItem(selected)
        }
    }
    fun advance(steps: Int): Boolean {
        val next = (selected + steps).coerceIn(state.queue.indices)
        if (next == selected) return false
        selected = next
        return true
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        val diameter = (maxHeight * .28f).coerceIn(116.dp, 174.dp)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Play order", color = Ink, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Key(
                        Glyph.Shuffle, "Shuffle upcoming tracks",
                        enabled = state.queue.lastIndex - state.index >= 2,
                        onClick = player::shuffleUpcoming
                    )
                    val repeatLabel = when (state.repeat) {
                        Player.REPEAT_MODE_ALL -> "Repeat all"
                        Player.REPEAT_MODE_ONE -> "Repeat one"
                        else -> "Repeat off"
                    }
                    Key(
                        if (state.repeat == Player.REPEAT_MODE_ONE) Glyph.RepeatOne else Glyph.Repeat,
                        "$repeatLabel. Change repeat mode", active = state.repeat != Player.REPEAT_MODE_OFF,
                        onClick = player::cycleRepeat
                    )
                }
            }
            LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                itemsIndexed(state.queue) { index, track ->
                    TrackRow(
                        track = track,
                        selected = selected == index,
                        leading = if (index == state.index) "NOW" else (index + 1).toString().padStart(2, '0'),
                        onClick = {
                            selected = index
                            player.playQueueItem(index)
                        }
                    ) {
                        TrackMenu(
                            label = "Queue actions for ${track.title}",
                            actions = buildList {
                                if (index > 0) add("Move up" to { player.moveQueueItem(index, index - 1) })
                                if (index < state.queue.lastIndex) add("Move down" to { player.moveQueueItem(index, index + 1) })
                                add("Remove from queue" to { player.removeQueueItem(index) })
                            }
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Dial(
                    diameter = diameter,
                    enabled = state.queue.size > 1,
                    gestureKey = state.queue,
                    label = "Queue wheel",
                    backwardLabel = "Highlight previous track",
                    forwardLabel = "Highlight next track",
                    centerIcon = Glyph.Play,
                    centerLabel = "Play highlighted track",
                    centerEnabled = state.connected,
                    onCenter = { player.playQueueItem(selected) },
                    onStart = { startIndex = selected },
                    onStep = ::advance,
                    onFinish = { canceled -> if (canceled) selected = startIndex.coerceIn(state.queue.indices) },
                    onAccessibleStep = ::advance
                )
                DotText("BROWSE  ·  1 TRACK / CLICK", Modifier.padding(top = 4.dp), size = 11)
            }
        }
    }
}

@Composable
fun EmptyScreen(title: String, detail: String, action: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = 23.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            action()
        }
    }
}
