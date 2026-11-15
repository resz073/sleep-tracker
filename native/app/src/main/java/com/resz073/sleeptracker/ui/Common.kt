package com.resz073.sleeptracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resz073.sleeptracker.R
import com.resz073.sleeptracker.show.LegendItem
import com.resz073.sleeptracker.show.Rich
import com.resz073.sleeptracker.show.Stat
import com.resz073.sleeptracker.show.Tone

/* The building blocks every screen uses: Lamplight's layout, in the theme's colours. */

/** Atkinson Hyperlegible Next at a size in sp */
fun font(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(fontFamily = Atkinson, fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.4f).sp)

/** Literata, for times, figures and headings */
fun serif(size: Float, weight: FontWeight = FontWeight.Normal, lineHeight: Float = size * 1.2f): TextStyle =
    TextStyle(fontFamily = Literata, fontSize = size.sp, fontWeight = weight, lineHeight = lineHeight.sp)

/** The space at a screen's sides */
val SIDE = 24.dp

/** A screen's title, at the top of what scrolls */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = serif(34f, FontWeight.Medium, 44f), color = LocalAppColors.current.ink, modifier = modifier.padding(start = SIDE, end = SIDE, top = 8.dp))
}

/** A section's heading */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, size: Float = 22f) {
    Text(text, style = serif(size, FontWeight.Medium, size * 1.28f), color = LocalAppColors.current.ink, modifier = modifier)
}

/** The line between sections */
@Composable
fun SectionDivider() {
    HorizontalDivider(Modifier.padding(vertical = 24.dp), color = LocalAppColors.current.line)
}

/**
 * Today's and Schedule's layout: a top part on the ground, then a sheet in the panel colour that reaches the
 * bottom of the screen. [glow]: the faint lamp light behind the top part, in dark mode.
 */
@Composable
fun HeroSheet(glow: Boolean, top: @Composable ColumnScope.() -> Unit, sheet: @Composable ColumnScope.() -> Unit) {
    val c = LocalAppColors.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        var topHeight by remember { mutableStateOf(0.dp) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val lamp = if (glow && c.dark) Modifier.drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(c.acc.copy(alpha = 0.075f), c.acc.copy(alpha = 0.025f), Color.Transparent),
                        center = Offset(size.width * 0.45f, size.height * 0.42f), radius = size.width * 0.8f,
                    ),
                    radius = size.width * 0.8f, center = Offset(size.width * 0.45f, size.height * 0.42f),
                )
            } else Modifier
            Column(
                Modifier.fillMaxWidth().then(lamp).onSizeChanged { topHeight = with(density) { it.height.toDp() } },
                content = top,
            )
            Column(
                Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .heightIn(min = (viewport - topHeight - 24.dp).coerceAtLeast(0.dp))
                    .background(c.panel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(start = SIDE, end = SIDE, top = 24.dp, bottom = 28.dp),
                content = sheet,
            )
        }
    }
}

private val GLUE = Regex("""(\d) (AM|PM|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|nights?)\b""")

/** Keeps "6:15 AM", "13 Nov" and "19 nights" on one line when text wraps */
fun glued(text: String): String = text.replace(GLUE, "$1 $2")

/** Quieter text */
@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Float = 14f) {
    Text(text, modifier = modifier, style = font(size), color = LocalAppColors.current.mute)
}

/** A highlighted part's colour: the accent for good and bad alike (sleep debt, Steady), and warn for warnings */
fun toneColor(tone: Tone, c: AppColors): Color? = when (tone) {
    Tone.GOOD, Tone.BAD -> c.acc
    Tone.WARN -> c.warn
    Tone.NONE -> null
}

/** [r] with its bold and coloured parts; bold parts without a colour take [boldColor] if given */
fun richString(r: Rich, c: AppColors, boldColor: Color?): AnnotatedString = buildAnnotatedString {
    for (s in r.spans) {
        val color = toneColor(s.tone, c) ?: (if (s.bold) boldColor else null) ?: Color.Unspecified
        if (!s.bold && color == Color.Unspecified) append(s.text)
        else withStyle(SpanStyle(color = color, fontWeight = if (s.bold) FontWeight.Bold else null)) { append(s.text) }
    }
}

