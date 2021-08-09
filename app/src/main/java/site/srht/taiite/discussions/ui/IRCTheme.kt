package site.srht.taiite.discussions.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import com.google.accompanist.insets.ProvideWindowInsets

// TODO customise
private val IRCLightPalette = lightColors()
private val IRCDarkPalette = darkColors()
private val IRCShapes = Shapes()
private val IRCTypography = Typography()

@Composable
fun IRCTheme(
    colors: Colors = if (isSystemInDarkTheme()) IRCDarkPalette else IRCLightPalette,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colors = colors,
        typography = IRCTypography,
        shapes = IRCShapes,
    ) {
        ProvideWindowInsets(
            consumeWindowInsets = false,
            content = content,
        )
    }
}