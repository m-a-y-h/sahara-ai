package pk.edu.ucp.saharaai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pk.edu.ucp.saharaai.R
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontStyle

val ExoFamily = FontFamily(
    Font(R.font.exo_bold, weight = FontWeight.W900),
    Font(R.font.exo_bolditalic, weight = FontWeight.W900, style = FontStyle.Italic),

    Font(R.font.exo_semibold, weight = FontWeight.W800),
    Font(R.font.exo_semibolditalic, weight = FontWeight.W800, style = FontStyle.Italic),

    Font(R.font.exo_medium, weight = FontWeight.W700),
    Font(R.font.exo_mediumitalic, weight = FontWeight.W700, style = FontStyle.Italic),

    Font(R.font.exo_regular, weight = FontWeight.W600),
    Font(R.font.exo_italic, weight = FontWeight.W600, style = FontStyle.Italic),

    Font(R.font.exo_light, weight = FontWeight.W500),
    Font(R.font.exo_lightitalic, weight = FontWeight.W500, style = FontStyle.Italic),

    Font(R.font.exo_light, weight = FontWeight.W400),
    Font(R.font.exo_lightitalic, weight = FontWeight.W400, style = FontStyle.Italic),

    Font(R.font.exo_medium, weight = FontWeight.W300),
    Font(R.font.exo_mediumitalic, weight = FontWeight.W300, style = FontStyle.Italic),

    Font(R.font.exo_extralight, weight = FontWeight.W200),
    Font(R.font.exo_extralightitalic, weight = FontWeight.W200, style = FontStyle.Italic),

    Font(R.font.exo_thin, weight = FontWeight.W100),
    Font(R.font.exo_thinitalic, weight = FontWeight.W100, style = FontStyle.Italic)
)

private val defaultTextStyle = TextStyle(
    fontFamily = ExoFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    fontWeight = FontWeight.W400,
    letterSpacing = 0.5.sp
)

val Typography = Typography(
    displayLarge = defaultTextStyle.copy(fontSize = 48.sp, lineHeight = 56.sp),
    displayMedium = defaultTextStyle.copy(fontSize = 36.sp, lineHeight = 44.sp),
    displaySmall = defaultTextStyle.copy(fontSize = 32.sp, lineHeight = 40.sp),
    
    headlineLarge = defaultTextStyle.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = defaultTextStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = defaultTextStyle.copy(fontSize = 20.sp, lineHeight = 28.sp),
    
    titleLarge = defaultTextStyle.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = defaultTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp),
    titleSmall = defaultTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
    
    bodyLarge = defaultTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = defaultTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
    bodySmall = defaultTextStyle.copy(fontSize = 11.sp, lineHeight = 14.sp),
    
    labelLarge = defaultTextStyle.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = defaultTextStyle.copy(fontSize = 10.sp, lineHeight = 14.sp),
    labelSmall = defaultTextStyle.copy(fontSize = 9.sp, lineHeight = 12.sp)
)
