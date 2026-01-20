package org.xmtp.android.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xmtp.android.example.ui.theme.AvatarColors
import org.xmtp.android.example.ui.theme.XMTPTheme
import kotlin.math.abs

@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    showOnlineIndicator: Boolean = false
) {
    val initials = getInitials(name)
    val backgroundColor = getAvatarColor(name)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Main avatar circle
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = Color.White,
                fontSize = (size.value * 0.35f).sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Online indicator
        if (showOnlineIndicator) {
            Box(
                modifier = Modifier
                    .size(size * 0.25f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .size(size * 0.2f)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xFF34C759))
                )
            }
        }
    }
}

private fun getInitials(name: String): String {
    val cleaned = name.removePrefix("0x").trim()
    return if (cleaned.length >= 2) {
        cleaned.take(2).uppercase()
    } else {
        cleaned.uppercase()
    }
}

private fun getAvatarColor(name: String): Color {
    val hash = abs(name.hashCode())
    return AvatarColors[hash % AvatarColors.size]
}

@Preview
@Composable
private fun AvatarPreview() {
    XMTPTheme {
        Avatar(name = "0x1234567890abcdef")
    }
}

@Preview
@Composable
private fun AvatarWithOnlinePreview() {
    XMTPTheme {
        Avatar(name = "John Doe", showOnlineIndicator = true)
    }
}
