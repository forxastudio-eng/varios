package com.forxa.pianocascada.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forxa.pianocascada.ui.theme.PC

enum class G { BACK, PLAY, PAUSE, RESTART, LOOP, GEAR, CLOSE, CHECK, PLUS, MINUS, STAR, HEART, PEDAL, SEARCH, KEYS, NOTE, WAVE, HAND, FILE, MIC }

/** Iconos dibujados con trazos finos (no hace falta ninguna librería de iconos). */
@Composable
fun Glyph(g: G, modifier: Modifier = Modifier.size(24.dp), tint: Color = PC.text1, filled: Boolean = false) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.085f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), strokeWidth = sw, cap = StrokeCap.Round)
        when (g) {
            G.BACK -> { line(0.62f, 0.2f, 0.3f, 0.5f); line(0.3f, 0.5f, 0.62f, 0.8f) }
            G.PLAY -> drawPath(Path().apply { moveTo(0.3f * w, 0.18f * h); lineTo(0.8f * w, 0.5f * h); lineTo(0.3f * w, 0.82f * h); close() }, tint)
            G.PAUSE -> {
                drawRoundRect(tint, Offset(0.25f * w, 0.2f * h), Size(0.18f * w, 0.6f * h), androidx.compose.ui.geometry.CornerRadius(0.04f * w))
                drawRoundRect(tint, Offset(0.57f * w, 0.2f * h), Size(0.18f * w, 0.6f * h), androidx.compose.ui.geometry.CornerRadius(0.04f * w))
            }
            G.RESTART -> {
                drawArc(tint, -50f, 290f, false, Offset(0.18f * w, 0.18f * h), Size(0.64f * w, 0.64f * h), style = st)
                drawPath(Path().apply { moveTo(0.62f * w, 0.1f * h); lineTo(0.8f * w, 0.28f * h); lineTo(0.56f * w, 0.34f * h); close() }, tint)
            }
            G.LOOP -> {
                drawRoundRect(tint, Offset(0.15f * w, 0.28f * h), Size(0.7f * w, 0.44f * h), androidx.compose.ui.geometry.CornerRadius(0.2f * w), style = st)
                drawPath(Path().apply { moveTo(0.52f * w, 0.14f * h); lineTo(0.66f * w, 0.28f * h); lineTo(0.52f * w, 0.42f * h); close() }, tint)
            }
            G.GEAR -> {
                drawCircle(tint, 0.2f * w, Offset(0.5f * w, 0.5f * h), style = st)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val c = Math.cos(a).toFloat(); val s = Math.sin(a).toFloat()
                    line(0.5f + 0.3f * c, 0.5f + 0.3f * s, 0.5f + 0.4f * c, 0.5f + 0.4f * s)
                }
            }
            G.CLOSE -> { line(0.25f, 0.25f, 0.75f, 0.75f); line(0.75f, 0.25f, 0.25f, 0.75f) }
            G.CHECK -> { line(0.22f, 0.52f, 0.42f, 0.72f); line(0.42f, 0.72f, 0.8f, 0.28f) }
            G.PLUS -> { line(0.2f, 0.5f, 0.8f, 0.5f); line(0.5f, 0.2f, 0.5f, 0.8f) }
            G.MINUS -> line(0.2f, 0.5f, 0.8f, 0.5f)
            G.STAR -> {
                val p = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 0.42f else 0.18f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = (0.5f + r * Math.cos(a).toFloat()) * w; val y = (0.52f + r * Math.sin(a).toFloat()) * h
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                if (filled) drawPath(p, tint) else drawPath(p, tint, style = st)
            }
            G.HEART -> {
                val p = Path().apply {
                    moveTo(0.5f * w, 0.82f * h)
                    cubicTo(0.05f * w, 0.5f * h, 0.2f * w, 0.12f * h, 0.5f * w, 0.36f * h)
                    cubicTo(0.8f * w, 0.12f * h, 0.95f * w, 0.5f * h, 0.5f * w, 0.82f * h)
                    close()
                }
                if (filled) drawPath(p, tint) else drawPath(p, tint, style = st)
            }
            G.PEDAL -> {
                drawRoundRect(tint, Offset(0.2f * w, 0.35f * h), Size(0.6f * w, 0.32f * h), androidx.compose.ui.geometry.CornerRadius(0.08f * w), style = st)
                line(0.35f, 0.8f, 0.65f, 0.8f)
            }
            G.SEARCH -> { drawCircle(tint, 0.24f * w, Offset(0.45f * w, 0.45f * h), style = st); line(0.63f, 0.63f, 0.82f, 0.82f) }
            G.KEYS -> {
                drawRoundRect(tint, Offset(0.12f * w, 0.25f * h), Size(0.76f * w, 0.5f * h), androidx.compose.ui.geometry.CornerRadius(0.06f * w), style = st)
                line(0.37f, 0.5f, 0.37f, 0.75f); line(0.63f, 0.5f, 0.63f, 0.75f)
                drawRect(tint, Offset(0.31f * w, 0.25f * h), Size(0.12f * w, 0.28f * h))
                drawRect(tint, Offset(0.57f * w, 0.25f * h), Size(0.12f * w, 0.28f * h))
            }
            G.NOTE -> {
                drawCircle(tint, 0.14f * w, Offset(0.36f * w, 0.7f * h))
                line(0.5f, 0.7f, 0.5f, 0.2f); line(0.5f, 0.2f, 0.74f, 0.32f)
            }
            G.WAVE -> {
                val p = Path().apply {
                    moveTo(0.1f * w, 0.5f * h)
                    cubicTo(0.25f * w, 0.1f * h, 0.3f * w, 0.1f * h, 0.4f * w, 0.5f * h)
                    cubicTo(0.5f * w, 0.9f * h, 0.55f * w, 0.9f * h, 0.65f * w, 0.5f * h)
                    cubicTo(0.72f * w, 0.25f * h, 0.8f * w, 0.25f * h, 0.9f * w, 0.5f * h)
                }
                drawPath(p, tint, style = st)
            }
            G.HAND -> {
                for (i in 0 until 4) line(0.3f + i * 0.14f, 0.5f, 0.3f + i * 0.14f, 0.18f + (if (i == 1 || i == 2) 0f else 0.06f))
                line(0.3f, 0.5f, 0.2f, 0.4f)
                drawRoundRect(tint, Offset(0.28f * w, 0.46f * h), Size(0.46f * w, 0.36f * h), androidx.compose.ui.geometry.CornerRadius(0.12f * w), style = st)
            }
            G.FILE -> {
                val p = Path().apply { moveTo(0.28f * w, 0.14f * h); lineTo(0.58f * w, 0.14f * h); lineTo(0.76f * w, 0.32f * h); lineTo(0.76f * w, 0.86f * h); lineTo(0.28f * w, 0.86f * h); close() }
                drawPath(p, tint, style = st)
            }
            G.MIC -> {
                drawRoundRect(tint, Offset(0.38f * w, 0.14f * h), Size(0.24f * w, 0.42f * h), androidx.compose.ui.geometry.CornerRadius(0.12f * w), style = st)
                drawArc(tint, 0f, 180f, false, Offset(0.26f * w, 0.3f * h), Size(0.48f * w, 0.38f * h), style = st)
                line(0.5f, 0.68f, 0.5f, 0.84f)
            }
        }
    }
}

