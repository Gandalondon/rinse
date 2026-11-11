package com.gandalondon.rinse.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp
import com.gandalondon.rinse.R
import com.gandalondon.rinse.Phase

// Same ink and paper as Hugin. Colour carries the state, so there is no light or dark theme.
val Ink = Color(0xFF1A1A1A)
val Paper = Color(0xFFFFFFFF)
val Brick = Color(0xFF7A1E1E)

/** Black at night, White when clean (morning), Brick when it cannot start. */
data class Palette(val bg: Color, val fg: Color, val muted: Color, val line: Color)

val Night = Palette(Ink, Paper, Color(0xFFA8A8A8), Color(0xFF333333))
val Day = Palette(Paper, Ink, Color(0xFF6B6B6B), Color(0xFFE6E6E6))
val Alarm = Palette(Brick, Paper, Color(0xFFE8B9B9), Color(0xFF943434))

fun paletteFor(phase: Phase) = when (phase) {
    Phase.Done -> Day
    Phase.Blocked -> Alarm
    else -> Night
}

// Geist (SIL Open Font License), bundled so the app looks the same on every phone.
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

// Two sizes: one heading, one for everything else. Same scale as Hugin.
object Type {
    val Hero = TextStyle(
        fontFamily = Geist,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        lineBreak = LineBreak.Heading, // balanced lines, no widows
    )
    val Body = TextStyle(
        fontFamily = Geist,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
        lineBreak = LineBreak.Paragraph, // avoids a lone last word
    )
    val Button = TextStyle(fontFamily = Geist, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)
}
