package com.resz073.sleeptracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.resz073.sleeptracker.show.Drawing
import com.resz073.sleeptracker.show.Shape

/**
 * Draws one of version 1's charts (a [Drawing]: SVG-like shapes in its own coordinates), scaled to the
 * available width. [onTap] gets the x of a tap in the chart's coordinates.
 */
@Composable
fun DrawingView(d: Drawing, modifier: Modifier = Modifier, onTap: ((Float) -> Unit)? = null) {
    val colors = LocalAppColors.current
    val measurer = rememberTextMeasurer()
    val paths = remember(d) { d.shapes.map { if (it.tag == "path") pathOf(it["d"] ?: "") else null } }
    val clips = remember(d) { d.clips.mapValues { pathOf(it.value) } }
    var m = modifier.fillMaxWidth().aspectRatio(d.width / d.height)
    d.label?.let { label -> m = m.semantics { contentDescription = label } }
    if (onTap != null) m = m.pointerInput(d) { detectTapGestures(onTap = { o -> onTap(o.x / size.width * d.width) }) }
    Canvas(m) {
        val k = size.width / d.width
        scale(k, k, pivot = Offset.Zero) {
            d.shapes.forEachIndexed { i, s -> shape(s, paths[i], clips, colors, measurer) }
        }
    }
}

/** A colour role from version 1's stylesheet ("var(--acc)") */
fun role(colors: AppColors, v: String?): Color? {
    if (v == null || v == "none") return null
    return when (v.removePrefix("var(--").removeSuffix(")")) {
        "acc" -> colors.acc
        "acc-soft" -> colors.accSoft
        "good" -> colors.good
        "bad" -> colors.bad
        "warn" -> colors.warn
        "good-soft" -> colors.goodSoft
        "bad-soft" -> colors.badSoft
        "warn-soft" -> colors.warnSoft
        "ink" -> colors.ink
        "mute" -> colors.mute
        "line" -> colors.line
        "grid" -> colors.grid
        "card" -> colors.card
        "card2" -> colors.card2
        "sky" -> colors.sky
        "sky-ink" -> colors.skyInk
        "sky-mute" -> colors.skyMute
        "sky-acc" -> colors.skyAcc
        "sky-line" -> colors.skyLine
        "sky-dawn" -> colors.skyDawn
        else -> colors.ink
    }
}

private fun dash(v: String?): PathEffect? {
    val parts = v?.split(' ', ',')?.mapNotNull { it.toFloatOrNull() } ?: return null
    if (parts.size < 2) return null
    return PathEffect.dashPathEffect(parts.toFloatArray(), 0f)
}

private fun cap(v: String?) = if (v == "round") StrokeCap.Round else StrokeCap.Butt

