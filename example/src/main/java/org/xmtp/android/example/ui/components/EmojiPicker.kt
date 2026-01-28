package org.xmtp.android.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xmtp.android.example.ui.theme.XMTPTheme

val defaultEmojis = listOf(
    // Quick reactions row
    "\uD83D\uDC4D", // 👍
    "\u2764\uFE0F", // ❤️
    "\uD83D\uDE02", // 😂
    "\uD83D\uDE2E", // 😮
    "\uD83D\uDE22", // 😢
    "\uD83D\uDE4F", // 🙏
    // Smileys & Emotion
    "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01", "\uD83D\uDE06",
    "\uD83D\uDE05", "\uD83E\uDD23", "\uD83D\uDE0A", "\uD83D\uDE07", "\uD83D\uDE42",
    "\uD83D\uDE43", "\uD83D\uDE09", "\uD83D\uDE0C", "\uD83D\uDE0D", "\uD83E\uDD70",
    "\uD83D\uDE18", "\uD83D\uDE17", "\uD83D\uDE19", "\uD83D\uDE1A", "\uD83D\uDE0B",
    "\uD83D\uDE1B", "\uD83D\uDE1C", "\uD83E\uDD2A", "\uD83D\uDE1D", "\uD83E\uDD11",
    "\uD83E\uDD17", "\uD83E\uDD2D", "\uD83E\uDD2B", "\uD83E\uDD14", "\uD83E\uDD10",
    "\uD83E\uDD28", "\uD83D\uDE10", "\uD83D\uDE11", "\uD83D\uDE36", "\uD83D\uDE0F",
    "\uD83D\uDE12", "\uD83D\uDE44", "\uD83D\uDE2C", "\uD83E\uDD25", "\uD83D\uDE14",
    "\uD83D\uDE2A", "\uD83E\uDD24", "\uD83D\uDE34", "\uD83D\uDE37", "\uD83E\uDD12",
    "\uD83E\uDD15", "\uD83E\uDD22", "\uD83E\uDD2E", "\uD83E\uDD27", "\uD83E\uDD75",
    "\uD83E\uDD76", "\uD83E\uDD74", "\uD83D\uDE35", "\uD83E\uDD2F", "\uD83E\uDD20",
    "\uD83E\uDD73", "\uD83D\uDE0E", "\uD83E\uDD13", "\uD83E\uDDD0", "\uD83D\uDE15",
    "\uD83D\uDE1F", "\uD83D\uDE41", "\u2639\uFE0F", "\uD83D\uDE2E", "\uD83D\uDE2F",
    "\uD83D\uDE32", "\uD83D\uDE33", "\uD83E\uDD7A", "\uD83D\uDE26", "\uD83D\uDE27",
    "\uD83D\uDE28", "\uD83D\uDE30", "\uD83D\uDE25", "\uD83D\uDE2D", "\uD83D\uDE31",
    "\uD83D\uDE16", "\uD83D\uDE23", "\uD83D\uDE1E", "\uD83D\uDE13", "\uD83D\uDE29",
    "\uD83D\uDE2B", "\uD83E\uDD71", "\uD83D\uDE24", "\uD83D\uDE21", "\uD83D\uDE20",
    "\uD83E\uDD2C", "\uD83D\uDE08", "\uD83D\uDC7F", "\uD83D\uDC80", "\u2620\uFE0F",
    "\uD83D\uDCA9", "\uD83E\uDD21", "\uD83D\uDC79", "\uD83D\uDC7A", "\uD83D\uDC7B",
    "\uD83D\uDC7D", "\uD83D\uDC7E", "\uD83E\uDD16",
    // Gestures
    "\uD83D\uDC4E", "\uD83D\uDC4A", "\u270A", "\uD83E\uDD1B", "\uD83E\uDD1C",
    "\uD83D\uDC4F", "\uD83D\uDE4C", "\uD83D\uDC50", "\uD83E\uDD32", "\uD83E\uDD1D",
    "\u270D\uFE0F", "\uD83D\uDC85", "\uD83E\uDD33", "\uD83D\uDCAA",
    // Hearts
    "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99", "\uD83D\uDC9C",
    "\uD83E\uDD0E", "\uD83D\uDDA4", "\uD83E\uDD0D", "\uD83D\uDC94", "\u2763\uFE0F",
    "\uD83D\uDC95", "\uD83D\uDC9E", "\uD83D\uDC93", "\uD83D\uDC97", "\uD83D\uDC96",
    "\uD83D\uDC98", "\uD83D\uDC9D", "\uD83D\uDC9F",
    // Common objects
    "\uD83D\uDD25", "\u2B50", "\uD83C\uDF1F", "\u2728", "\uD83C\uDF88", "\uD83C\uDF89",
    "\uD83C\uDF8A", "\uD83C\uDF81", "\uD83C\uDF80", "\uD83D\uDCAF", "\uD83D\uDCA2",
    "\uD83D\uDCA5", "\uD83D\uDCAB", "\uD83D\uDCA6", "\uD83D\uDCA8", "\uD83D\uDCA3",
    "\uD83D\uDCAC",
    // Symbols
    "\u2714\uFE0F", "\u274C", "\u274E", "\u2753", "\u2757", "\u26A0\uFE0F",
    "\uD83D\uDEAB", "\u2705", "\uD83D\uDC4C",
)

@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    emojis: List<String> = defaultEmojis,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(emojis) { emoji ->
            EmojiItem(
                emoji = emoji,
                onClick = { onEmojiSelected(emoji) },
            )
        }
    }
}

@Composable
fun QuickReactionPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    reactions: List<String> = defaultEmojis.take(6),
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            reactions.forEach { emoji ->
                EmojiItem(
                    emoji = emoji,
                    onClick = { onEmojiSelected(emoji) },
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun EmojiItem(
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmojiPickerPreview() {
    XMTPTheme {
        EmojiPicker(
            onEmojiSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickReactionPickerPreview() {
    XMTPTheme {
        QuickReactionPicker(
            onEmojiSelected = {},
        )
    }
}
