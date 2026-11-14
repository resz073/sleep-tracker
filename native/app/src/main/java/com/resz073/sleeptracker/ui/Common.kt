package com.resz073.sleeptracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resz073.sleeptracker.show.LegendItem
import com.resz073.sleeptracker.show.Rich
import com.resz073.sleeptracker.show.Stat
import com.resz073.sleeptracker.show.Tone

/* The current app's building blocks (src/styles.css), so each screen reads like the web app. */

/** Nunito at a size in sp (the web app's px) */
fun font(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontFamily = Nunito, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.4f).sp)

/** A section card (.card) */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = c.card, contentColor = c.ink) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** The night-sky card at the top of Today and Schedule (.sky) */
@Composable
fun SkyCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = c.sky, contentColor = c.skyInk) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

/** A card's heading (.card h2) */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = font(17f, FontWeight.ExtraBold), color = LocalAppColors.current.ink, modifier = modifier.padding(bottom = 10.dp))
}

private val GLUE = Regex("""(\d) (AM|PM|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|nights?)\b""")

/** Keeps "6:15 AM", "13 Nov" and "19 nights" on one line when text wraps */
fun glued(text: String): String = text.replace(GLUE, "$1\u00A0$2")

/** Small grey text (.sm) */
@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Float = 13f) {
    Text(text, modifier = modifier, style = font(size), color = LocalAppColors.current.mute)
}

fun toneColor(tone: Tone, c: AppColors): Color? = when (tone) {
    Tone.GOOD -> c.good
    Tone.BAD -> c.bad
    Tone.WARN -> c.warn
    Tone.NONE -> null
}

/** [r] with its bold and coloured parts; bold parts without a colour take [boldColor] if given */
fun richString(r: Rich, c: AppColors, boldColor: Color?): AnnotatedString = buildAnnotatedString {
    for (s in r.spans) {
        val color = toneColor(s.tone, c) ?: (if (s.bold) boldColor else null) ?: Color.Unspecified
        if (!s.bold && color == Color.Unspecified) append(s.text)
        else withStyle(SpanStyle(color = color, fontWeight = if (s.bold) FontWeight.ExtraBold else null)) { append(s.text) }
    }
}

@Composable
fun RichText(r: Rich, style: TextStyle, color: Color, modifier: Modifier = Modifier, boldColor: Color? = null, align: TextAlign? = null) {
    Text(richString(r, LocalAppColors.current, boldColor), modifier = modifier, style = style, color = color, textAlign = align ?: TextAlign.Start)
}

/** The current app's button styles: .btn, .tonal, .text, .danger, .dangerfill, and the sky card's three */
enum class Btn { FILLED, TONAL, TEXT, DANGER, DANGER_FILL, SKY, SKY_DAWN, SKY_GHOST }

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: Btn = Btn.FILLED, enabled: Boolean = true) {
    val c = LocalAppColors.current
    val (bg, fg) = when (kind) {
        Btn.FILLED -> c.acc to c.onAcc
        Btn.TONAL -> c.accSoft to c.acc
        Btn.TEXT -> Color.Transparent to c.acc
        Btn.DANGER -> Color.Transparent to c.bad
        Btn.DANGER_FILL -> c.bad to c.onBad
        Btn.SKY -> c.skyAcc to c.sky
        Btn.SKY_DAWN -> c.skyDawn to c.onSkyDawn
        Btn.SKY_GHOST -> Color.Transparent to c.skyInk
    }
    val small = kind == Btn.TEXT || kind == Btn.DANGER
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (small) 40.dp else 48.dp).alpha(if (enabled) 1f else 0.45f),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg, disabledContainerColor = bg, disabledContentColor = fg),
        border = if (kind == Btn.SKY_GHOST) BorderStroke(1.5.dp, c.skyLine) else null,
        contentPadding = if (small) PaddingValues(horizontal = 10.dp, vertical = 8.dp) else PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text, style = font(15f, FontWeight.ExtraBold), textAlign = TextAlign.Center)
    }
}

