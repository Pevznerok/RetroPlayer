package app.detent.player

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val QueueRowMinimumHeight = 60.dp
private val QueueRowSpacing = 5.dp

@Composable
fun QueueScreen(
    player: PlayerViewModel,
    state: Playback,
    diameter: Dp,
    openLibrary: () -> Unit,
    openQueues: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlainButton("Queues", onClick = openQueues)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaybackOrderKeys(player, state)
            }
        }
        if (state.queue.isEmpty()) {
            EmptyScreen("The queue is empty", "Choose a track or playlist to set the play order.") {
                PlainButton("Open library", onClick = openLibrary)
            }
        } else {
            key(state.source?.queueId) {
                QueueBody(player, state, diameter, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QueueBody(player: PlayerViewModel, state: Playback, diameter: Dp, modifier: Modifier) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceIn(state.queue.indices))
    var selected by remember { mutableIntStateOf(state.index.coerceIn(state.queue.indices)) }
    var startIndex by remember { mutableIntStateOf(selected) }

    LaunchedEffect(state.queue) {
        selected = state.index.coerceIn(state.queue.indices)
    }

    fun advance(steps: Int): Boolean {
        val next = (selected + steps).coerceIn(state.queue.indices)
        if (next == selected) return false
        selected = next
        return true
    }
    val wheel: @Composable () -> Unit = {
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
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val listBesideWheel = maxHeight < diameter + 80.dp && maxWidth >= diameter + 218.dp
        if (listBesideWheel) {
            Row(
                Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QueueList(player, state, listState, selected, Modifier.weight(1f).fillMaxHeight()) { selected = it }
                wheel()
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                QueueList(player, state, listState, selected, Modifier.weight(1f).fillMaxWidth()) { selected = it }
                Box(Modifier.padding(top = 8.dp, bottom = 8.dp)) { wheel() }
            }
        }
    }
}

@Composable
private fun QueueList(
    player: PlayerViewModel,
    state: Playback,
    listState: LazyListState,
    selected: Int,
    modifier: Modifier,
    onSelect: (Int) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val maximumHeightPx = constraints.maxHeight
        val density = LocalDensity.current
        val rowHeight = maxOf(QueueRowMinimumHeight, with(density) { 24.sp.toDp() } * 2 + 14.dp)
        val rowPx = with(density) { rowHeight.roundToPx() }
        val gapPx = with(density) { QueueRowSpacing.roundToPx() }
        val rows = minOf(state.queue.size, (maximumHeightPx + gapPx) / (rowPx + gapPx))
        if (rows == 0) return@BoxWithConstraints
        val height = with(density) { (rows * rowPx + (rows - 1) * gapPx).toDp() }

        LaunchedEffect(selected, rows, state.queue) {
            val first = listState.firstVisibleItemIndex
            if (selected < first || (selected == first && listState.firstVisibleItemScrollOffset != 0)) {
                listState.scrollToItem(selected)
            } else if (selected >= first + rows) {
                listState.scrollToItem(selected - rows + 1)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().height(height),
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start),
            verticalArrangement = Arrangement.spacedBy(QueueRowSpacing)
        ) {
            itemsIndexed(state.queue) { index, track ->
                TrackRow(
                    track = track,
                    selected = selected == index,
                    leading = if (index == state.index) "NOW" else (index + 1).toString().padStart(2, '0'),
                    showArtwork = true,
                    modifier = Modifier.height(rowHeight),
                    onClick = {
                        onSelect(index)
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