private fun DrawScope.shape(s: Shape, path: Path?, clips: Map<String, Path>, colors: AppColors, measurer: TextMeasurer) {
    val fill = role(colors, s["fill"] ?: if (s.tag == "text" || s.tag == "rect" || s.tag == "path" || s.tag == "circle") "var(--ink)" else null)
    val fillAlpha = (s["fill-opacity"]?.toFloatOrNull() ?: 1f) * (s["opacity"]?.toFloatOrNull() ?: 1f)
    val stroke = role(colors, s["stroke"])
    val strokeAlpha = (s["stroke-opacity"]?.toFloatOrNull() ?: 1f) * (s["opacity"]?.toFloatOrNull() ?: 1f)
    val strokeWidth = s.num("stroke-width", 1f)
    when (s.tag) {
        "rect" -> {
            val topLeft = Offset(s.num("x"), s.num("y"))
            val sz = Size(s.num("width"), s.num("height"))
            val r = CornerRadius(s.num("rx"), s.num("rx"))
            if (fill != null) drawRoundRect(fill, topLeft, sz, r, alpha = fillAlpha, style = Fill)
            if (stroke != null) drawRoundRect(stroke, topLeft, sz, r, alpha = strokeAlpha, style = Stroke(strokeWidth, pathEffect = dash(s["stroke-dasharray"])))
        }
        "line" -> if (stroke != null) {
            drawLine(stroke, Offset(s.num("x1"), s.num("y1")), Offset(s.num("x2"), s.num("y2")), strokeWidth, cap(s["stroke-linecap"]), dash(s["stroke-dasharray"]), strokeAlpha)
        }
        "path" -> if (path != null) {
            val clip = s["clip-path"]?.removePrefix("url(#")?.removeSuffix(")")?.let { clips[it] }
            val drawIt: DrawScope.() -> Unit = {
                if (fill != null) drawPath(path, fill, alpha = fillAlpha, style = Fill)
                if (stroke != null) drawPath(path, stroke, alpha = strokeAlpha,
                    style = Stroke(strokeWidth, cap = cap(s["stroke-linecap"]), join = if (s["stroke-linejoin"] == "round") StrokeJoin.Round else StrokeJoin.Miter, pathEffect = dash(s["stroke-dasharray"])))
            }
            if (clip != null) clipPath(clip) { drawIt() } else drawIt()
        }
        "circle" -> {
            val c = Offset(s.num("cx"), s.num("cy"))
            val r = s.num("r")
            val dashes = s["stroke-dasharray"]?.split(' ', ',')?.mapNotNull { it.toFloatOrNull() }
            if (fill != null) drawCircle(fill, r, c, alpha = fillAlpha, style = Fill)
            if (stroke != null) {
                if (dashes != null && dashes.size == 2 && dashes[1] > 0f) {
                    // A progress ring: the dash is the part drawn, starting at the top (rotate(-90))
                    val sweep = 360f * Math.min(1f, dashes[0] / dashes[1])
                    drawArc(stroke, -90f, sweep, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), alpha = strokeAlpha, style = Stroke(strokeWidth, cap = cap(s["stroke-linecap"])))
                } else drawCircle(stroke, r, c, alpha = strokeAlpha, style = Stroke(strokeWidth))
            }
        }
        "text" -> {
            val text = s.text ?: return
            val style = TextStyle(
                fontFamily = Nunito, fontSize = s.num("font-size", 10f).toSp(),
                fontWeight = when (s["font-weight"]) { "800" -> FontWeight.ExtraBold; "700" -> FontWeight.Bold; else -> FontWeight.Normal }, color = fill ?: colors.ink
            )
            val layout = measurer.measure(text, style)
            val w = layout.size.width.toFloat()
            val x = s.num("x") - when (s["text-anchor"]) { "middle" -> w / 2; "end" -> w; else -> 0f }
            drawText(layout, topLeft = Offset(x, s.num("y") - layout.firstBaseline), alpha = fillAlpha)
        }
    }
}

/** Reads the path commands the charts use: M, L, H, V, C and Z, absolute */
fun pathOf(d: String): Path {
    val p = Path()
    val tokens = Regex("""[MLHVCZmlhvcz]|-?\d*\.?\d+(?:e[-+]?\d+)?""").findAll(d).map { it.value }.toList()
    var i = 0
    var cmd = 'M'
    var x = 0f
    var y = 0f
    fun next(): Float = tokens[i++].toFloat()
    while (i < tokens.size) {
        val tk = tokens[i]
        if (tk[0].isLetter()) { cmd = tk[0].uppercaseChar(); i++; if (cmd == 'Z') { p.close(); continue } }
        when (cmd) {
            'M' -> { x = next(); y = next(); p.moveTo(x, y); cmd = 'L' }
            'L' -> { x = next(); y = next(); p.lineTo(x, y) }
            'H' -> { x = next(); p.lineTo(x, y) }
            'V' -> { y = next(); p.lineTo(x, y) }
            'C' -> { val x1 = next(); val y1 = next(); val x2 = next(); val y2 = next(); x = next(); y = next(); p.cubicTo(x1, y1, x2, y2, x, y) }
            else -> i++
        }
    }
    return p
}