/** A segmented control (.seg): Month / Week / 3 days, Automatic / Set a time */
@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Row(
        modifier.fillMaxWidth().border(1.5.dp, c.line, CircleShape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Surface(
                onClick = { onSelect(i) }, shape = CircleShape, color = if (on) c.accSoft else Color.Transparent, contentColor = if (on) c.acc else c.mute,
                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                    Text(label, style = font(15f, FontWeight.ExtraBold), color = if (on) c.acc else c.mute, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Figures in a grid (.mstats, or .grid2 with bigger numbers) */
@Composable
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier, columns: Int = 3, bigSize: Float = 17f, labelSize: Float = 12.5f) {
    val c = LocalAppColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (columns == 2) 16.dp else 10.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (columns == 2) 12.dp else 10.dp)) {
                row.forEach { s ->
                    Column(Modifier.weight(1f)) {
                        Text(s.big, style = font(bigSize, FontWeight.ExtraBold).copy(lineHeight = (bigSize * 1.25f).sp), color = toneColor(s.tone, c) ?: c.ink)
                        Text(s.label, style = font(labelSize).copy(lineHeight = (labelSize * 1.3f).sp), color = c.mute)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A night not logged, as a chip that logs it (.chip) */
@Composable
fun WarnChip(text: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(onClick = onClick, shape = CircleShape, color = c.warnSoft, contentColor = c.warn) {
        Text(text, style = font(14f, FontWeight.ExtraBold), color = c.warn, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
    }
}

/** A tip box (.note) */
@Composable
fun Note(r: Rich) {
    val c = LocalAppColors.current
    RichText(
        r, font(14f), c.ink,
        Modifier.padding(top = 10.dp).fillMaxWidth().background(c.card2, RoundedCornerShape(14.dp)).padding(horizontal = 13.dp, vertical = 11.dp),
    )
}

/** A chart's key (.legend) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<LegendItem>) {
    val c = LocalAppColors.current
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(item.mark)
                Spacer(Modifier.width(6.dp))
                Text(item.text, style = font(12.5f), color = c.mute)
            }
        }
    }
}

@Composable
private fun Swatch(mark: LegendItem.Mark) {
    val c = LocalAppColors.current
    when (mark) {
        LegendItem.Mark.LINE -> Box(Modifier.size(16.dp, 3.dp).background(c.acc, RoundedCornerShape(2.dp)))
        LegendItem.Mark.GOAL -> Canvas(Modifier.size(16.dp, 2.dp)) {
            drawLine(c.good, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 2.dp.toPx())))
        }
        LegendItem.Mark.DEFICIT -> Box(Modifier.size(11.dp).background(c.badSoft, RoundedCornerShape(3.dp)).border(1.dp, c.bad, RoundedCornerShape(3.dp)))
        LegendItem.Mark.SURPLUS -> Box(Modifier.size(11.dp).background(c.goodSoft, RoundedCornerShape(3.dp)).border(1.dp, c.good, RoundedCornerShape(3.dp)))
        LegendItem.Mark.MISSING -> Box(Modifier.size(11.dp, 3.dp).background(c.warn, RoundedCornerShape(2.dp)))
        LegendItem.Mark.LIVE -> Box(Modifier.size(9.dp).border(2.dp, c.acc, CircleShape))
        LegendItem.Mark.SLEEP -> Box(Modifier.size(16.dp, 10.dp).background(c.acc, RoundedCornerShape(3.dp)))
        LegendItem.Mark.NAP -> Box(Modifier.size(16.dp, 6.dp).alpha(0.5f).background(c.acc, RoundedCornerShape(3.dp)))
        LegendItem.Mark.PLAN -> Box(Modifier.size(16.dp, 10.dp).background(c.accSoft, RoundedCornerShape(3.dp)).border(1.dp, c.line, RoundedCornerShape(3.dp)))
    }
}

/** A menu like the current app's <select>: the chosen option, and the list when tapped. [small]: the compact one in a card's title row */
@Composable
fun <T> MenuField(options: List<Pair<T, String>>, selected: T?, placeholder: String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true }, shape = RoundedCornerShape(if (small) 999.dp else 14.dp), color = c.bg, contentColor = c.ink,
            border = BorderStroke(1.5.dp, c.line), modifier = if (small) Modifier else Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.heightIn(min = if (small) 36.dp else 48.dp).padding(horizontal = 12.dp, vertical = if (small) 6.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    options.firstOrNull { it.first == selected }?.second ?: placeholder,
                    style = font(if (small) 13f else 15f, if (small) FontWeight.Bold else FontWeight.Normal), color = c.ink,
                    modifier = if (small) Modifier else Modifier.weight(1f),
                )
                Text("  ▾", style = font(if (small) 13f else 15f), color = c.mute)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = font(15f, if (v == selected) FontWeight.ExtraBold else FontWeight.Normal)) },
                    onClick = { open = false; onSelect(v) },
                )
            }
        }
    }
}
