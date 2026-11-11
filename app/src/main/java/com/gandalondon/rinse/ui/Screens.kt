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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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

// --- Screens: one statement at the top, nothing in small print ---

@Composable
private fun ColumnScope.Idle(vm: RinseViewModel, ui: Ui, p: Palette) {
    val c = LocalContext.current
    Heading(Rinse.statement(c, ui.snap.copy(phase = Phase.Idle)), p,
        icon = Icons.Filled.Settings, iconLabel = "Tonight's programme", onIcon = vm::openSettings)
    Spacer(Modifier.weight(1f))
    val starting = ui.busy || ui.snap.phase == Phase.Starting
    Pill(if (starting) "Starting" else "Start", p, filled = true, onClick = vm::start)
}

@Composable
private fun ColumnScope.Running(vm: RinseViewModel, ui: Ui, p: Palette) {
    val c = LocalContext.current
    val s = ui.snap
    Heading(Rinse.statement(c, s), p)
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
    Heading(Rinse.statement(LocalContext.current, ui.snap), p)
}

@Composable
private fun ColumnScope.Blocked(vm: RinseViewModel, ui: Ui, p: Palette) {
    Heading(ui.snap.head, p)
    Spacer(Modifier.weight(1f))
    Pill(if (ui.busy) "Checking" else "Try again", p, filled = true, onClick = vm::retry)
}

@Composable
private fun ColumnScope.Unlinked(vm: RinseViewModel, ui: Ui, p: Palette, openUrl: (String) -> Unit) {
    Heading("Link your dishwasher.", p)
    Spacer(Modifier.weight(1f))
    Pill(if (ui.busy) "Opening" else "Sign in with Bosch", p, filled = true, onClick = { vm.signIn(openUrl) })
}

@Composable
private fun ColumnScope.SignInCode(vm: RinseViewModel, ui: Ui, p: Palette, openUrl: (String) -> Unit) {
    val code = ui.signIn ?: return
    Heading("Approve Rinse on the Bosch page. Code ${code.userCode}.", p,
        icon = Icons.Filled.Close, iconLabel = "Cancel", onIcon = vm::cancelSignIn)
    Spacer(Modifier.weight(1f))
    Pill("Open the Bosch page", p, filled = false, onClick = { openUrl(code.verifyUrl) })
}

@Composable
private fun ColumnScope.SettingsScreen(vm: RinseViewModel, ui: Ui, p: Palette) {
    val c = LocalContext.current
    Heading("Just this once.", p, icon = Icons.Filled.Close, iconLabel = "Close", onIcon = vm::closeSettings)
    Column(Modifier.padding(top = 32.dp).weight(1f).verticalScroll(rememberScrollState())) {
        val current = Rinse.store(c).program
        for (key in ui.programs.ifEmpty { listOf(Programs.ECO50) }) {
            Row(
                Modifier.fillMaxWidth().clickable { vm.pick(key) }.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Programs.name(key), style = Type.Body, color = p.fg)
                if (key == current) Icon(Icons.Filled.Check, contentDescription = "Tonight", tint = p.fg, modifier = Modifier.size(24.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        }
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
