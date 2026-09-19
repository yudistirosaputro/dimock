package com.yudistirosaputro.dimock.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

private val scheme = darkColorScheme(
    primary = DimockColors.Accent,
    onPrimary = DimockColors.OnAccent,
    secondary = DimockColors.TextMuted,
    background = DimockColors.Surface,
    onBackground = DimockColors.Text,
    surface = DimockColors.Surface,
    onSurface = DimockColors.Text,
    surfaceVariant = DimockColors.SurfaceRaised,
    onSurfaceVariant = DimockColors.TextMuted,
    outline = DimockColors.Control,
    outlineVariant = DimockColors.Hairline,
    error = DimockColors.Status5xx,
    onError = DimockColors.OnAccent,
    surfaceContainer = DimockColors.SurfaceRaised,
    surfaceContainerHigh = DimockColors.SurfaceRaised,
    inverseSurface = DimockColors.SnackbarSurface,
    inverseOnSurface = DimockColors.SnackbarText,
    inversePrimary = DimockColors.Accent,
)

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
fun DimockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
}