@Composable
fun RichText(r: Rich, style: TextStyle, color: Color, modifier: Modifier = Modifier, boldColor: Color? = null, align: TextAlign? = null) {
    Text(richString(r, LocalAppColors.current, boldColor), modifier = modifier, style = style, color = color, textAlign = align ?: TextAlign.Start)
}

/** Button styles: the main tonal one, outlined, text only, raised (quieter), and the two for deleting */
enum class Btn { TONAL, OUTLINE, TEXT, RAISED, DANGER, DANGER_FILL }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: Btn = Btn.TONAL,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    icon: Int? = null,
    size: Float = 16f,
) {
    val c = LocalAppColors.current
    val (bg, fg) = when (kind) {
        Btn.TONAL -> c.tonal to c.onTonal
        Btn.OUTLINE -> Color.Transparent to c.acc
        Btn.TEXT -> Color.Transparent to c.acc
        Btn.RAISED -> c.raised to c.ink
        Btn.DANGER -> Color.Transparent to c.bad
        Btn.DANGER_FILL -> c.bad to c.onBad
    }
    val small = kind == Btn.TEXT || kind == Btn.DANGER
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = if (small) 44.dp else height).alpha(if (enabled) 1f else 0.45f),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg, disabledContainerColor = bg, disabledContentColor = fg),
        border = if (kind == Btn.OUTLINE) BorderStroke(1.5.dp, c.line) else null,
        contentPadding = if (small) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else PaddingValues(horizontal = 22.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = font(size, FontWeight.Bold), textAlign = TextAlign.Center)
    }
}

/** A segmented control: Month / Week / 3 days, Automatic / Set a time */
@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, container: Color? = null) {
    val c = LocalAppColors.current
    Row(
        modifier.fillMaxWidth().background(container ?: c.panel, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Surface(
                onClick = { onSelect(i) }, shape = CircleShape, color = if (on) c.sel else Color.Transparent, contentColor = if (on) c.onSel else c.mute,
                modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { role = Role.Tab; this.selected = on },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                    Text(label, style = font(15f, if (on) FontWeight.Bold else FontWeight.Medium), color = if (on) c.onSel else c.mute, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Figures in a grid: the figure in Literata, its label under it */
@Composable
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier, columns: Int = 3, bigSize: Float = 22f, labelSize: Float = 13f) {
    val c = LocalAppColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        stats.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s ->
                    Column(Modifier.weight(1f)) {
                        Text(glued(s.big), style = serif(bigSize, FontWeight.Normal, bigSize * 1.15f), color = toneColor(s.tone, c) ?: c.ink)
                        Spacer(Modifier.height(2.dp))
                        Text(s.label, style = font(labelSize).copy(lineHeight = (labelSize * 1.3f).sp), color = c.mute)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A night not logged, as a chip that logs it */
@Composable
fun WarnChip(text: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Surface(onClick = onClick, shape = CircleShape, color = c.raised, contentColor = c.warn) {
        Text(text, style = font(14f, FontWeight.Bold), color = c.warn, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
    }
}

/** A tip in a raised box, with the moon when [moon] */
@Composable
fun Note(r: Rich, moon: Boolean = false) {
    val c = LocalAppColors.current
    Row(
        Modifier.padding(top = 16.dp).fillMaxWidth().background(c.raised, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (moon) {
            Icon(painterResource(R.drawable.ic_moon), contentDescription = null, tint = c.acc, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        RichText(r, font(15f), c.ink, boldColor = c.ink)
    }
}

/** One entry in a chart's key */
@Composable
fun LegendEntry(text: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(6.dp))
        Text(text, style = font(13f), color = LocalAppColors.current.mute)
    }
}

/** A chart's key, from the show layer's entries */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item -> LegendEntry(item.text) { Swatch(item.mark) } }
    }
}

@Composable
fun Swatch(mark: LegendItem.Mark) {
    val c = LocalAppColors.current
    val bar = RoundedCornerShape(topStart = 3.5.dp, topEnd = 3.5.dp)
    when (mark) {
        LegendItem.Mark.LINE, LegendItem.Mark.SURPLUS -> Box(Modifier.size(7.dp, 14.dp).background(c.met, bar))
        LegendItem.Mark.DEFICIT -> Box(Modifier.size(7.dp, 14.dp).background(c.under, bar))
        LegendItem.Mark.GOAL -> Canvas(Modifier.size(18.dp, 2.dp)) {
            drawLine(c.goal, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        }
        LegendItem.Mark.MISSING -> Box(Modifier.size(14.dp, 3.dp).background(c.mute, RoundedCornerShape(2.dp)))
        LegendItem.Mark.LIVE -> Box(Modifier.size(7.dp, 14.dp).border(1.5.dp, c.met, bar))
        LegendItem.Mark.SLEEP -> Box(Modifier.size(16.dp, 8.dp).background(c.met, RoundedCornerShape(4.dp)))
        LegendItem.Mark.NAP -> Box(Modifier.size(16.dp, 8.dp).background(c.under, RoundedCornerShape(4.dp)))
        LegendItem.Mark.PLAN -> Box(Modifier.size(16.dp, 12.dp).background(c.window, RoundedCornerShape(3.dp)).border(1.dp, c.line, RoundedCornerShape(3.dp)))
    }
}

/** A menu: the chosen option in a raised field, and the list when tapped. [small]: the compact one under a heading */
@Composable
fun <T> MenuField(options: List<Pair<T, String>>, selected: T?, placeholder: String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = LocalAppColors.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true }, shape = RoundedCornerShape(if (small) 14.dp else 16.dp), color = c.raised, contentColor = c.ink,
            modifier = if (small) Modifier else Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.heightIn(min = if (small) 44.dp else 52.dp).padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    options.firstOrNull { it.first == selected }?.second ?: placeholder,
                    style = font(if (small) 15f else 16f), color = c.ink,
                    modifier = if (small) Modifier else Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.raised) {
            options.forEach { (v, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = font(16f, if (v == selected) FontWeight.Bold else FontWeight.Normal), color = c.ink) },
                    onClick = { open = false; onSelect(v) },
                )
            }
        }
    }
}

/** A switch in the theme's colours */
@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val c = LocalAppColors.current
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        modifier = Modifier.semantics { contentDescription = label },
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.switchOnThumb, checkedTrackColor = c.switchOn, checkedBorderColor = c.switchOn,
            uncheckedThumbColor = c.switchOffThumb, uncheckedTrackColor = c.switchOff, uncheckedBorderColor = c.switchOffBorder,
        ),
    )
}

