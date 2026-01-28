package org.xmtp.android.example.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.xmtp.android.example.ui.theme.XMTPTheme

sealed class AttachmentOption {
    object Camera : AttachmentOption()
    object Gallery : AttachmentOption()
    object File : AttachmentOption()
    object Gif : AttachmentOption()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onOptionSelected: (AttachmentOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Send Attachment",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AttachmentOptionItem(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    backgroundColor = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1976D2),
                    onClick = {
                        onOptionSelected(AttachmentOption.Camera)
                        onDismiss()
                    },
                )

                AttachmentOptionItem(
                    icon = Icons.Default.Image,
                    label = "Gallery",
                    backgroundColor = Color(0xFFE8F5E9),
                    iconTint = Color(0xFF388E3C),
                    onClick = {
                        onOptionSelected(AttachmentOption.Gallery)
                        onDismiss()
                    },
                )

                AttachmentOptionItem(
                    icon = Icons.Default.AttachFile,
                    label = "File",
                    backgroundColor = Color(0xFFFFF3E0),
                    iconTint = Color(0xFFF57C00),
                    onClick = {
                        onOptionSelected(AttachmentOption.File)
                        onDismiss()
                    },
                )

                AttachmentOptionItem(
                    icon = Icons.Default.Gif,
                    label = "GIF",
                    backgroundColor = Color(0xFFFCE4EC),
                    iconTint = Color(0xFFC2185B),
                    onClick = {
                        onOptionSelected(AttachmentOption.Gif)
                        onDismiss()
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttachmentOptionItem(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AttachmentPickerContentPreview() {
    XMTPTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            Text(
                text = "Send Attachment",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AttachmentOptionItem(
                    icon = Icons.Default.CameraAlt,
                    label = "Camera",
                    backgroundColor = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1976D2),
                    onClick = {},
                )

                AttachmentOptionItem(
                    icon = Icons.Default.Image,
                    label = "Gallery",
                    backgroundColor = Color(0xFFE8F5E9),
                    iconTint = Color(0xFF388E3C),
                    onClick = {},
                )

                AttachmentOptionItem(
                    icon = Icons.Default.AttachFile,
                    label = "File",
                    backgroundColor = Color(0xFFFFF3E0),
                    iconTint = Color(0xFFF57C00),
                    onClick = {},
                )

                AttachmentOptionItem(
                    icon = Icons.Default.Gif,
                    label = "GIF",
                    backgroundColor = Color(0xFFFCE4EC),
                    iconTint = Color(0xFFC2185B),
                    onClick = {},
                )
            }
        }
    }
}
