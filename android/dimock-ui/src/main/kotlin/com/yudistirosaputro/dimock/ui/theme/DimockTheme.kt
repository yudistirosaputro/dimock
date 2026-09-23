package com.yudistirosaputro.dimock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yudistirosaputro.dimock.ui.R

/**
 * Typefaces, bundled under `res/font/` (latin subsets, ~30 KB each, SIL OFL 1.1 — licences in
 * `dimock-ui/fonts-license/`). Plus Jakarta Sans carries everything a person reads; DM Mono carries everything a
 * machine produced: paths, status codes, durations, timestamps, headers, bodies, commands.
 */
object DimockFonts {
    val Ui: FontFamily = FontFamily(
        Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
        Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    )
    val Mono: FontFamily = FontFamily(
        Font(R.font.dm_mono_regular, FontWeight.Normal),
        Font(R.font.dm_mono_medium, FontWeight.Medium),
    )
}

/** Text sits on its line box, not above it: every style trims the platform padding and centres the line height. */
private val lineBox = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun ui(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = DimockFonts.Ui,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = lineBox,
)

private fun mono(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Double = 0.0) = TextStyle(
    fontFamily = DimockFonts.Mono,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = lineBox,
)

object DimockType {
    /** The big figure on Detail: the status code (or `ERR`). Sans, tight, heavy — the one display moment on the panel. */
    val StatusBig = ui(56, 52, FontWeight.SemiBold, -0.04)
    /** The rules-active count on Mocks. */
    val HeaderCount = ui(44, 44, FontWeight.SemiBold, -0.03)
    val ScreenTitle = ui(26, 30, FontWeight.SemiBold, -0.02)
    val SheetTitle = ui(20, 26, FontWeight.SemiBold)
    val Wordmark = ui(15, 20, FontWeight.SemiBold, 0.01)
    val Body = ui(14, 20)
    val BodyStrong = ui(15, 20, FontWeight.Medium)
    val Label = ui(13, 18, FontWeight.Medium)
    val Caption = ui(12, 16)
    /** Bottom-bar tab titles. */
    val Tab = ui(13, 18)
    val MonoRow = mono(14, 18)
    val MonoSmall = mono(12, 16)
    val MonoCode = mono(13, 20)
    /** Status code in the Traffic row's first column. */
    val MonoStatus = mono(14, 18, FontWeight.Medium)
    /** Method under the status code, and the MOCK badge text. */
    val MonoTiny = mono(10, 12, FontWeight.Medium, 0.08)
    val MockTag = MonoTiny.copy(letterSpacing = 0.06.em)
    val SectionLabel = mono(11, 14, tracking = 0.06)
}

/**
 * Reads the live palette: `DimockTheme.colors.text`. An object and a composable function of the same name sit
 * side by side here exactly as `MaterialTheme` does.
 */
object DimockTheme {
    val colors: DimockPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalDimockPalette.current
}

/** The M3 roles the few Material primitives we use (Scaffold, ModalBottomSheet, Snackbar) read from. */
private fun materialScheme(p: DimockPalette, dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        secondary = p.textMuted,
        background = p.surface,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceRaised,
        onSurfaceVariant = p.textMuted,
        outline = p.control,
        outlineVariant = p.hairline,
        error = p.status5xx,
        onError = p.onAccent,
        surfaceContainer = p.surfaceRaised,
        surfaceContainerHigh = p.surfaceRaised,
        surfaceContainerLow = p.surfaceRaised,
        inverseSurface = p.snackbarSurface,
        inverseOnSurface = p.snackbarText,
        inversePrimary = p.accent,
        scrim = p.surface,
    )
} else {
    lightColorScheme(
        primary = p.accent,
        onPrimary = p.onAccent,
        secondary = p.textMuted,
        background = p.surface,
        onBackground = p.text,
        surface = p.surface,
        onSurface = p.text,
        surfaceVariant = p.surfaceRaised,
        onSurfaceVariant = p.textMuted,
        outline = p.control,
        outlineVariant = p.hairline,
        error = p.status5xx,
        onError = p.onAccent,
        surfaceContainer = p.surfaceRaised,
        surfaceContainerHigh = p.surfaceRaised,
        surfaceContainerLow = p.surfaceRaised,
        inverseSurface = p.snackbarSurface,
        inverseOnSurface = p.snackbarText,
        inversePrimary = p.accent,
        scrim = p.text,
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(DimockDimens.RADIUS_TAG_DP.dp),
    small = RoundedCornerShape(DimockDimens.RADIUS_CONTROL_DP.dp),
    medium = RoundedCornerShape(DimockDimens.RADIUS_CONTROL_DP.dp),
    large = RoundedCornerShape(DimockDimens.RADIUS_CONTAINER_DP.dp),
    extraLarge = RoundedCornerShape(topStart = DimockDimens.RADIUS_SHEET_DP.dp, topEnd = DimockDimens.RADIUS_SHEET_DP.dp),
)

private val typography = Typography(
    headlineMedium = DimockType.ScreenTitle,
    titleLarge = DimockType.SheetTitle,
    titleMedium = DimockType.BodyStrong,
    bodyMedium = DimockType.Body,
    bodySmall = DimockType.Caption,
    labelLarge = DimockType.Label,
    labelSmall = DimockType.MockTag,
)

@Composable
fun DimockTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (darkTheme) DimockPalettes.Dark else DimockPalettes.Light
    CompositionLocalProvider(LocalDimockPalette provides palette) {
        MaterialTheme(
            colorScheme = materialScheme(palette, darkTheme),
            shapes = shapes,
            typography = typography,
            content = content,
        )
    }
}
