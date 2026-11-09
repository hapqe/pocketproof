package com.pocketproof.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.pocketproof.engine.Wording

enum class Lang(val label: String) { En("English"), De("Deutsch") }

object Language {
    var current by mutableStateOf(Lang.En)
        private set

    fun set(lang: Lang) {
        Wording.german = lang == Lang.De
        current = lang
    }
}

/** Picks the text for the current language; composables reading it update when the language changes. */
fun tr(en: String, de: String) = if (Language.current == Lang.De) de else en

val SubStyle = SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift(-0.22f))
val SupStyle = SpanStyle(fontSize = 0.7.em, baselineShift = BaselineShift(0.42f))

// Computer Modern has no glyphs for these, so they are set as real shifted text.
private val SUBSCRIPTS = "₀₁₂₃₄₅₆₇₈₉".mapIndexed { i, c -> c to i.toString() }.toMap() + mapOf('ᵢ' to "i", 'ⱼ' to "j", 'ₙ' to "n")
private val SUPERSCRIPTS = mapOf('⁻' to "−", '¹' to "1", 'ⁿ' to "n", 'ˣ' to "×", 'ᵀ' to "T")

private const val COMPUTE = "{compute}"
private const val FLIP = "{flip}"

private fun proseText(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith(COMPUTE, i) -> {
                appendInlineContent(COMPUTE, tr("Compute", "Rechnen"))
                i += COMPUTE.length
            }
            text.startsWith(FLIP, i) -> {
                appendInlineContent(FLIP, tr("Flip", "Umdrehen"))
                i += FLIP.length
            }
            else -> {
                val c = text[i++]
                val sub = SUBSCRIPTS[c]
                val sup = SUPERSCRIPTS[c]
                when {
                    sub != null -> withStyle(SubStyle) { append(sub) }
                    sup != null -> withStyle(SupStyle) { append(sup) }
                    else -> append(c)
                }
            }
        }
    }
}

/** Text that can contain math: set in Computer Modern, with {compute} and {flip} drawn as icons. */
@Composable
fun Prose(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val icons = remember(tint) {
        fun icon(vector: androidx.compose.ui.graphics.vector.ImageVector) = InlineTextContent(
            Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter),
        ) { Icon(vector, null, Modifier.fillMaxSize(), tint = tint) }
        mapOf(COMPUTE to icon(Icons.Rounded.Bolt), FLIP to icon(Icons.Rounded.SwapHoriz))
    }
    Text(
        text = proseText(text),
        modifier = modifier,
        color = tint,
        fontFamily = MathFamily,
        // Computer Modern is small and light next to the interface font at the same size.
        fontSize = style.fontSize * 1.1f,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = icons,
        style = style,
    )
}
