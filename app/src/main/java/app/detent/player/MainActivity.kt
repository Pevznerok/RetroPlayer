package app.detent.player

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val player: PlayerViewModel by viewModels()
    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                player.setLibraryPermission(it)
            }
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Signal, onPrimary = Case, background = Case, onBackground = Ink,
                    surface = Panel, onSurface = Ink, surfaceVariant = Raised, onSurfaceVariant = Muted,
                    outline = Line
                )
            ) {
                Detent(player) { permission.launch(audioPermission) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        player.setLibraryPermission(checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
}

private enum class Screen(val title: String, val icon: Glyph) {
    Player("Player", Glyph.Record), Library("Library", Glyph.Library), Queue("Queue", Glyph.Queue)
}

@Composable
private fun Detent(player: PlayerViewModel, requestPermission: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Player) }
    val screenStates = rememberSaveableStateHolder()
    val playback by player.playback.collectAsStateWithLifecycle()
    val view = LocalView.current
    BackHandler(screen != Screen.Player) { screen = Screen.Player }

    Column(Modifier.fillMaxSize().background(Case).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 19.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symbol(Glyph.Record, 19.dp, Signal)
                DotText("DETENT", color = Ink, size = 24)
            }
            val status = when (screen) {
                Screen.Player -> playback.current?.format?.ifBlank { "LOCAL" } ?: "OFFLINE"
                Screen.Library -> "ON DEVICE"
                Screen.Queue -> "${playback.queue.size} TRACK${if (playback.queue.size == 1) "" else "S"}"
            }
            Box(
                Modifier.border(1.dp, Line, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                DotText(status, color = if (status == "FLAC") Signal else Muted, size = 10)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            screenStates.SaveableStateProvider(screen.name) {
                when (screen) {
                    Screen.Player -> NowPlaying(player, playback) { screen = Screen.Library }
                    Screen.Library -> LibraryScreen(player, playback.connected, requestPermission) { screen = Screen.Player }
                    Screen.Queue -> QueueScreen(player, playback) { screen = Screen.Library }
                }
            }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().height(58.dp).selectableGroup()) {
            for (tab in Screen.entries) {
                val selected = screen == tab
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(selected, role = Role.Tab) {
                        if (!selected) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        screen = tab
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Symbol(tab.icon, 20.dp, if (selected) Signal else Muted)
                    Text(tab.title, color = if (selected) Ink else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun PlainButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val view = LocalView.current
    Box(
        modifier.height(46.dp).clip(shape).background(Raised).border(1.dp, Line, shape)
            .clickable(enabled = enabled, role = Role.Button) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }.padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (enabled) Ink else Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
