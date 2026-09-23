package com.yudistirosaputro.dimock.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The inspector palette — "Slate & Sand". Source of truth: docs/design/tokens.md.
 *
 * One shape, two instances: the inspector follows night mode so it never glares in a lit room, but it keeps
 * its own hues either way and never adopts the host app's colours — you always know whose screen you are on.
 * Cool slate neutrals carry the structure; one warm accent, sand, means exactly one thing: mocked. Errors are
 * a red family only (5xx full, 4xx softened), so the accent never competes with a failure colour on a row.
 *
 * Every ink here clears WCAG AA (4.5:1) on all three surfaces and [control] clears 1.4.11 (3:1);
 * `PaletteContrastTest` measures it, so changing a value without re-running that test is how the panel stops
 * being readable.
 */
@Immutable
data class DimockPalette(
    /** Screen background. */
    val surface: Color,
    /** Grouped lists, cards, sheets, the floating bottom bar. */
    val surfaceRaised: Color,
    /** Wells: text fields, code blocks, the segmented-control track. Sits *below* the surface. */
    val surfaceSunken: Color,
    /** Primary dividers and the border of wells. */
    val hairline: Color,
    /** Row dividers inside a group. */
    val hairlineSoft: Color,
    /** Outlined button borders, idle chip borders, the inactive switch. Meets 3:1 on both surfaces. */
    val control: Color,
    val text: Color,
    val textMuted: Color,
    val textDim: Color,
    /**
     * One meaning only: mocked. Sand — a fill for the MOCK badge, the selected chip, the active switch and the
     * primary button, and ink for accent text actions. It clears AA as ink on every surface in both modes, so
     * [accentText] is the same value; it stays a separate field so a future palette can split them again.
     */
    val accent: Color,
    /** The accent as ink on a surface. Identical to [accent] in this palette. */
    val accentText: Color,
    /** Ink on an [accent] fill: near-black on sand in dark, white on bronze in light. */
    val onAccent: Color,
    /** The accent at ~14 % over the raised surface, pre-blended: mock banner fill, focused-editor ring, highlighted row. */
    val accentSoft: Color,
    val status5xx: Color,
    /** 4xx: the same red family, softened. Not amber — the panel has one warm hue, and it is the accent. */
    val status4xx: Color,
    /** 3xx only, a muted slate blue. Rare on a row. */
    val status3xx: Color,
    val snackbarSurface: Color,
    val snackbarText: Color,
) {
    /** Semantic only: 5xx red, 4xx softened red, 3xx slate blue, 2xx neutral. A missing code reads as severe as a 5xx. */
    fun forStatus(code: Int?): Color = forTone(StatusTone.of(code))

    /** Methods are told apart by weight of colour, not by hue: reads are quiet, writes brighter, DELETE the full text. */
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
        MethodTone.DESTRUCTIVE -> text
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

/** How severe a status code is: 5xx red, 4xx softened red, 3xx slate blue, 2xx neutral; no code at all is as severe as a 5xx. */
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

/** How loud a method should read: GET is quiet, POST/PUT/PATCH and DELETE full text, anything else dim. */
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
        surface = Color(0xFF15181D),
        surfaceRaised = Color(0xFF1C2027),
        surfaceSunken = Color(0xFF10131A),
        hairline = Color(0xFF2A3038),
        hairlineSoft = Color(0xFF252A32),
        control = Color(0xFF646F7C),
        text = Color(0xFFE6E9ED),
        textMuted = Color(0xFF98A1AC),
        textDim = Color(0xFF7F8A96),
        accent = Color(0xFFD8C3A0),
        accentText = Color(0xFFD8C3A0),
        onAccent = Color(0xFF1A1712),
        accentSoft = Color(0xFF363738),
        status5xx = Color(0xFFEF6B62),
        status4xx = Color(0xFFEC8F87),
        status3xx = Color(0xFF8FA8C8),
        snackbarSurface = Color(0xFFE6E9ED),
        snackbarText = Color(0xFF15181D),
    )

    val Light = DimockPalette(
        surface = Color(0xFFF2F3F5),
        surfaceRaised = Color(0xFFFFFFFF),
        surfaceSunken = Color(0xFFE9EBEF),
        hairline = Color(0xFFE0E3E8),
        hairlineSoft = Color(0xFFEAECEF),
        control = Color(0xFF78828E),
        text = Color(0xFF171A1F),
        textMuted = Color(0xFF4F5864),
        textDim = Color(0xFF616B77),
        accent = Color(0xFF836136),
        accentText = Color(0xFF836136),
        onAccent = Color(0xFFFFFFFF),
        accentSoft = Color(0xFFF0ECE7),
        status5xx = Color(0xFFBE3A33),
        status4xx = Color(0xFFA34D47),
        status3xx = Color(0xFF3D5A80),
        snackbarSurface = Color(0xFF1C2027),
        snackbarText = Color(0xFFF2F3F5),
    )
}

val LocalDimockPalette = staticCompositionLocalOf { DimockPalettes.Dark }

/**
 * Shape and spacing. Radii are layered, never uniform: the further out a shape sits, the softer its corners.
 * Nothing is a pill — a 34 dp chip at 10 dp is rounded, a 34 dp chip at 17 dp would be a capsule.
 */
object DimockDimens {
    /** Containers: grouped lists, cards. */
    const val RADIUS_CONTAINER_DP = 14
    /** Controls: chips, buttons, fields, code blocks, banners, the segmented control. */
    const val RADIUS_CONTROL_DP = 10
    /** Tags and small marks: MOCK badge, switch knob. */
    const val RADIUS_TAG_DP = 6
    /** Hairline-thin marks that are still rounded: the sheet handle, the active-tab pill. */
    const val RADIUS_PILL_DP = 2
    /** Bottom sheets, top corners. */
    const val RADIUS_SHEET_DP = 24
    /** The floating bottom bar. */
    const val RADIUS_BAR_DP = 18
    const val ROW_HEIGHT_DP = 72
    /** Horizontal padding of screen content. */
    const val GUTTER_DP = 20
    /** Horizontal inset of grouped lists and the floating bottom bar from the screen edge. */
    const val INSET_DP = 14
    const val TAP_TARGET_DP = 44
    const val BAR_HEIGHT_DP = 60
    /** Gap between the floating bar and the bottom edge (above the navigation bar inset). */
    const val BAR_BOTTOM_DP = 16
    /** Space a scrolling screen leaves under itself so the floating bar never covers its last row. */
    const val BAR_CLEARANCE_DP = 92
    /** Left inset of the divider between rows in a grouped list: past the status column, under the path. */
    const val ROW_DIVIDER_INSET_DP = 76
}
