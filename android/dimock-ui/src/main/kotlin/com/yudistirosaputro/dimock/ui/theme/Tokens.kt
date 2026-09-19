package com.yudistirosaputro.dimock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The inspector palette. Source of truth: docs/design/tokens.md.
 *
 * One shape, two instances: the inspector follows night mode so it never glares in a lit room, but it keeps
 * its own hues either way and never adopts the host app's colours — you always know whose screen you are on.
 * Every ink here clears WCAG AA (4.5:1) on both surfaces and [control] clears 1.4.11 (3:1); `PaletteContrastTest`
 * measures it, so changing a value without re-running that test is how the panel stops being readable.
 */
@Immutable
data class DimockPalette(
    val surface: Color,
    val surfaceRaised: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val control: Color,
    val text: Color,
    val textMuted: Color,
    val textDim: Color,
    /**
     * One meaning only: mocked. A **fill** colour, never ink and never a thin mark — on the light surface it
     * measures 1.18:1, so text or a hairline in it would simply disappear. Ink goes in [accentText].
     */
    val accent: Color,
    /** The accent as ink on a surface: the accent itself in dark, a dark olive-lime in light. */
    val accentText: Color,
    /** Ink on an [accent] fill. */
    val onAccent: Color,
    val status5xx: Color,
    val status4xx: Color,
    val status3xx: Color,
    val snackbarSurface: Color,
    val snackbarText: Color,
) {
    /** Semantic only: 5xx red, 4xx amber, 3xx blue, 2xx neutral. A missing code reads as severe as a 5xx. */
    fun forStatus(code: Int?): Color = forTone(StatusTone.of(code))

    /** Methods are told apart by weight of colour, not by hue: reads are quiet, writes brighter, DELETE warm. */
    fun forMethod(method: String): Color = forTone(MethodTone.of(method))

    fun forTone(tone: StatusTone): Color = when (tone) {
        StatusTone.SERVER_ERROR -> status5xx
        StatusTone.CLIENT_ERROR -> status4xx
        StatusTone.REDIRECT -> status3xx
        StatusTone.NEUTRAL -> textMuted
    }

    fun forTone(tone: MethodTone): Color = when (tone) {
        MethodTone.READ -> textMuted
        MethodTone.WRITE -> text
        MethodTone.DESTRUCTIVE -> status4xx
        MethodTone.OTHER -> textDim
    }

    fun forTone(tone: SecondaryTone): Color = when (tone) {
        SecondaryTone.RULE -> textMuted
        SecondaryTone.FAILURE -> status5xx
        SecondaryTone.HOST -> textDim
    }

    fun forTone(tone: EffectTone): Color = when (tone) {
        EffectTone.FAILURE -> status5xx
        EffectTone.WARNING -> status4xx
        EffectTone.PLAIN -> text
    }
}

/**
 * Display models are built off the composition — on a background thread, before anyone knows which palette is
 * live — so they carry meaning, not colour. The composable resolves a tone through [DimockPalette.forTone] at
 * draw time, which is what lets the inspector follow night mode without every row going stale.
 */

/** How severe a status code is: 5xx red, 4xx amber, 3xx blue, 2xx neutral; no code at all is as severe as a 5xx. */
enum class StatusTone {
    SERVER_ERROR, CLIENT_ERROR, REDIRECT, NEUTRAL;

    companion object {
        fun of(code: Int?): StatusTone = when {
            code == null -> SERVER_ERROR
            code >= 500 -> SERVER_ERROR
            code >= 400 -> CLIENT_ERROR
            code >= 300 -> REDIRECT
            else -> NEUTRAL
        }
    }
}

/** How loud a method should read: GET is quiet, POST/PUT/PATCH brighter, DELETE warm, anything else dim. */
enum class MethodTone {
    READ, WRITE, DESTRUCTIVE, OTHER;

    companion object {
        fun of(method: String): MethodTone = when (method.uppercase()) {
            "GET" -> READ
            "POST", "PUT", "PATCH" -> WRITE
            "DELETE" -> DESTRUCTIVE
            else -> OTHER
        }
    }
}

/** What the second line of a Traffic row is saying: the rule that served it, why it failed, or just the host. */
enum class SecondaryTone { RULE, FAILURE, HOST }

/** What a rule will do to the app: break the call, hand back a 4xx, or answer plainly. */
enum class EffectTone { FAILURE, WARNING, PLAIN }

object DimockPalettes {

    val Dark = DimockPalette(
        surface = Color(0xFF0E1012),
        surfaceRaised = Color(0xFF15181B),
        hairline = Color(0xFF262B30),
        hairlineSoft = Color(0xFF1C2024),
        control = Color(0xFF5C6670),
        text = Color(0xFFE8ECEF),
        textMuted = Color(0xFF8B949E),
        textDim = Color(0xFF7A858F),
        accent = Color(0xFFC6F135),
        accentText = Color(0xFFC6F135),
        onAccent = Color(0xFF0E1012),
        status5xx = Color(0xFFF06A5B),
        status4xx = Color(0xFFF2B84B),
        status3xx = Color(0xFF7FB2F0),
        snackbarSurface = Color(0xFFE8ECEF),
        snackbarText = Color(0xFF0E1012),
    )

    val Light = DimockPalette(
        surface = Color(0xFFF1F3F5),
        surfaceRaised = Color(0xFFFBFCFD),
        hairline = Color(0xFFD3D9DE),
        hairlineSoft = Color(0xFFE4E8EB),
        control = Color(0xFF7E8894),
        text = Color(0xFF101417),
        textMuted = Color(0xFF4E5964),
        textDim = Color(0xFF646F79),
        accent = Color(0xFFC6F135),
        accentText = Color(0xFF4C6600),
        onAccent = Color(0xFF0E1012),
        status5xx = Color(0xFFB3251B),
        status4xx = Color(0xFF7A5200),
        status3xx = Color(0xFF1B5FAF),
        snackbarSurface = Color(0xFF15181B),
        snackbarText = Color(0xFFF1F3F5),
    )
}

val LocalDimockPalette = staticCompositionLocalOf { DimockPalettes.Dark }

object DimockDimens {
    const val RADIUS_DP = 4
    const val RADIUS_SHEET_DP = 8
    const val ROW_HEIGHT_DP = 68
    const val GUTTER_DP = 20
    const val TAP_TARGET_DP = 44
    const val MARKER_WIDTH_DP = 3
    /** A lime fill has no edge of its own against a near-white surface; in light mode it gets one. */
    const val ACCENT_OUTLINE_DP = 1
}
