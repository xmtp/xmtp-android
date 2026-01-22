package org.xmtp.android.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
    darkColorScheme(
        primary = XMTPBlue,
        onPrimary = Color.White,
        primaryContainer = XMTPBlueDark,
        onPrimaryContainer = Color.White,
        secondary = PurpleGrey80,
        onSecondary = Color.Black,
        secondaryContainer = PurpleGrey40,
        onSecondaryContainer = Color.White,
        tertiary = Pink80,
        onTertiary = Color.Black,
        background = Color(0xFF121212),
        onBackground = Color.White,
        surface = Color(0xFF1E1E1E),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2C2C2E),
        onSurfaceVariant = Color(0xFFCAC4D0),
        outline = Color(0xFF938F99),
        outlineVariant = Color(0xFF49454F),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = XMTPBlue,
        onPrimary = Color.White,
        primaryContainer = XMTPBlueLight,
        onPrimaryContainer = Color.White,
        secondary = PurpleGrey40,
        onSecondary = Color.White,
        secondaryContainer = PurpleGrey80,
        onSecondaryContainer = Color.Black,
        tertiary = Pink40,
        onTertiary = Color.White,
        background = Color(0xFFF8F8F8),
        onBackground = Color.Black,
        surface = Color.White,
        onSurface = Color.Black,
        surfaceVariant = Color(0xFFE8E8ED),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0),
    )

@Composable
fun XMTPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