/** A row with a title, a line under it and a switch */
@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val c = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = font(16f, FontWeight.Bold), color = c.ink)
            Text(sub, style = font(14f), color = c.mute)
        }
        Spacer(Modifier.width(16.dp))
        AppSwitch(checked, onChange, label)
    }
}

/** The star shape in a 24 by 24 box */
private val STAR = floatArrayOf(12f, 3.6f, 14.5f, 8.8f, 20.2f, 9.6f, 16.1f, 13.6f, 17.1f, 19.3f, 12f, 16.6f, 6.9f, 19.3f, 7.9f, 13.6f, 3.8f, 9.6f, 9.5f, 8.8f)

fun starPath(px: Float): Path {
    val k = px / 24f
    val p = Path()
    for (i in STAR.indices step 2) {
        if (i == 0) p.moveTo(STAR[i] * k, STAR[i + 1] * k) else p.lineTo(STAR[i] * k, STAR[i + 1] * k)
    }
    p.close()
    return p
}

/** One star, filled or as an outline */
@Composable
fun Star(filled: Boolean, size: Dp, color: Color, outline: Color) {
    Canvas(Modifier.size(size)) {
        val p = starPath(this.size.width)
        if (filled) drawPath(p, color, style = Fill)
        drawPath(p, if (filled) color else outline, style = Stroke(width = 1.6.dp.toPx() * this.size.width / 24.dp.toPx()))
    }
}

/** Five stars, [filled] of them filled */
@Composable
fun Stars(filled: Int, label: String, size: Dp = 18.dp) {
    val c = LocalAppColors.current
    Row(Modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 1..5) Star(i <= filled, size, c.acc, c.mute)
    }
}

/** Five stars to tap: [value] (0 to 5) filled; tapping the chosen star again clears it when [clearable] */
@Composable
fun StarPicker(value: Int, onPick: (Int) -> Unit, clearable: Boolean = true, size: Dp = 30.dp) {
    val c = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            Surface(
                onClick = { onPick(if (clearable && i == value) 0 else i) },
                shape = CircleShape, color = Color.Transparent,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "$i of 5"; selected = i == value },
            ) {
                Box(contentAlignment = Alignment.Center) { Star(i <= value, size, c.acc, c.mute) }
            }
        }
    }
}
