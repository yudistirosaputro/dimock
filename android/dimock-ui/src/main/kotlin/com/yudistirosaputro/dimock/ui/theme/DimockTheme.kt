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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Typefaces. docs/design/tokens.md names Space Grotesk + JetBrains Mono; drop the .ttf files into
 * `dimock-ui/src/main/res/font/` and switch these two vals to `FontFamily(Font(R.font.space_grotesk_regular), ...)`.
 * Until then the platform faces keep the layout metrics close enough to the mockups.
 */
object DimockFonts {
    val Ui: FontFamily = FontFamily.SansSerif
    val Mono: FontFamily = FontFamily.Monospace
}

object DimockType {
    val HeaderCount = TextStyle(fontFamily = DimockFonts.Mono, fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.03).em)
    val ScreenTitle = TextStyle(fontFamily = DimockFonts.Ui, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)
    val SheetTitle = TextStyle(fontFamily = DimockFonts.Ui, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val Wordmark = TextStyle(fontFamily = DimockFonts.Ui, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)
    val Body = TextStyle(fontFamily = DimockFonts.Ui, fontSize = 14.sp, lineHeight = 20.sp)
    val BodyStrong = TextStyle(fontFamily = DimockFonts.Ui, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val Label = TextStyle(fontFamily = DimockFonts.Ui, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontFamily = DimockFonts.Ui, fontSize = 12.sp, lineHeight = 16.sp)
    val MonoRow = TextStyle(fontFamily = DimockFonts.Mono, fontSize = 14.sp, lineHeight = 18.sp)
    val MonoSmall = TextStyle(fontFamily = DimockFonts.Mono, fontSize = 12.sp, lineHeight = 16.sp)
    val MonoCode = TextStyle(fontFamily = DimockFonts.Mono, fontSize = 13.sp, lineHeight = 21.sp)
    val MonoStatusBig = TextStyle(fontFamily = DimockFonts.Mono, fontSize = 56.sp, lineHeight = 52.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.04).em)
    val MockTag = TextStyle(fontFamily = DimockFonts.Mono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.em)
    val SectionLabel = TextStyle(fontFamily = DimockFonts.Mono, fontSize = 11.sp, letterSpacing = 0.06.em)
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
        inverseSurface = p.snackbarSurface,
        inverseOnSurface = p.snackbarText,
        inversePrimary = p.accent,
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
        inverseSurface = p.snackbarSurface,
        inverseOnSurface = p.snackbarText,
        inversePrimary = p.accent,
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(DimockDimens.RADIUS_DP.dp),
    medium = RoundedCornerShape(DimockDimens.RADIUS_DP.dp),
    large = RoundedCornerShape(DimockDimens.RADIUS_SHEET_DP.dp),
    extraLarge = RoundedCornerShape(DimockDimens.RADIUS_SHEET_DP.dp),
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
