package app.detent.player

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import java.util.Locale

val Case = Color(0xFF141414)
val Panel = Color(0xFF1C1C1B)
val Raised = Color(0xFF232322)
val Line = Color(0xFF353633)
val Ink = Color(0xFFF1EEE6)
val Muted = Color(0xFFAAA8A0)
val Dim = Color(0xFF777873)
val Signal = Color(0xFFFF665D)
val DotFont = FontFamily(Font(R.font.dot_gothic))

@Composable
fun DotText(text: String, modifier: Modifier = Modifier, color: Color = Muted, size: Int = 12, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text, modifier, color = color, fontFamily = DotFont, fontSize = size.sp,
        letterSpacing = 1.sp, lineHeight = (size + 5).sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis
    )
}

enum class Glyph {
    Play, Pause, Previous, Next, Record, Library, Queue, Search, More, Back, Add, Repeat,
    RepeatOne, Shuffle, Close, Refresh, PlaylistAdd, Heart, HeartFilled
}

@Composable
fun Symbol(glyph: Glyph, size: Dp, color: Color = Ink) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val width = 1.6f * unit
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun line(x: Float, y: Float, endX: Float, endY: Float) =
            drawLine(color, point(x, y), point(endX, endY), width, StrokeCap.Round)
        fun triangle(x: Float, y: Float, tip: Float, halfHeight: Float) {
            drawPath(Path().apply {
                moveTo(x * unit, (y - halfHeight) * unit)
                lineTo(tip * unit, y * unit)
                lineTo(x * unit, (y + halfHeight) * unit)
                close()
            }, color)
        }
        fun chain(vararg points: Pair<Float, Float>) {
            drawPath(Path().apply {
                moveTo(points.first().first * unit, points.first().second * unit)
                for ((x, y) in points.drop(1)) lineTo(x * unit, y * unit)
            }, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        when (glyph) {
            Glyph.Play -> triangle(7f, 12f, 19f, 7f)
            Glyph.Pause -> {
                drawRoundRect(color, point(6f, 5f), Size(4f * unit, 14f * unit))
                drawRoundRect(color, point(14f, 5f), Size(4f * unit, 14f * unit))
            }
            Glyph.Previous -> {
                triangle(19f, 12f, 7f, 6f)
                line(5f, 6f, 5f, 18f)
            }
            Glyph.Next -> {
                triangle(5f, 12f, 17f, 6f)
                line(19f, 6f, 19f, 18f)
            }
            Glyph.Record -> {
                drawCircle(color, 8.8f * unit, style = Stroke(width))
                drawCircle(color, 2f * unit)
            }
            Glyph.Library -> {
                line(4f, 5f, 4f, 19f)
                line(9f, 5f, 9f, 19f)
                line(14f, 5f, 14f, 19f)
                line(18f, 5f, 21f, 18f)
            }
            Glyph.Queue -> {
                line(3f, 6f, 21f, 6f)
                line(3f, 12f, 16f, 12f)
                line(3f, 18f, 12f, 18f)
                triangle(17f, 17f, 22f, 3f)
            }
            Glyph.Search -> {
                drawCircle(color, 5.5f * unit, point(10f, 10f), style = Stroke(width))
                line(14f, 14f, 20f, 20f)
            }
            Glyph.More -> for (x in 6..18 step 6) drawCircle(color, 1.3f * unit, point(x.toFloat(), 12f))
            Glyph.Back -> {
                chain(11f to 5f, 4f to 12f, 11f to 19f)
                line(4f, 12f, 21f, 12f)
            }
            Glyph.Add -> {
                line(5f, 12f, 19f, 12f)
                line(12f, 5f, 12f, 19f)
            }
            Glyph.PlaylistAdd -> {
                line(3f, 6f, 17f, 6f)
                line(3f, 11f, 12f, 11f)
                line(3f, 16f, 10f, 16f)
                line(14f, 16f, 22f, 16f)
                line(18f, 12f, 18f, 20f)
            }
            Glyph.Heart, Glyph.HeartFilled -> {
                val heart = Path().apply {
                    moveTo(12f * unit, 20f * unit)
                    cubicTo(8f * unit, 17f * unit, 3f * unit, 13f * unit, 3f * unit, 9f * unit)
                    cubicTo(3f * unit, 4f * unit, 9f * unit, 2.5f * unit, 12f * unit, 7f * unit)
                    cubicTo(15f * unit, 2.5f * unit, 21f * unit, 4f * unit, 21f * unit, 9f * unit)
                    cubicTo(21f * unit, 13f * unit, 16f * unit, 17f * unit, 12f * unit, 20f * unit)
                    close()
                }
                if (glyph == Glyph.HeartFilled) drawPath(heart, color)
                else drawPath(heart, color, style = Stroke(width, join = StrokeJoin.Round))
            }
            Glyph.Repeat, Glyph.RepeatOne -> {
                chain(3f to 10f, 3f to 6f, 20f to 6f)
                chain(16f to 2f, 20f to 6f, 16f to 10f)
                chain(21f to 14f, 21f to 18f, 4f to 18f)
                chain(8f to 14f, 4f to 18f, 8f to 22f)
                if (glyph == Glyph.RepeatOne) line(12f, 10f, 12f, 14f)
            }
            Glyph.Shuffle -> {
                chain(3f to 6f, 7f to 6f, 17f to 18f, 21f to 18f)
                chain(17f to 14f, 21f to 18f, 17f to 22f)
                chain(3f to 18f, 7f to 18f, 9f to 15f)
                chain(13f to 10f, 17f to 6f, 21f to 6f)
                chain(17f to 2f, 21f to 6f, 17f to 10f)
            }
            Glyph.Close -> {
                line(6f, 6f, 18f, 18f)
                line(18f, 6f, 6f, 18f)
            }
            Glyph.Refresh -> {
                drawArc(color, -64f, -284f, false, point(4f, 4f), Size(16f * unit, 16f * unit), style = Stroke(width))
                chain(20f to 6f, 20f to 13f, 14f to 13f)
            }
        }
    }
}

@Composable
fun Key(
    glyph: Glyph,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.size(48.dp).clip(shape)
            .then(if (framed) Modifier.background(Raised).border(1.dp, Line, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }.semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        val color = when {
            !enabled -> Dim
            active -> Signal
            framed -> Ink
            else -> Muted
        }
        Symbol(glyph, 21.dp, color)
    }
}

@Composable
fun PlaybackOrderKeys(
    player: PlayerViewModel,
    state: Playback,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    Key(
        Glyph.Shuffle, "Shuffle upcoming tracks", enabled = state.queue.lastIndex - state.index >= 2,
        modifier = modifier, framed = framed, onClick = player::shuffleUpcoming
    )
    val repeatLabel = when (state.repeat) {
        Player.REPEAT_MODE_ALL -> "Repeat all"
        Player.REPEAT_MODE_ONE -> "Repeat one"
        else -> "Repeat off"
    }
    Key(
        if (state.repeat == Player.REPEAT_MODE_ONE) Glyph.RepeatOne else Glyph.Repeat,
        "$repeatLabel. Change repeat mode", enabled = state.current != null,
        active = state.repeat != Player.REPEAT_MODE_OFF, modifier = modifier, framed = framed,
        onClick = player::cycleRepeat
    )
}

fun timestamp(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }
}
