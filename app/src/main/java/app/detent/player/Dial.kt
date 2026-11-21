package app.detent.player

import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val DetentDegrees = 15f
private const val NotchDegrees = DetentDegrees / 2
private const val TickIntervalMs = 17L

@Composable
fun Dial(
    diameter: Dp,
    enabled: Boolean,
    gestureKey: Any?,
    label: String,
    backwardLabel: String,
    forwardLabel: String,
    centerIcon: Glyph,
    centerLabel: String,
    centerEnabled: Boolean,
    onCenter: () -> Unit,
    onStart: () -> Unit,
    onStep: (Int) -> Unit,
    onFinish: (Boolean) -> Unit,
    onAccessibleStep: (Int) -> Boolean,
) {
    val view = LocalView.current
    val start by rememberUpdatedState(onStart)
    val step by rememberUpdatedState(onStep)
    val finish by rememberUpdatedState(onFinish)
    var rotation by remember { mutableFloatStateOf(0f) }
    val tickEffect = if (Build.VERSION.SDK_INT >= 34) {
        HapticFeedbackConstants.SEGMENT_TICK
    } else {
        HapticFeedbackConstants.CLOCK_TICK
    }
    fun accessStep(direction: Int): Boolean {
        val changed = onAccessibleStep(direction)
        if (changed) {
            rotation = (rotation + direction * DetentDegrees) % 360
            view.performHapticFeedback(tickEffect)
        }
        return changed
    }

    Box(
        Modifier.size(diameter)
            .pointerInput(enabled, gestureKey) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f
                    val distance = (down.position - center).getDistance()
                    if (distance !in radius * .50f..radius) return@awaitEachGesture
                    val turnStart = rotation
                    down.consume()
                    start()
                    var angle = angleAt(down.position - center)
                    var accumulated = 0f
                    var applied = 0
                    var lastNotch = 0
                    var lastTick = 0L
                    var canceled = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val touch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (event.changes.any { it.id != down.id && it.pressed } || touch.isConsumed) break
                            if (!touch.pressed) {
                                canceled = false
                                break
                            }
                            val relative = touch.position - center
                            if (relative.getDistance() < radius * .34f) continue
                            val nextAngle = angleAt(relative)
                            accumulated += angularDelta(angle, nextAngle)
                            angle = nextAngle
                            val detents = (accumulated / DetentDegrees).toInt()
                            rotation = (turnStart + accumulated) % 360
                            if (detents != applied) {
                                step(detents - applied)
                                applied = detents
                            }
                            val notch = (accumulated / NotchDegrees).toInt()
                            if (notch != lastNotch) {
                                lastNotch = notch
                                val now = SystemClock.uptimeMillis()
                                if (now - lastTick >= TickIntervalMs) {
                                    view.performHapticFeedback(tickEffect)
                                    lastTick = now
                                }
                            }
                            touch.consume()
                        }
                    } finally {
                        rotation = (turnStart + if (canceled) 0f else applied * DetentDegrees) % 360
                        finish(canceled)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier.fillMaxSize().semantics {
                contentDescription = label
                if (enabled) customActions = listOf(
                    CustomAccessibilityAction(backwardLabel) { accessStep(-1) },
                    CustomAccessibilityAction(forwardLabel) { accessStep(1) }
                )
            }
        ) {
            val r = size.minDimension * .48f
            drawCircle(if (enabled) Dim else Line, r, style = Stroke(1.5.dp.toPx()))
            val notchCount = (360 / NotchDegrees).toInt()
            for (i in 0 until notchCount) {
                val radians = Math.toRadians((i * NotchDegrees + rotation - 90).toDouble())
                val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
                val major = i % 2 == 0
                drawLine(
                    when {
                        !enabled -> Line
                        i == 0 -> Ink
                        major -> Muted
                        else -> Dim
                    },
                    center + direction * (r * if (major) .79f else .875f),
                    center + direction * (r * .965f),
                    strokeWidth = (if (major) 1.6.dp else 1.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        Box(
            Modifier.size((diameter * .27f).coerceAtLeast(48.dp)).clip(CircleShape)
                .background(if (centerEnabled) Signal else Raised)
                .clickable(enabled = centerEnabled, role = Role.Button) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onCenter()
                }
                .semantics { contentDescription = centerLabel },
            contentAlignment = Alignment.Center
        ) {
            Symbol(centerIcon, (diameter * .105f).coerceAtLeast(21.dp), if (centerEnabled) Case else Dim)
        }
    }
}

private fun angleAt(point: Offset): Float = Math.toDegrees(atan2(point.y, point.x).toDouble()).toFloat()

internal fun angularDelta(previous: Float, next: Float): Float = (next - previous + 540f) % 360f - 180f
