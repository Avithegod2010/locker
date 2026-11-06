package app.locker.vault.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.locker.vault.R
import app.locker.vault.core.Accent
import app.locker.vault.core.AppSettings
import app.locker.vault.core.ThemeMode

val BrandBlue = Color(0xFF5B9CFF)
val BrandMint = Color(0xFF57D9A3)
val BrandAmber = Color(0xFFFFB968)
val BrandViolet = Color(0xFFB69CFF)
val BrandCoral = Color(0xFFFF8F7A)

/**
 * Static accent themes. Each entry recolors the primary family of the base scheme; dynamic
 * wallpaper colors override all of them when enabled.
 */
data class AccentTheme(
    val id: String,
    val label: String,
    val swatch: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color
)

val AccentThemes = listOf(
    AccentTheme(
        Accent.ID_DEFAULT, "Locker Blue", Color(0xFF5B9CFF),
        Color(0xFF6EA8FF), Color(0xFF1B3E70), Color(0xFFD6E4FF),
        Color(0xFF2F6FEA), Color(0xFFD9E5FF), Color(0xFF001B3E)
    ),
    AccentTheme(
        "violet", "Violet", Color(0xFFB69CFF),
        Color(0xFFB69CFF), Color(0xFF472A80), Color(0xFFEBDDFF),
        Color(0xFF7452E0), Color(0xFFE7DCFF), Color(0xFF251058)
    ),
    AccentTheme(
        "mint", "Mint", Color(0xFF57D9A3),
        Color(0xFF5FE0A4), Color(0xFF0E4C30), Color(0xFFAFF5CF),
        Color(0xFF178F5D), Color(0xFFC3F1D8), Color(0xFF00391F)
    ),
    AccentTheme(
        "coral", "Coral", Color(0xFFFF8F7A),
        Color(0xFFFF9E8C), Color(0xFF6E1F12), Color(0xFFFFDAD4),
        Color(0xFFCB4227), Color(0xFFFFDAD2), Color(0xFF550500)
    ),
    AccentTheme(
        "amber", "Amber", Color(0xFFFFB968),
        Color(0xFFFFC978), Color(0xFF5C420B), Color(0xFFFFE191),
        Color(0xFF8A5708), Color(0xFFFFE0AC), Color(0xFF2A1800)
    ),
    AccentTheme(
        "rose", "Rose", Color(0xFFFF7FA8),
        Color(0xFFFF8FBB), Color(0xFF761B44), Color(0xFFFFD9E5),
        Color(0xFFC13E63), Color(0xFFFFD9E1), Color(0xFF51031E)
    ),
    AccentTheme(
        "ice", "Ice", Color(0xFF7BD5E5),
        Color(0xFF86DBE9), Color(0xFF0E4A57), Color(0xFFC2F3FF),
        Color(0xFF0B7D91), Color(0xFFBEEFF7), Color(0xFF003441)
    ),
    AccentTheme(
        "lime", "Lime", Color(0xFFC4E86B),
        Color(0xFFCDEE7C), Color(0xFF46531A), Color(0xFFE7F8B3),
        Color(0xFF5C7211), Color(0xFFE7F2B8), Color(0xFF1A2300)
    )
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFF002E62),
    primaryContainer = Color(0xFF1B3E70),
    onPrimaryContainer = Color(0xFFD6E4FF),
    inversePrimary = Color(0xFF2C64B8),
    secondary = Color(0xFF98D7B5),
    onSecondary = Color(0xFF003825),
    secondaryContainer = Color(0xFF17402E),
    onSecondaryContainer = Color(0xFFB6F2CF),
    tertiary = Color(0xFFFFCA7D),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5D420E),
    onTertiaryContainer = Color(0xFFFFE2B3),
    background = Color(0xFF0B0F12),
    onBackground = Color(0xFFE7EDF1),
    surface = Color(0xFF0B0F12),
    onSurface = Color(0xFFE7EDF1),
    surfaceVariant = Color(0xFF1C252A),
    onSurfaceVariant = Color(0xFFA9B6BE),
    surfaceTint = Color(0xFF6EA8FF),
    surfaceContainerLowest = Color(0xFF070A0C),
    surfaceContainerLow = Color(0xFF11171A),
    surfaceContainer = Color(0xFF161D21),
    surfaceContainerHigh = Color(0xFF1D262B),
    surfaceContainerHighest = Color(0xFF253139),
    outline = Color(0xFF3B484F),
    outlineVariant = Color(0xFF253036),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF561F1D),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE7EDF1),
    inverseOnSurface = Color(0xFF1B2327)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6FEA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E5FF),
    onPrimaryContainer = Color(0xFF001B3E),
    inversePrimary = Color(0xFFA9C7FF),
    secondary = Color(0xFF2A7D5B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDF0D6),
    onSecondaryContainer = Color(0xFF002114),
    tertiary = Color(0xFF865300),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF11181D),
    surface = Color(0xFFF6F8FB),
    onSurface = Color(0xFF11181D),
    surfaceVariant = Color(0xFFE7EEF4),
    onSurfaceVariant = Color(0xFF4D5B65),
    surfaceTint = Color(0xFF2F6FEA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEBF1F7),
    surfaceContainerHighest = Color(0xFFDDE5ED),
    outline = Color(0xFFB6C3CE),
    outlineVariant = Color(0xFFDCE4EB),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF263238),
    inverseOnSurface = Color(0xFFEDF3F8)
)

@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int): Font = Font(
    resId = R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

@OptIn(ExperimentalTextApi::class)
val LockerFontFamily = FontFamily(
    jakarta(400),
    jakarta(500),
    jakarta(600),
    jakarta(700),
    jakarta(800)
)

private val LockerTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-1.2).sp
    ),
    displayMedium = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = LockerFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun LockerTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val accent = AccentThemes.firstOrNull { it.id == settings.accentId }
    var colors = base
    if (accent != null && !dynamic) {
        val primary = if (dark) accent.darkPrimary else accent.lightPrimary
        val container = if (dark) accent.darkContainer else accent.lightContainer
        val onContainer = if (dark) accent.darkOnContainer else accent.lightOnContainer
        colors = colors.copy(
            primary = primary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            surfaceTint = primary
        )
    }
    if (dark && settings.trueBlack) {
        colors = colors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceTint = colors.surfaceTint,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF070707),
            surfaceContainer = Color(0xFF0C0C0C),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1D1D1D),
            outlineVariant = Color(0xFF262626)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = LockerTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
        ),
        content = content
    )
}
