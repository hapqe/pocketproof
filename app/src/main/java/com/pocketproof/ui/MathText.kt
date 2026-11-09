package com.pocketproof.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketproof.engine.Loc
import com.pocketproof.engine.Path
import com.pocketproof.engine.Rendered
import com.pocketproof.engine.Style
import com.pocketproof.engine.TermSpan

fun Rendered.annotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    for (s in styles) {
        val style = when (s.style) {
            Style.Var -> SpanStyle(fontFamily = MathItalic, fontStyle = FontStyle.Italic)
            Style.Sub -> SubStyle
            Style.Sup -> SupStyle
            Style.Keyword -> SpanStyle()
        }
        addStyle(style, s.start, s.end)
    }
}

/** A piece of rendered math on screen that tools can be dropped onto. */
class MathZone(val loc: Loc, var rendered: Rendered) {
    var coords: LayoutCoordinates? = null
    var layout: TextLayoutResult? = null

    /** Where a subterm is, in root coordinates. */
    fun bounds(span: TermSpan): Rect? {
        val c = coords?.takeIf { it.isAttached } ?: return null
        val l = layout ?: return null
        if (span.end > l.layoutInput.text.length) return null
        val box = l.getPathForRange(span.start, span.end).getBounds()
        return box.translate(c.positionInRoot())
    }
}

@Composable
fun MathText(
    rendered: Rendered,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    highlights: Set<Path> = emptySet(),
    active: Path? = null,
    hinted: Path? = null,
    zone: MathZone? = null,
    onTapTerm: ((Path) -> Unit)? = null,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val highlightColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
    val activeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.72f)
    val hintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    val paint = remember {
        Paint().apply {
            style = PaintingStyle.Fill
            pathEffect = PathEffect.cornerPathEffect(16f)
        }
    }
    zone?.rendered = rendered

    val tapModifier = if (onTapTerm != null && highlights.isNotEmpty()) {
        Modifier.pointerInput(highlights, rendered) {
            detectTapGestures { pos ->
                val l = layout ?: return@detectTapGestures
                // Nearest highlighted subterm wins; among those under the finger, the smallest.
                val reach = 40.dp.toPx()
                rendered.spans
                    .filter { it.path in highlights && it.end <= l.layoutInput.text.length }
                    .map { span ->
                        val r = l.getPathForRange(span.start, span.end).getBounds()
                        val dx = maxOf(r.left - pos.x, 0f, pos.x - r.right)
                        val dy = maxOf(r.top - pos.y, 0f, pos.y - r.bottom)
                        Triple(span, kotlin.math.hypot(dx, dy), r.width * r.height)
                    }
                    .filter { it.second <= reach }
                    .minWithOrNull(compareBy({ it.second }, { it.third }))
                    ?.let { onTapTerm(it.first.path) }
            }
        }
    } else Modifier

    BasicText(
        text = rendered.annotated(),
        style = TextStyle(fontFamily = MathFamily, fontSize = fontSize, color = color, lineHeight = fontSize * 1.6f),
        onTextLayout = {
            layout = it
            zone?.layout = it
        },
        modifier = modifier
            .then(tapModifier)
            .onGloballyPositioned { zone?.coords = it }
            .drawBehind {
                val l = layout ?: return@drawBehind
                fun paintSpan(path: Path, c: Color) {
                    val span = rendered.spanFor(path) ?: return
                    if (span.end > l.layoutInput.text.length) return
                    paint.color = c
                    drawIntoCanvas { it.drawPath(l.getPathForRange(span.start, span.end), paint) }
                }
                highlights.forEach { paintSpan(it, highlightColor) }
                hinted?.let { paintSpan(it, hintColor) }
                active?.let { paintSpan(it, activeColor) }
            },
    )
}
