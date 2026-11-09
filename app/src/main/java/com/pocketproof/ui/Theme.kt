package com.pocketproof.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import com.pocketproof.R

// Latin Modern is Computer Modern with full math and German coverage; the italic is cmmi, the TeX math italic.
val MathFamily = FontFamily(Font(R.font.latin_modern_math))
val MathItalic = FontFamily(Font(R.font.cm_math_italic, style = FontStyle.Italic))

@Composable
fun PocketProofTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        // Without this, text placed outside a Surface defaults to black, which disappears in dark mode.
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

data class WorldColors(val strong: Color, val onStrong: Color, val soft: Color, val onSoft: Color)

private val LIGHT = listOf(
    WorldColors(Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D)),
    WorldColors(Color(0xFF00696B), Color.White, Color(0xFF9CF1F2), Color(0xFF002020)),
    WorldColors(Color(0xFF8B5000), Color.White, Color(0xFFFFDCBE), Color(0xFF2C1600)),
    WorldColors(Color(0xFF3B6939), Color.White, Color(0xFFBCF0B4), Color(0xFF002204)),
    WorldColors(Color(0xFF0061A4), Color.White, Color(0xFFD1E4FF), Color(0xFF001D36)),
    WorldColors(Color(0xFF984061), Color.White, Color(0xFFFFD9E2), Color(0xFF3E001D)),
    WorldColors(Color(0xFF6C5E00), Color.White, Color(0xFFF7E388), Color(0xFF211B00)),
    WorldColors(Color(0xFFB3261E), Color.White, Color(0xFFF9DEDC), Color(0xFF410E0B)),
)

private val DARK = listOf(
    WorldColors(Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF)),
    WorldColors(Color(0xFF80D5D6), Color(0xFF003738), Color(0xFF004F51), Color(0xFF9CF1F2)),
    WorldColors(Color(0xFFFFB870), Color(0xFF4A2800), Color(0xFF693C00), Color(0xFFFFDCBE)),
    WorldColors(Color(0xFFA1D39A), Color(0xFF0A390F), Color(0xFF235024), Color(0xFFBCF0B4)),
    WorldColors(Color(0xFF9ECAFF), Color(0xFF003258), Color(0xFF00497D), Color(0xFFD1E4FF)),
    WorldColors(Color(0xFFFFB1C8), Color(0xFF5E1133), Color(0xFF7B2949), Color(0xFFFFD9E2)),
    WorldColors(Color(0xFFDAC76F), Color(0xFF393000), Color(0xFF524600), Color(0xFFF7E388)),
    WorldColors(Color(0xFFF2B8B5), Color(0xFF601410), Color(0xFF8C1D18), Color(0xFFF9DEDC)),
)

@Composable
fun worldColors(index: Int): WorldColors = (if (isSystemInDarkTheme()) DARK else LIGHT)[index % LIGHT.size]
