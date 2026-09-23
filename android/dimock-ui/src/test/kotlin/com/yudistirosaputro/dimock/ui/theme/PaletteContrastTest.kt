package com.yudistirosaputro.dimock.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The palette is a contract, not a mood board: every ink the inspector puts on a surface has to stay readable
 * on a phone held at arm's length in daylight. This measures real WCAG 2.x contrast — sRGB linearisation,
 * relative luminance, `(L1 + 0.05) / (L2 + 0.05)` — instead of trusting the eye.
 *
 * AA for text is 4.5:1; WCAG 1.4.11 asks 3:1 of non-text boundaries (our `control` borders and inactive switch).
 * Inks are measured on all three surfaces — text sits in sunken wells (code blocks, fields) as often as on the
 * screen itself. `accent` is measured twice: as the fill under `onAccent`, and as ink through `accentText`.
 *
 * Pure JVM: `Color` is Kotlin value-class arithmetic, no Android framework call is made.
 */
class PaletteContrastTest {

    @Test
    fun `dark palette clears WCAG AA for every ink on every surface`() {
        assertPalette("dark", DimockPalettes.Dark)
    }

    @Test
    fun `light palette clears WCAG AA for every ink on every surface`() {
        assertPalette("light", DimockPalettes.Light)
    }

    // --- WCAG 2.x, verbatim -------------------------------------------------------------------------------

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    // --- the contract --------------------------------------------------------------------------------------

    /** One measurement the palette must satisfy. */
    private class Probe(val what: String, val fg: Color, val bg: Color, val min: Double)

    private fun probes(p: DimockPalette): List<Probe> {
        val inks = listOf(
            "text" to p.text,
            "textMuted" to p.textMuted,
            "textDim" to p.textDim,
            "accentText" to p.accentText,
            "status5xx" to p.status5xx,
            "status4xx" to p.status4xx,
            "status3xx" to p.status3xx,
        )
        val backgrounds = listOf("surface" to p.surface, "surfaceRaised" to p.surfaceRaised, "surfaceSunken" to p.surfaceSunken)
        return buildList {
            for ((inkName, ink) in inks) {
                for ((bgName, bg) in backgrounds) add(Probe("$inkName on $bgName", ink, bg, AA_TEXT))
            }
            add(Probe("onAccent on accent", p.onAccent, p.accent, AA_TEXT))
            add(Probe("snackbarText on snackbarSurface", p.snackbarText, p.snackbarSurface, AA_TEXT))
            add(Probe("control on surface", p.control, p.surface, AA_NON_TEXT))
            add(Probe("control on surfaceRaised", p.control, p.surfaceRaised, AA_NON_TEXT))
        }
    }

    private fun assertPalette(name: String, palette: DimockPalette) {
        val probes = probes(palette)
        val report = probes.joinToString("\n") { probe ->
            val r = ratio(probe.fg, probe.bg)
            String.format(Locale.US, "  %-28s %5.2f:1  needs %.1f:1  %s", probe.what, r, probe.min, if (r >= probe.min) "ok" else "FAIL")
        }
        val failures = probes.filter { ratio(it.fg, it.bg) < it.min }.map { it.what }
        assertWithMessage("$name palette contrast\n$report").that(failures).isEmpty()
    }

    private companion object {
        const val AA_TEXT = 4.5
        const val AA_NON_TEXT = 3.0
    }
}