@Composable
fun IconBtn(g: G, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp, active: Boolean = false, tint: Color = PC.text1, enabled: Boolean = true) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) PC.surface3 else PC.surface2)
            .then(if (active) Modifier.border(1.dp, PC.text3, CircleShape) else Modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(g, Modifier.size(size * 0.5f), tint) }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, big: Boolean = false) {
    Box(
        modifier
            .height(if (big) 64.dp else 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PC.text1)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (big) 40.dp else 26.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = PC.bg, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PC.surface2)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = PC.text1, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color = PC.right) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PC.surface3 else PC.surface2)
            .border(BorderStroke(1.dp, if (selected) accent else Color.Transparent), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) PC.text1 else PC.text2, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(PC.surface2).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((v, label) in options) {
            val sel = v == selected
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) PC.surface3 else Color.Transparent)
                    .clickable { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (sel) PC.text1 else PC.text2, style = MaterialTheme.typography.labelLarge, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium) }
        }
    }
}

@Composable
fun StarRow(stars: Int, size: Dp = 16.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..3) Glyph(G.STAR, Modifier.size(size), if (i <= stars) PC.left else PC.text3, filled = i <= stars)
    }
}

@Composable
fun DifficultyDots(level: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..4) Box(Modifier.size(7.dp).clip(CircleShape).background(if (i <= level) PC.right else PC.surface3))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = PC.text3, style = MaterialTheme.typography.labelMedium, modifier = modifier.padding(bottom = 10.dp))
}

@Composable
fun LabeledSlider(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit,
    format: (Float) -> String = { "%.0f".format(it) }, modifier: Modifier = Modifier, hint: String? = null,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = PC.text1, style = MaterialTheme.typography.bodyMedium)
            Text(format(value), color = PC.text2, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = PC.text1, activeTrackColor = PC.right, inactiveTrackColor = PC.surface3),
        )
        if (hint != null) Text(hint, color = PC.text3, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = PC.text1, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) Text(hint, color = PC.text3, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = PC.bg, checkedTrackColor = PC.text1, uncheckedThumbColor = PC.text2, uncheckedTrackColor = PC.surface3, uncheckedBorderColor = PC.surface3),
        )
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PC.surface1)
            .border(1.dp, PC.line, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
fun Gap(h: Dp = 0.dp, w: Dp = 0.dp) = Spacer(Modifier.height(h).width(w))
