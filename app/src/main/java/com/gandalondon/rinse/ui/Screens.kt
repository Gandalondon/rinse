package com.gandalondon.rinse.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.gandalondon.rinse.Phase
import com.gandalondon.rinse.Programs
import com.gandalondon.rinse.Rinse
import com.gandalondon.rinse.RinseViewModel
import com.gandalondon.rinse.Ui
import kotlinx.coroutines.delay

@Composable
fun RinseApp(vm: RinseViewModel, ui: Ui, openUrl: (String) -> Unit) {
    val phase = if (ui.settings) Phase.Idle else ui.snap.phase
    val pal = paletteFor(phase)
    val bg by animateColorAsState(pal.bg, tween(400), label = "bg")
    val fg by animateColorAsState(pal.fg, tween(400), label = "fg")
    val p = pal.copy(bg = bg, fg = fg)

    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        ) {
            when {
                ui.settings -> SettingsScreen(vm, ui, p)
                ui.signIn != null -> SignInCode(vm, ui, p, openUrl)
                else -> when (ui.snap.phase) {
                    Phase.Unlinked -> Unlinked(vm, ui, p, openUrl)
                    Phase.Idle, Phase.Starting -> Idle(vm, ui, p)
                    Phase.Running -> Running(vm, ui, p)
                    Phase.Done -> DoneScreen(ui, p)
                    Phase.Blocked -> Blocked(vm, ui, p)
                }
            }
        }
    }
}

// --- Screens ---

@Composable
private fun ColumnScope.Idle(vm: RinseViewModel, ui: Ui, p: Palette) {
    val c = androidx.compose.ui.platform.LocalContext.current
    val key = Rinse.store(c).program
    Heading(Programs.name(key), p, icon = Icons.Filled.Settings, iconLabel = "Tonight's programme", onIcon = vm::openSettings)
    Body(Rinse.doneAt(c, key) ?: "Ready", p.muted)
    Spacer(Modifier.weight(1f))
    val starting = ui.busy || ui.snap.phase == Phase.Starting
    Pill(if (starting) "Starting" else "Start", p, filled = true, onClick = vm::start)
}

@Composable
private fun ColumnScope.Running(vm: RinseViewModel, ui: Ui, p: Palette) {
    val s = ui.snap
    Heading(Rinse.time(s.endAt), p)
    Body("${Programs.name(s.programKey)}, ${Rinse.left(s)}", p.muted)
    val start = s.endAt - ((Rinse.store(androidx.compose.ui.platform.LocalContext.current).minutes(s.programKey) ?: 120) * 60_000L)
    val fraction = if (s.progress > 0) s.progress / 100f
    else ((ui.clock - start).toFloat() / (s.endAt - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(Modifier.padding(top = 24.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(p.fg))
    }
    Spacer(Modifier.weight(1f))

    // Tap twice to stop, so a stray tap in the dark does not cancel the wash.
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) { if (armed) { delay(3000); armed = false } }
    Pill(
        when { ui.busy -> "Stopping"; armed -> "Tap again to stop"; else -> "Stop" }, p, filled = false,
        onClick = { if (armed) { armed = false; vm.stop() } else armed = true },
    )
}

@Composable
private fun ColumnScope.DoneScreen(ui: Ui, p: Palette) {
    Heading("Clean", p)
    Body("Finished at ${Rinse.time(ui.snap.endAt)}", p.muted)
}

@Composable
private fun ColumnScope.Blocked(vm: RinseViewModel, ui: Ui, p: Palette) {
    Heading(ui.snap.head, p)
    Body(ui.snap.body, p.muted)
    Spacer(Modifier.weight(1f))
    Pill(if (ui.busy) "Checking" else "Try again", p, filled = true, onClick = vm::retry)
}

@Composable
private fun ColumnScope.Unlinked(vm: RinseViewModel, ui: Ui, p: Palette, openUrl: (String) -> Unit) {
    Heading("Link your dishwasher", p)
    Body("Sign in to Home Connect once, with the account you use in the Bosch app.", p.muted)
    Spacer(Modifier.weight(1f))
    Pill(if (ui.busy) "Opening" else "Sign in", p, filled = true, onClick = { vm.signIn(openUrl) })
}

@Composable
private fun ColumnScope.SignInCode(vm: RinseViewModel, ui: Ui, p: Palette, openUrl: (String) -> Unit) {
    val code = ui.signIn ?: return
    Heading(code.userCode, p, icon = Icons.Filled.Close, iconLabel = "Cancel", onIcon = vm::cancelSignIn)
    Body("Approve Rinse on the Bosch page. If it asks for a code, use this one. Rinse carries on by itself.", p.muted)
    Spacer(Modifier.weight(1f))
    Pill("Open the Bosch page", p, filled = false, onClick = { openUrl(code.verifyUrl) })
}

@Composable
private fun ColumnScope.SettingsScreen(vm: RinseViewModel, ui: Ui, p: Palette) {
    val c = androidx.compose.ui.platform.LocalContext.current
    Heading("Tonight", p, icon = Icons.Filled.Close, iconLabel = "Close", onIcon = vm::closeSettings)
    Body("Back to Eco 50 tomorrow", p.muted)
    Column(Modifier.padding(top = 32.dp).weight(1f).verticalScroll(rememberScrollState())) {
        val current = Rinse.store(c).program
        for (key in ui.programs.ifEmpty { listOf(Programs.ECO50) }) {
            Row(
                Modifier.fillMaxWidth().clickable { vm.pick(key) }.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(Programs.name(key), style = Type.Body, color = p.fg)
                if (key == current) Text("Tonight", style = Type.Body, color = p.muted)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        }
        Text(
            "Unlink ${Rinse.store(c).haName}", style = Type.Body, color = p.muted,
            modifier = Modifier.padding(top = 32.dp).clickable { vm.signOut() }.padding(vertical = 8.dp),
        )
    }
}

// --- Parts ---

@Composable
private fun Heading(text: String, p: Palette, icon: ImageVector? = null, iconLabel: String = "", onIcon: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(text, style = Type.Hero, color = p.fg, modifier = Modifier.weight(1f))
        if (icon != null) {
            Box(
                Modifier.padding(start = 16.dp).size(48.dp).clip(RoundedCornerShape(24.dp)).clickable(onClick = onIcon),
                contentAlignment = Alignment.TopEnd,
            ) {
                Icon(icon, contentDescription = iconLabel, tint = p.fg, modifier = Modifier.padding(top = 8.dp).size(28.dp))
            }
        }
    }
}

@Composable
private fun Body(text: String, color: Color) {
    Text(text, style = Type.Body, color = color, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun Pill(label: String, p: Palette, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().height(64.dp).clip(shape)
            .then(if (filled) Modifier.background(p.fg) else Modifier.border(1.dp, p.fg, shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.Button, color = if (filled) p.bg else p.fg)
    }
}
