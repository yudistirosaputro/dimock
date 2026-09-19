package com.yudistirosaputro.dimock.ui.theme

import androidx.compose.ui.graphics.Color

/** Fixed inspector palette. Source of truth: docs/design/tokens.md. The theme never follows the host app. */
object DimockColors {
    val Surface = Color(0xFF0E1012)
    val SurfaceRaised = Color(0xFF15181B)
    val Hairline = Color(0xFF262B30)
    val HairlineSoft = Color(0xFF1C2024)
    val Control = Color(0xFF3A424A)
    val Text = Color(0xFFE8ECEF)
    val TextMuted = Color(0xFF8B949E)
    val TextDim = Color(0xFF5C6670)
    /** One meaning only: mocked. */
    val Accent = Color(0xFFC6F135)
    val OnAccent = Color(0xFF0E1012)
    val Status5xx = Color(0xFFF06A5B)
    val Status4xx = Color(0xFFF2B84B)
    val Status3xx = Color(0xFF7FB2F0)
    val SnackbarSurface = Color(0xFFE8ECEF)
    val SnackbarText = Color(0xFF0E1012)

    /** Semantic only: 5xx red, 4xx amber, 3xx blue, 2xx neutral. A missing code reads as severe as a 5xx. */
    fun forStatus(code: Int?): Color = when {
        code == null -> Status5xx
        code >= 500 -> Status5xx
        code >= 400 -> Status4xx
        code >= 300 -> Status3xx
        else -> TextMuted
    }

    /** Methods are told apart by weight of colour, not by hue: reads are quiet, writes brighter, DELETE warm. */
    fun forMethod(method: String): Color = when (method.uppercase()) {
        "GET" -> TextMuted
        "POST", "PUT", "PATCH" -> Text
        "DELETE" -> Status4xx
        else -> TextDim
    }
}

object DimockDimens {
    const val RADIUS_DP = 4
    const val RADIUS_SHEET_DP = 8
    const val ROW_HEIGHT_DP = 68
    const val GUTTER_DP = 20
    const val TAP_TARGET_DP = 44
    const val MARKER_WIDTH_DP = 3
}
