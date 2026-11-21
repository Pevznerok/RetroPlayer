package app.detent.player

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SeekStepMs = 5_000L

@Composable
fun NowPlaying(player: PlayerViewModel, state: Playback, openLibrary: () -> Unit) {
    val track = state.current
    val favorites by player.favorites.collectAsStateWithLifecycle()
    val position by player.position.collectAsStateWithLifecycle()
    var playlistTrack by remember { mutableStateOf<Track?>(null) }
    var preview by remember(track?.id, state.index) { mutableStateOf<Long?>(null) }
    var seekFrom by remember(track?.id, state.index) { mutableStateOf(0L) }
    val gestureKey = track?.let { state.index to it.id }
    val enabled = gestureKey != null && state.duration > 0
    val shape = RoundedCornerShape(12.dp)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val availableWidth = maxWidth - 36.dp
        val dialSize = minOf(availableWidth, (maxHeight * .48f).coerceIn(152.dp, 380.dp))
        val coverHeight = minOf(availableWidth, (maxHeight - dialSize - 158.dp).coerceIn(120.dp, 340.dp))
        val coverWidth = minOf(availableWidth, coverHeight * 1.35f)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.width(coverWidth).height(coverHeight).clip(shape).background(Panel).border(1.dp, Line, shape)
            ) {
                AlbumCover(track, Modifier.matchParentSize())
                if (track == null) {
                    Box(
                        Modifier.align(Alignment.BottomStart).padding(12.dp)
                            .background(Case, RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        DotText("FLAC DEMO", size = 11, color = Ink)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (track == null) {
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 53.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pick a track", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        Text("From your device", color = Muted, fontSize = 13.sp)
                    }
                    PlainButton("Try demo", enabled = state.connected) { player.playTracks(player.demos, QueueSource.Demo) }
                }
            } else {
                Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 53.dp)) {
                    Text(
                        track.title, color = Ink, fontSize = 25.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 29.sp
                    )
                    Text(
                        track.artist, color = Muted, fontSize = 14.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            state.error?.let {
                Text(it, color = Signal, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(4.dp))
            PositionReadout(position, state.duration, preview)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val key = Modifier.weight(1f)
                PlaybackOrderKeys(player, state, modifier = key, framed = false)
                Key(
                    Glyph.Previous, "Previous track", enabled = track != null, modifier = key,
                    framed = false, onClick = player::skipPrevious
                )
                Key(
                    Glyph.Next, "Next track", enabled = state.canSkipNext, modifier = key,
                    framed = false, onClick = player::skipNext
                )
                Key(Glyph.PlaylistAdd, "Add to playlist", enabled = track != null, modifier = key, framed = false) {
                    playlistTrack = track
                }
                val favorite = track != null && track.id in favorites
                Key(
                    if (favorite) Glyph.HeartFilled else Glyph.Heart,
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    enabled = track != null, active = favorite, modifier = key, framed = false
                ) {
                    track?.let(player::toggleFavorite)
                }
            }
            Dial(
                diameter = dialSize,
                enabled = enabled,
                gestureKey = gestureKey,
                label = "Seek wheel",
                backwardLabel = "Back five seconds",
                forwardLabel = "Forward five seconds",
                centerIcon = if (state.playing) Glyph.Pause else Glyph.Play,
                centerLabel = if (state.playing) "Pause" else if (track != null) "Play" else "Open library",
                centerEnabled = state.connected,
                onCenter = { if (track == null) openLibrary() else player.togglePlayback() },
                onStart = { seekFrom = position.coerceIn(0, state.duration) },
                onStep = { steps ->
                    val from = preview ?: seekFrom
                    preview = (from + steps * SeekStepMs).coerceIn(0, state.duration)
                },
                onFinish = { canceled ->
                    val target = preview
                    preview = null
                    if (!canceled && target != null && gestureKey != null && target != seekFrom) {
                        player.seek(gestureKey.first, gestureKey.second, target)
                    }
                },
                onAccessibleStep = { step ->
                    val target = (position + step * SeekStepMs).coerceIn(0, state.duration)
                    if (target == position || gestureKey == null) {
                        false
                    } else {
                        player.seek(gestureKey.first, gestureKey.second, target)
                        true
                    }
                }
            )
        }
    }
    playlistTrack?.let { AddToPlaylistDialog(player, it) { playlistTrack = null } }
}

@Composable
private fun PositionReadout(position: Long, duration: Long, preview: Long?) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(2.dp)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(3.dp).clip(shape).background(Line).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Signal))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DotText(timestamp(position), color = Ink)
            if (preview != null) DotText("SEEK ${timestamp(preview)}", color = Signal)
            DotText(timestamp(duration))
        }
    }
}

@Composable
private fun AlbumCover(track: Track?, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(track?.id) { mutableStateOf<Bitmap?>(null) }
    val demo = track == null || track.artwork != null
    LaunchedEffect(track?.id) {
        if (track != null && !demo) {
            bitmap = withContext(Dispatchers.IO) { readArtwork(context, track) }
        }
    }

    Box(modifier) {
        val cover = bitmap
        when {
            demo -> Image(
                painterResource(R.drawable.room_tone), null,
                Modifier.matchParentSize(), contentScale = ContentScale.Crop
            )
            cover != null -> Image(
                cover.asImageBitmap(), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop
            )
            else -> {
                Canvas(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Raised, Case)))) {
                    val radius = size.minDimension * .36f
                    for (index in 1..24) {
                        drawCircle(Line, radius * index / 24f, style = Stroke(1.dp.toPx()))
                    }
                    drawCircle(Panel, radius * .28f)
                    drawCircle(Dim, radius * .28f, style = Stroke(1.dp.toPx()))
                    drawCircle(Signal, 2.dp.toPx())
                }
            }
        }
    }
}
